package com.woozoo.quiz.infrastructure.validation;

import com.woozoo.quiz.quiz.domain.CognitiveType;
import com.woozoo.quiz.quiz.port.GeneratedOption;
import com.woozoo.quiz.quiz.port.JudgedQuestionType;
import com.woozoo.quiz.quiz.port.QuestionToJudgeType;
import com.woozoo.quiz.quiz.port.QuestionTypeJudgeException;
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

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 게이트웨이를 부르지 않고, 응답을 정해 둔 ChatModel 로 어댑터가 보내는 것과 받은 것을 다루는 방식을 본다
class LlmQuestionTypeJudgeAdapterTest {

    private static final List<QuestionToJudgeType> QUESTIONS = List.of(
            new QuestionToJudgeType(3, "TCP 의 특징은?", "TCP는 연결 지향 프로토콜이다.", "자료에 그대로 나온다.",
                    List.of(new GeneratedOption("연결 지향", true), new GeneratedOption("비연결형", false)),
                    "TCP는 연결 지향 프로토콜이다.\n신뢰성 있는 전송을 보장한다."));

    private static final String VERDICTS_JSON = """
            {"verdicts": [{"index": 3, "reason": "원문의 사실 하나로 풀린다.", "cognitiveType": "REMEMBER"}]}
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
        return new ChatResponse(
                List.of(new Generation(new AssistantMessage(text))),
                ChatResponseMetadata.builder().usage(new DefaultUsage(100, 50)).build());
    }

    private static LlmQuestionTypeJudgeAdapter adapterWith(ChatModel chatModel) {
        return new LlmQuestionTypeJudgeAdapter(ChatClient.builder(chatModel),
                new ClassPathResource("prompts/question-type-judge-system.md"), "judge-model", 3000);
    }

    @Test
    void 응답_JSON_을_판정_목록으로_바꾼다() {
        List<JudgedQuestionType> judged = adapterWith(new StubChatModel(prompt -> respond(VERDICTS_JSON)))
                .judge(QUESTIONS);

        assertThat(judged).containsExactly(
                new JudgedQuestionType(3, "원문의 사실 하나로 풀린다.", CognitiveType.REMEMBER));
    }

    @Test
    void 정해진_모델과_출력_상한으로_보낸다() {
        StubChatModel chatModel = new StubChatModel(prompt -> respond(VERDICTS_JSON));

        adapterWith(chatModel).judge(QUESTIONS);

        assertThat(chatModel.received.getOptions()).isInstanceOfSatisfying(OpenAiChatOptions.class, options -> {
            assertThat(options.getModel()).isEqualTo("judge-model");
            assertThat(options.getMaxTokens()).isEqualTo(3000);
        });
    }

    @Test
    void 판정의_JSON_스키마를_response_format_으로_보낸다() {
        StubChatModel chatModel = new StubChatModel(prompt -> respond(VERDICTS_JSON));

        adapterWith(chatModel).judge(QUESTIONS);

        assertThat(chatModel.received.getOptions()).isInstanceOfSatisfying(OpenAiChatOptions.class, options -> {
            assertThat(options.getResponseFormat().getType()).isEqualTo(OpenAiChatModel.ResponseFormat.Type.JSON_SCHEMA);
            assertThat(options.getResponseFormat().getJsonSchema())
                    .contains("\"verdicts\"", "\"index\"", "\"reason\"", "\"cognitiveType\"");
        });
    }

    @Test
    void system_프롬프트와_문제를_담은_user_메시지를_보낸다() {
        StubChatModel chatModel = new StubChatModel(prompt -> respond(VERDICTS_JSON));

        adapterWith(chatModel).judge(QUESTIONS);

        assertThat(chatModel.received.getSystemMessage().getText()).contains("인지 유형을 분류하는");
        assertThat(chatModel.received.getUserMessage().getText()).isEqualTo("""
                <question index="3">
                content: TCP 의 특징은?
                options:
                - (정답) 연결 지향
                - 비연결형
                explanation: 자료에 그대로 나온다.
                source_quote: TCP는 연결 지향 프로토콜이다.
                source_chunk:
                TCP는 연결 지향 프로토콜이다.
                신뢰성 있는 전송을 보장한다.
                </question>

                """);
    }

    // 원문의 { } 를 템플릿 변수로 읽으면 호출 전에 실패한다
    @Test
    void 자료의_중괄호를_그대로_보낸다() {
        StubChatModel chatModel = new StubChatModel(prompt -> respond(VERDICTS_JSON));
        List<QuestionToJudgeType> withBraces = List.of(new QuestionToJudgeType(0, "문제", "인용", "해설",
                List.of(), "int main() { return 0; }"));

        adapterWith(chatModel).judge(withBraces);

        assertThat(chatModel.received.getUserMessage().getText()).contains("int main() { return 0; }");
    }

    @Test
    void 호출이_실패하면_QuestionTypeJudgeException_으로_감싼다() {
        RuntimeException failure = new RuntimeException("429 Too Many Requests");
        LlmQuestionTypeJudgeAdapter adapter = adapterWith(new StubChatModel(prompt -> {
            throw failure;
        }));

        assertThatThrownBy(() -> adapter.judge(QUESTIONS))
                .isInstanceOf(QuestionTypeJudgeException.class)
                .hasRootCause(failure);
    }

    // max_tokens 에 걸려 잘린 응답이 이렇게 온다
    @Test
    void JSON_이_깨져_있으면_QuestionTypeJudgeException_이다() {
        String cutOff = VERDICTS_JSON.substring(0, VERDICTS_JSON.length() / 2);
        LlmQuestionTypeJudgeAdapter adapter = adapterWith(new StubChatModel(prompt -> respond(cutOff)));

        assertThatThrownBy(() -> adapter.judge(QUESTIONS)).isInstanceOf(QuestionTypeJudgeException.class);
    }

    @Test
    void 응답이_비어_있으면_QuestionTypeJudgeException_이다() {
        LlmQuestionTypeJudgeAdapter adapter = adapterWith(new StubChatModel(prompt -> respond("")));

        assertThatThrownBy(() -> adapter.judge(QUESTIONS)).isInstanceOf(QuestionTypeJudgeException.class);
    }

    @Test
    void 판정이_하나도_없으면_QuestionTypeJudgeException_이다() {
        LlmQuestionTypeJudgeAdapter adapter = adapterWith(new StubChatModel(prompt -> respond("{\"verdicts\": []}")));

        assertThatThrownBy(() -> adapter.judge(QUESTIONS)).isInstanceOf(QuestionTypeJudgeException.class);
    }
}
