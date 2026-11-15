package com.woozoo.quiz.infrastructure.generation;

import com.woozoo.quiz.quiz.domain.SourceScope;
import com.woozoo.quiz.quiz.port.GeneratedQuiz;
import com.woozoo.quiz.quiz.port.QuizGenerationException;
import com.woozoo.quiz.quiz.port.QuizGenerationPort;
import com.woozoo.quiz.quiz.port.QuizGenerationRequest;
import com.openai.models.completions.CompletionUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.time.Duration;

// 학교 LLM 게이트웨이(OpenAI 호환 API, 뒤에서는 Bedrock 의 Claude)로 퀴즈를 만든다. llm 프로필에서만 뜬다
@Slf4j
@Component
@ConditionalOnProperty(name = "quiz.generation.generator", havingValue = "llm")
public class LlmQuizGenerationAdapter implements QuizGenerationPort {

    private final ChatClient chatClient;
    private final Resource groundedPrompt;
    private final Resource extendedPrompt;
    private final String reasoningEffort;

    public LlmQuizGenerationAdapter(ChatClient.Builder clientBuilder,
                                    @Value("classpath:prompts/quiz-generation-system.md")
                                    Resource groundedPrompt,
                                    @Value("classpath:prompts/quiz-extended-generation-system.md")
                                    Resource extendedPrompt,
                                    @Value("${quiz.generation.reasoning-effort:}")
                                    String reasoningEffort,
                                    @Value("${spring.ai.openai.timeout}")
                                    Duration timeout) {
        this.groundedPrompt = groundedPrompt;
        this.extendedPrompt = extendedPrompt;
        this.reasoningEffort = reasoningEffort.isBlank() ? null : reasoningEffort.strip();
        // 호출 옵션에는 Spring AI 기본값 60초의 타임아웃이 따로 붙어 클라이언트 설정보다 앞선다. 그래서 여기서 직접 정한다.
        // 판정 모델은 추론 수준을 받지 않을 수 있어, 공통 설정이 아니라 생성 클라이언트에만 붙인다.
        // 비어 있으면 보내지 않아 게이트웨이의 기본값을 쓴다
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder().timeout(timeout);
        if (this.reasoningEffort != null) {
            options.reasoningEffort(this.reasoningEffort);
        }
        this.chatClient = clientBuilder
                .defaultOptions(options)
                .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .build();
    }

    @Override
    public GeneratedQuiz generateQuiz(QuizGenerationRequest request) {
        String userMessage = QuizGenerationUserMessage.from(request);
        long startTime = System.nanoTime();
        ResponseEntity<ChatResponse, GeneratedQuiz> result;
        // 프롬프트는 LLM 으로 만드는 방식에 딸린 것이라 어댑터가 가진다. 애플리케이션은 문제 종류만 넘긴다
        Resource prompt = request.scope() == SourceScope.EXTENDED ? extendedPrompt : groundedPrompt;
        try {
            result = chatClient.prompt()
                    .system(prompt)
                    .user(userMessage)
                    .call()
                    .responseEntity(new LenientOutputConverter<>(GeneratedQuiz.class));
        } catch (RuntimeException e) {
            // 타임아웃인지, 추론에 출력 상한을 다 써서 빈 응답이 온 것인지는 걸린 시간으로 가늠한다
            long failedAfterMs = Duration.ofNanos(System.nanoTime() - startTime).toMillis();
            throw new QuizGenerationException("LLM 호출이나 응답 해석에 실패했다 (%s, %dms, 추론 수준 %s)".formatted(
                    request.scope(), failedAfterMs, effortLabel()), e);
        }
        long elapsedMs = Duration.ofNanos(System.nanoTime() - startTime).toMillis();

        Usage usage = result.response().getMetadata().getUsage();

        // 추론에 출력 상한을 다 쓰면 내용 없이 끝난다(finish_reason=length). 그 경우를 가르려고 사용량을 남긴다
        if (result.entity() == null) {
            log.warn("LLM 응답을 해석하지 못했다({}): {}ms, 끝난 이유 {}, 입력 토큰 {}, 출력 토큰 {}, 추론 토큰 {}, 추론 수준 {}",
                    request.scope(), elapsedMs, finishReasonOf(result.response()), usage.getPromptTokens(),
                    usage.getCompletionTokens(), reasoningTokensOf(usage), effortLabel());
            throw new QuizGenerationException("LLM 응답이 비었거나 형식에 맞지 않는다");
        }

        log.info("LLM 생성 완료({}): {}ms, 입력 토큰 {}, 출력 토큰 {}, 추론 토큰 {}, 추론 수준 {}",
                request.scope(), elapsedMs, usage.getPromptTokens(), usage.getCompletionTokens(),
                reasoningTokensOf(usage), effortLabel());

        return result.entity();
    }

    private String effortLabel() {
        return reasoningEffort == null ? "기본값" : reasoningEffort;
    }

    private static String finishReasonOf(ChatResponse response) {
        return response.getResult() == null ? null : response.getResult().getMetadata().getFinishReason();
    }

    // 추론 토큰은 출력 토큰에 포함된다. 게이트웨이가 넘겨주지 않으면 null 로 찍힌다
    private static Long reasoningTokensOf(Usage usage) {
        if (usage.getNativeUsage() instanceof CompletionUsage completionUsage) {
            return completionUsage.completionTokensDetails()
                    .flatMap(CompletionUsage.CompletionTokensDetails::reasoningTokens)
                    .orElse(null);
        }
        return null;
    }
}
