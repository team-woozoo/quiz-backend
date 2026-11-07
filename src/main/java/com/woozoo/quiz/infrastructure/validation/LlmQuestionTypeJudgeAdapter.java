package com.woozoo.quiz.infrastructure.validation;

import com.woozoo.quiz.quiz.port.JudgedQuestionType;
import com.woozoo.quiz.quiz.port.QuestionToJudgeType;
import com.woozoo.quiz.quiz.port.QuestionTypeJudgeException;
import com.woozoo.quiz.quiz.port.QuestionTypeJudgePort;
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
import java.util.List;

// 학교 LLM 게이트웨이로 생성기와 다른 모델에게 유형을 판정받는다. llm 프로필에서만 뜬다
@Slf4j
@Component
@ConditionalOnProperty(name = "quiz.generation.judge", havingValue = "llm")
public class LlmQuestionTypeJudgeAdapter implements QuestionTypeJudgePort {

    private final ChatClient chatClient;

    public LlmQuestionTypeJudgeAdapter(ChatClient.Builder clientBuilder,
                                       @Value("classpath:prompts/question-type-judge-system.md")
                                       Resource systemPrompt,
                                       @Value("${quiz.generation.judge-model}") String model,
                                       @Value("${quiz.generation.judge-max-tokens}") int maxTokens) {
        this.chatClient = clientBuilder
                .defaultSystem(systemPrompt)
                .defaultOptions(OpenAiChatOptions.builder().model(model).maxTokens(maxTokens).temperature(0.0))
                .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .build();
    }

    // reason 에는 강의 내용이 섞일 수 있어 로그에 남기지 않는다
    @Override
    public List<JudgedQuestionType> judge(List<QuestionToJudgeType> questions) {
        String userMessage = QuestionTypeJudgeUserMessage.from(questions);
        long startTime = System.nanoTime();
        ResponseEntity<ChatResponse, Response> result;
        try {
            result = chatClient.prompt()
                    .user(userMessage)
                    .call()
                    .responseEntity(Response.class);
        } catch (RuntimeException e) {
            throw new QuestionTypeJudgeException("LLM 호출이나 응답 해석에 실패했다", e);
        }
        long elapsedMs = Duration.ofNanos(System.nanoTime() - startTime).toMillis();

        // 판정이 하나도 없으면 모든 문제가 불일치로 떨어진다. 호출 실패로 다뤄 받은 퀴즈를 살린다
        if (result.entity() == null || result.entity().verdicts() == null || result.entity().verdicts().isEmpty()) {
            throw new QuestionTypeJudgeException("LLM 판정 결과가 비어있다");
        }

        Usage usage = result.response().getMetadata().getUsage();
        log.info("LLM 유형 판정 완료: {}ms, 입력 토큰 {}, 출력 토큰 {}",
                elapsedMs, usage.getPromptTokens(), usage.getCompletionTokens());

        return result.entity().verdicts();
    }

    // JSON 스키마는 최상위가 객체여야 해서 목록을 감싼다
    record Response(List<JudgedQuestionType> verdicts) {
    }
}
