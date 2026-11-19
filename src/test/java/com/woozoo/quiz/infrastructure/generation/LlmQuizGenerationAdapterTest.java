package com.woozoo.quiz.infrastructure.generation;

import com.woozoo.quiz.quiz.domain.CognitiveType;
import com.woozoo.quiz.quiz.domain.Difficulty;
import com.woozoo.quiz.quiz.domain.QuizFormat;
import com.woozoo.quiz.quiz.domain.SourceScope;
import com.woozoo.quiz.quiz.port.GeneratedOption;
import com.woozoo.quiz.quiz.port.GeneratedQuestion;
import com.woozoo.quiz.quiz.port.GeneratedQuiz;
import com.woozoo.quiz.quiz.port.QuizGenerationException;
import com.woozoo.quiz.quiz.port.QuizGenerationRequest;
import com.woozoo.quiz.quiz.port.SourceChunk;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
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
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

// 게이트웨이를 부르지 않고, 응답을 정해 둔 ChatModel 로 어댑터가 보내는 것과 받은 것을 다루는 방식을 본다
class LlmQuizGenerationAdapterTest {

    private static final QuizGenerationRequest REQUEST = requestOf(SourceScope.GROUNDED);

    private static QuizGenerationRequest requestOf(SourceScope scope) {
        return new QuizGenerationRequest(
                List.of(new SourceChunk(1, "TCP는 연결 지향 프로토콜이다.")),
                QuizFormat.OX, Difficulty.NORMAL, 1, Map.of(), List.of(), scope);
    }

    // 게이트웨이가 돌려주는 모양 그대로다. 필드 이름은 GeneratedQuiz 레코드에서 만든 스키마를 따른다
    private static final String QUIZ_JSON = """
            {"concepts": [{
              "name": "TCP",
              "description": "연결 지향 프로토콜이다.",
              "chunkSeqs": [1],
              "questions": [{
                "content": "TCP는 연결 지향이다.",
                "sourceQuote": "TCP는 연결 지향 프로토콜이다.",
                "cognitiveType": "REMEMBER",
                "explanation": "자료에 그대로 나온다.",
                "options": [{"content": "O", "isCorrect": true}, {"content": "X", "isCorrect": false}],
                "sourceChunkSeq": 1
              }]
            }]}
            """;

    // 받은 Prompt 를 남기고 정해 둔 응답을 준다.
    // ChatClient 는 모델의 getOptions() 가 구조화 출력을 지원하는 옵션일 때만 스키마를 response_format 으로 보내고,
    // 아니면 형식 안내를 user 메시지 뒤에 글로 붙인다. 실제 OpenAiChatModel 처럼 OpenAI 옵션을 줘야 같은 경로를 탄다
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

    private static final int MAX_TOKENS = 1000;

    private static ChatResponse respond(String text) {
        return respond(text, 50);
    }

    private static ChatResponse respond(String text, int outputTokens) {
        return new ChatResponse(
                List.of(new Generation(new AssistantMessage(text))),
                ChatResponseMetadata.builder().usage(new DefaultUsage(100, outputTokens)).build());
    }

    private static LlmQuizGenerationAdapter adapterWith(ChatModel chatModel) {
        return adapterWith(chatModel, "");
    }

    private static LlmQuizGenerationAdapter adapterWith(ChatModel chatModel, String reasoningEffort) {
        return new LlmQuizGenerationAdapter(ChatClient.builder(chatModel),
                new ClassPathResource("prompts/quiz-generation-system.md"),
                new ClassPathResource("prompts/quiz-extended-generation-system.md"),
                reasoningEffort, Duration.ofSeconds(300), MAX_TOKENS);
    }

    @Test
    void 응답_JSON_을_GeneratedQuiz_로_바꾼다() {
        GeneratedQuiz quiz = adapterWith(new StubChatModel(prompt -> respond(QUIZ_JSON))).generateQuiz(REQUEST);

        assertThat(quiz.concepts()).singleElement().satisfies(concept -> {
            assertThat(concept.name()).isEqualTo("TCP");
            assertThat(concept.chunkSeqs()).containsExactly(1);
            GeneratedQuestion question = concept.questions().getFirst();
            assertThat(question.cognitiveType()).isEqualTo(CognitiveType.REMEMBER);
            assertThat(question.sourceChunkSeq()).isEqualTo(1);
            // record 의 isCorrect 가 스키마와 같은 이름으로 읽혀야 정답이 남는다
            assertThat(question.options()).extracting(GeneratedOption::content, GeneratedOption::isCorrect)
                    .containsExactly(tuple("O", true), tuple("X", false));
        });
    }

    @Test
    void system_프롬프트_파일과_조립한_user_메시지를_보낸다() {
        StubChatModel chatModel = new StubChatModel(prompt -> respond(QUIZ_JSON));

        adapterWith(chatModel).generateQuiz(REQUEST);

        assertThat(chatModel.received.getInstructions())
                .extracting(Message::getMessageType)
                .containsExactly(MessageType.SYSTEM, MessageType.USER);
        assertThat(chatModel.received.getSystemMessage().getText()).contains("학습용 퀴즈를 만드는 출제자");
        assertThat(chatModel.received.getUserMessage().getText())
                .isEqualTo(QuizGenerationUserMessage.from(REQUEST));
    }

    @Test
    void 근거_문제는_근거_문제_프롬프트로_보낸다() {
        StubChatModel chatModel = new StubChatModel(prompt -> respond(QUIZ_JSON));

        adapterWith(chatModel).generateQuiz(requestOf(SourceScope.GROUNDED));

        assertThat(chatModel.received.getSystemMessage().getText())
                .contains("학습용 퀴즈를 만드는 출제자")
                .doesNotContain("심화 문제를 만드는 출제자");
    }

    // 난이도가 아니라 요청의 문제 종류로 고른다. 어떤 종류로 낼지는 애플리케이션이 정한다
    @Test
    void 심화_문제는_심화_문제_프롬프트로_보낸다() {
        StubChatModel chatModel = new StubChatModel(prompt -> respond(QUIZ_JSON));

        adapterWith(chatModel).generateQuiz(requestOf(SourceScope.EXTENDED));

        assertThat(chatModel.received.getSystemMessage().getText())
                .contains("심화 문제를 만드는 출제자")
                .doesNotContain("학습용 퀴즈를 만드는 출제자");
    }

    // 정하지 않으면 Spring AI 가 호출마다 60초를 붙여, 설정한 타임아웃이 먹지 않는다
    @Test
    void 설정한_타임아웃을_호출_옵션에_담는다() {
        StubChatModel chatModel = new StubChatModel(prompt -> respond(QUIZ_JSON));

        adapterWith(chatModel).generateQuiz(REQUEST);

        assertThat(chatModel.received.getOptions()).isInstanceOfSatisfying(OpenAiChatOptions.class,
                options -> assertThat(options.getTimeout()).isEqualTo(Duration.ofSeconds(300)));
    }

    @Test
    void 추론_수준을_정하면_생성_요청에_담는다() {
        StubChatModel chatModel = new StubChatModel(prompt -> respond(QUIZ_JSON));

        adapterWith(chatModel, "high").generateQuiz(REQUEST);

        assertThat(chatModel.received.getOptions()).isInstanceOfSatisfying(OpenAiChatOptions.class,
                options -> assertThat(options.getReasoningEffort()).isEqualTo("high"));
    }

    // 비워 두면 게이트웨이의 기본값을 쓴다
    @Test
    void 추론_수준을_비워_두면_보내지_않는다() {
        StubChatModel chatModel = new StubChatModel(prompt -> respond(QUIZ_JSON));

        adapterWith(chatModel, "").generateQuiz(REQUEST);

        assertThat(chatModel.received.getOptions()).isInstanceOfSatisfying(OpenAiChatOptions.class,
                options -> assertThat(options.getReasoningEffort()).isNull());
    }

    // 프롬프트에 형식을 부탁하는 대신 response_format 의 JSON 스키마로 강제한다. 스키마의 필드 이름이 응답을 읽는 이름과 같아야 한다
    @Test
    void GeneratedQuiz_의_JSON_스키마를_response_format_으로_보낸다() {
        StubChatModel chatModel = new StubChatModel(prompt -> respond(QUIZ_JSON));

        adapterWith(chatModel).generateQuiz(REQUEST);

        assertThat(chatModel.received.getOptions()).isInstanceOfSatisfying(OpenAiChatOptions.class, options -> {
            assertThat(options.getResponseFormat().getType()).isEqualTo(OpenAiChatModel.ResponseFormat.Type.JSON_SCHEMA);
            assertThat(options.getResponseFormat().getJsonSchema())
                    .contains("\"concepts\"", "\"sourceQuote\"", "\"sourceChunkSeq\"", "\"isCorrect\"");
        });
        assertThat(chatModel.received.getUserMessage().getText()).doesNotContain("JSON Schema");
    }

    @Test
    void 호출이_실패하면_QuizGenerationException_으로_감싼다() {
        RuntimeException failure = new RuntimeException("429 Too Many Requests");
        LlmQuizGenerationAdapter adapter = adapterWith(new StubChatModel(prompt -> {
            throw failure;
        }));

        assertThatThrownBy(() -> adapter.generateQuiz(REQUEST))
                .isInstanceOf(QuizGenerationException.class)
                .hasRootCause(failure);
    }

    // luna 가 답을 쓰다 max_tokens 에 걸리면 이렇게 온다
    @Test
    void JSON_이_깨져_있으면_QuizGenerationException_이다() {
        String cutOff = QUIZ_JSON.substring(0, QUIZ_JSON.length() / 2);
        LlmQuizGenerationAdapter adapter = adapterWith(new StubChatModel(prompt -> respond(cutOff)));

        assertThatThrownBy(() -> adapter.generateQuiz(REQUEST)).isInstanceOf(QuizGenerationException.class);
    }

    // luna 가 추론에 출력 상한을 다 쓰면 이렇게 온다
    @Test
    void 응답이_비어_있으면_QuizGenerationException_이다() {
        LlmQuizGenerationAdapter adapter = adapterWith(new StubChatModel(prompt -> respond("")));

        assertThatThrownBy(() -> adapter.generateQuiz(REQUEST)).isInstanceOf(QuizGenerationException.class);
    }

    // Claude 가 max_tokens 에 걸리면 게이트웨이가 빈 객체를 정상 응답처럼 돌려준다. 파싱은 되지만 쓸 내용이 없다
    @Test
    void 빈_객체가_오면_QuizGenerationException_이다() {
        LlmQuizGenerationAdapter adapter = adapterWith(new StubChatModel(prompt -> respond("{}")));

        assertThatThrownBy(() -> adapter.generateQuiz(REQUEST)).isInstanceOf(QuizGenerationException.class);
    }

    @Test
    void 개념_목록이_비어_있으면_QuizGenerationException_이다() {
        LlmQuizGenerationAdapter adapter = adapterWith(new StubChatModel(prompt -> respond("{\"concepts\": []}")));

        assertThatThrownBy(() -> adapter.generateQuiz(REQUEST)).isInstanceOf(QuizGenerationException.class);
    }

    // 끝난 이유는 모델마다 믿을 수 없어, 출력 토큰이 상한에 닿았으면 내용이 온전해 보여도 버린다
    @Test
    void 출력이_상한에_닿으면_내용이_있어도_QuizGenerationException_이다() {
        LlmQuizGenerationAdapter adapter = adapterWith(new StubChatModel(prompt -> respond(QUIZ_JSON, MAX_TOKENS)));

        assertThatThrownBy(() -> adapter.generateQuiz(REQUEST)).isInstanceOf(QuizGenerationException.class);
    }

    @Test
    void 출력이_상한보다_적으면_그대로_쓴다() {
        GeneratedQuiz quiz = adapterWith(new StubChatModel(prompt -> respond(QUIZ_JSON, MAX_TOKENS - 1)))
                .generateQuiz(REQUEST);

        assertThat(quiz.concepts()).hasSize(1);
    }
}
