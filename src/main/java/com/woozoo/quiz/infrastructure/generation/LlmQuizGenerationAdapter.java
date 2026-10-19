package com.woozoo.quiz.infrastructure.generation;

import com.woozoo.quiz.quiz.port.GeneratedQuiz;
import com.woozoo.quiz.quiz.port.QuizGenerationException;
import com.woozoo.quiz.quiz.port.QuizGenerationPort;
import com.woozoo.quiz.quiz.port.QuizGenerationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
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

    public LlmQuizGenerationAdapter(ChatClient.Builder clientBuilder,
                                    @Value("classpath:prompts/quiz-generation-system.md")
                                    Resource systemPrompt) {
        this.chatClient = clientBuilder
                .defaultSystem(systemPrompt)
                .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .build();
    }

    @Override
    public GeneratedQuiz generateQuiz(QuizGenerationRequest request) {
        String userMessage = QuizGenerationUserMessage.from(request);
        long startTime = System.nanoTime();
        ResponseEntity<ChatResponse, GeneratedQuiz> result;
        try {
            result = chatClient.prompt()
                    .user(userMessage)
                    .call()
                    .responseEntity(GeneratedQuiz.class);
        } catch (RuntimeException e) {
            throw new QuizGenerationException("LLM 호출이나 응답 해석에 실패했다", e);
        }
        long elapsedMs = Duration.ofNanos(System.nanoTime() - startTime).toMillis();

        if (result.entity() == null) {
            throw new QuizGenerationException("LLM 호출 결과가 비어있다");
        }

        Usage usage = result.response().getMetadata().getUsage();

        log.info("LLM 생성 완료: {}ms, 입력 토큰 {}, 출력 토큰 {}",
                elapsedMs, usage.getPromptTokens(), usage.getCompletionTokens());

        return result.entity();
    }
}
