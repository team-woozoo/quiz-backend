package com.woozoo.quiz.infrastructure.analysis;

import com.woozoo.quiz.concept.domain.ConceptTag;
import com.woozoo.quiz.material.domain.MaterialChunk;
import com.woozoo.quiz.material.port.ExtractedConcept;
import com.woozoo.quiz.material.port.MaterialAnalysis;
import com.woozoo.quiz.material.port.MaterialAnalysisException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.io.ClassPathResource;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 게이트웨이를 부르지 않고, 응답을 정해 둔 ChatModel 로 어댑터가 보내는 것과 받은 것을 다루는 방식을 본다
class LlmMaterialAnalysisAdapterTest {

    private static final int MAX_TOKENS = 8000;
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private static final List<MaterialChunk> CHUNKS = List.of(
            new MaterialChunk(1, 4, "TCP는 연결 지향 프로토콜이다.\n신뢰성 있는 전송을 보장한다."),
            new MaterialChunk(2, 5, "UDP는 비연결형이다. 빠르지만 순서를 보장하지 않는다."));

    private static final String ANALYSIS_JSON = """
            {"suitable": true, "reason": "전송 계층 프로토콜의 특징이 담겨 있다.",
             "concepts": [
               {"name": "TCP와 UDP 비교", "description": "두 전송 프로토콜의 차이다.",
                "tags": ["CONTRAST", "TRADEOFF"], "chunkSeqs": [1, 2]},
               {"name": "TCP의 신뢰성", "description": "순서와 전달을 보장한다.",
                "tags": ["CONDITION"], "chunkSeqs": [1]}
             ]}
            """;

    // ChatClient 는 모델의 getOptions() 가 구조화 출력을 지원하는 옵션일 때만 스키마를 response_format 으로 보낸다
    static class StubChatModel implements ChatModel {

        private final Function<Prompt, ChatResponse> answer;
        Prompt received;

        StubChatModel(Function<Prompt, ChatResponse> answer) {
            this.answer = answer;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            this.received = prompt;
            return answer.apply(prompt);
        }

        @Override
        public ChatOptions getOptions() {
            return OpenAiChatOptions.builder().build();
        }
    }

    private static ChatResponse respond(String text) {
        return respond(text, 500);
    }

    private static ChatResponse respond(String text, int outputTokens) {
        return new ChatResponse(
                List.of(new Generation(new AssistantMessage(text))),
                ChatResponseMetadata.builder().usage(new DefaultUsage(100, outputTokens)).build());
    }

    private static LlmMaterialAnalysisAdapter adapterWith(ChatModel chatModel) {
        return new LlmMaterialAnalysisAdapter(ChatClient.builder(chatModel),
                new ClassPathResource("prompts/material-analysis-system.md"), "analysis-model", TIMEOUT, MAX_TOKENS);
    }

    @Test
    void 응답_JSON_을_분석_결과로_바꾼다() {
        MaterialAnalysis analysis = adapterWith(new StubChatModel(prompt -> respond(ANALYSIS_JSON))).analyze(CHUNKS);

        assertThat(analysis.suitable()).isTrue();
        assertThat(analysis.reason()).isEqualTo("전송 계층 프로토콜의 특징이 담겨 있다.");
        assertThat(analysis.concepts()).containsExactly(
                new ExtractedConcept("TCP와 UDP 비교", "두 전송 프로토콜의 차이다.",
                        List.of(ConceptTag.CONTRAST, ConceptTag.TRADEOFF), List.of(1, 2)),
                new ExtractedConcept("TCP의 신뢰성", "순서와 전달을 보장한다.",
                        List.of(ConceptTag.CONDITION), List.of(1)));
    }

    @Test
    void 정해진_모델_출력_상한_타임아웃으로_보낸다() {
        StubChatModel chatModel = new StubChatModel(prompt -> respond(ANALYSIS_JSON));

        adapterWith(chatModel).analyze(CHUNKS);

        assertThat(chatModel.received.getOptions()).isInstanceOfSatisfying(OpenAiChatOptions.class, options -> {
            assertThat(options.getModel()).isEqualTo("analysis-model");
            assertThat(options.getMaxTokens()).isEqualTo(MAX_TOKENS);
            assertThat(options.getTimeout()).isEqualTo(TIMEOUT);
        });
    }

    // 개념 이름은 이후 퀴즈와 학습 기록의 기준이라 같은 자료에 흔들리지 않아야 한다
    @Test
    void 온도_0_으로_보낸다() {
        StubChatModel chatModel = new StubChatModel(prompt -> respond(ANALYSIS_JSON));

        adapterWith(chatModel).analyze(CHUNKS);

        assertThat(chatModel.received.getOptions()).isInstanceOfSatisfying(OpenAiChatOptions.class,
                options -> assertThat(options.getTemperature()).isEqualTo(0.0));
    }

    @Test
    void 분석의_JSON_스키마를_response_format_으로_보낸다() {
        StubChatModel chatModel = new StubChatModel(prompt -> respond(ANALYSIS_JSON));

        adapterWith(chatModel).analyze(CHUNKS);

        assertThat(chatModel.received.getOptions()).isInstanceOfSatisfying(OpenAiChatOptions.class, options -> {
            assertThat(options.getResponseFormat().getType()).isEqualTo(OpenAiChatModel.ResponseFormat.Type.JSON_SCHEMA);
            assertThat(options.getResponseFormat().getJsonSchema())
                    .contains("\"suitable\"", "\"reason\"", "\"concepts\"", "\"name\"", "\"description\"",
                            "\"tags\"", "\"chunkSeqs\"");
        });
    }

    // 스키마의 허용 값이 프롬프트의 태그 이름과 같아야 모델이 설명대로 고를 수 있다
    @Test
    void 태그의_허용_값을_스키마에_싣는다() {
        StubChatModel chatModel = new StubChatModel(prompt -> respond(ANALYSIS_JSON));

        adapterWith(chatModel).analyze(CHUNKS);

        assertThat(chatModel.received.getOptions()).isInstanceOfSatisfying(OpenAiChatOptions.class,
                options -> assertThat(options.getResponseFormat().getJsonSchema())
                        .contains("CONTRAST", "CONDITION", "RULE", "FACTORS", "TRADEOFF"));
    }

    @Test
    void system_프롬프트와_청크를_담은_user_메시지를_보낸다() {
        StubChatModel chatModel = new StubChatModel(prompt -> respond(ANALYSIS_JSON));

        adapterWith(chatModel).analyze(CHUNKS);

        assertThat(chatModel.received.getSystemMessage().getText()).contains("대학 강의 자료를 분석해");
        assertThat(chatModel.received.getUserMessage().getText()).isEqualTo("""
                <material>
                <chunk seq="1">
                TCP는 연결 지향 프로토콜이다.
                신뢰성 있는 전송을 보장한다.
                </chunk>
                <chunk seq="2">
                UDP는 비연결형이다. 빠르지만 순서를 보장하지 않는다.
                </chunk>
                </material>
                """);
    }

    // 원문의 { } 를 템플릿 변수로 읽으면 호출 전에 실패한다
    @Test
    void 자료의_중괄호를_그대로_보낸다() {
        StubChatModel chatModel = new StubChatModel(prompt -> respond(ANALYSIS_JSON));

        adapterWith(chatModel).analyze(List.of(new MaterialChunk(1, 1, "int main() { return 0; }")));

        assertThat(chatModel.received.getUserMessage().getText()).contains("int main() { return 0; }");
    }

    // 부적합 자료는 개념이 없는 것이 맞는 응답이다. 실패로 막으면 "학습 자료 아님"이 "분석 실패"로 바뀐다
    @Test
    void 부적합이라_개념이_비어_있으면_그대로_돌려준다() {
        String rejected = """
                {"suitable": false, "reason": "강의 일정과 평가 방법만 있다.", "concepts": []}
                """;

        MaterialAnalysis analysis = adapterWith(new StubChatModel(prompt -> respond(rejected))).analyze(CHUNKS);

        assertThat(analysis.suitable()).isFalse();
        assertThat(analysis.concepts()).isEmpty();
    }

    // 학습 자료라면서 개념이 없는 모순은 서비스가 거르고 남은 개념 수로 함께 판단한다
    @Test
    void 학습_자료인데_개념이_비어_있어도_어댑터는_돌려준다() {
        String noConcepts = """
                {"suitable": true, "reason": "학습 내용이 있다.", "concepts": []}
                """;

        MaterialAnalysis analysis = adapterWith(new StubChatModel(prompt -> respond(noConcepts))).analyze(CHUNKS);

        assertThat(analysis.suitable()).isTrue();
        assertThat(analysis.concepts()).isEmpty();
    }

    // 로그용 태그 집계 때문에 정상 응답이 실패하면 안 된다. 태그 정리는 서비스가 한다
    @Test
    void 태그가_없는_개념이_있어도_실패하지_않는다() {
        String noTags = """
                {"suitable": true, "reason": "학습 내용이 있다.",
                 "concepts": [{"name": "TCP", "description": "연결 지향이다.", "chunkSeqs": [1]}]}
                """;

        MaterialAnalysis analysis = adapterWith(new StubChatModel(prompt -> respond(noTags))).analyze(CHUNKS);

        assertThat(analysis.concepts()).singleElement()
                .satisfies(concept -> assertThat(concept.tags()).isNull());
    }

    @Test
    void 호출이_실패하면_MaterialAnalysisException_으로_감싼다() {
        RuntimeException failure = new RuntimeException("403 Forbidden");
        LlmMaterialAnalysisAdapter adapter = adapterWith(new StubChatModel(prompt -> {
            throw failure;
        }));

        assertThatThrownBy(() -> adapter.analyze(CHUNKS))
                .isInstanceOf(MaterialAnalysisException.class)
                .hasRootCause(failure);
    }

    @Test
    void JSON_이_깨져_있으면_MaterialAnalysisException_이다() {
        String cutOff = ANALYSIS_JSON.substring(0, ANALYSIS_JSON.length() / 2);
        LlmMaterialAnalysisAdapter adapter = adapterWith(new StubChatModel(prompt -> respond(cutOff)));

        assertThatThrownBy(() -> adapter.analyze(CHUNKS)).isInstanceOf(MaterialAnalysisException.class);
    }

    // luna 가 추론에 출력을 다 쓰면 빈 문자열이 온다
    @Test
    void 응답이_비어_있으면_MaterialAnalysisException_이다() {
        LlmMaterialAnalysisAdapter adapter = adapterWith(new StubChatModel(prompt -> respond("")));

        assertThatThrownBy(() -> adapter.analyze(CHUNKS)).isInstanceOf(MaterialAnalysisException.class);
    }

    // Claude 가 max_tokens 에 걸리면 게이트웨이가 빈 객체를 정상 응답처럼 돌려준다.
    // suitable 이 false 로 채워지므로, 막지 않으면 잘린 응답이 "학습 자료 아님"으로 읽힌다
    @Test
    void 빈_객체가_오면_부적합이_아니라_MaterialAnalysisException_이다() {
        LlmMaterialAnalysisAdapter adapter = adapterWith(new StubChatModel(prompt -> respond("{}")));

        assertThatThrownBy(() -> adapter.analyze(CHUNKS)).isInstanceOf(MaterialAnalysisException.class);
    }

    // 끝난 이유는 모델마다 믿을 수 없어, 출력 토큰이 상한에 닿았으면 내용이 온전해 보여도 버린다
    @Test
    void 출력이_상한에_닿으면_내용이_있어도_MaterialAnalysisException_이다() {
        LlmMaterialAnalysisAdapter adapter = adapterWith(
                new StubChatModel(prompt -> respond(ANALYSIS_JSON, MAX_TOKENS)));

        assertThatThrownBy(() -> adapter.analyze(CHUNKS)).isInstanceOf(MaterialAnalysisException.class);
    }

    @Test
    void 출력이_상한보다_적으면_결과를_그대로_쓴다() {
        MaterialAnalysis analysis = adapterWith(new StubChatModel(prompt -> respond(ANALYSIS_JSON, MAX_TOKENS - 1)))
                .analyze(CHUNKS);

        assertThat(analysis.concepts()).hasSize(2);
    }
}
