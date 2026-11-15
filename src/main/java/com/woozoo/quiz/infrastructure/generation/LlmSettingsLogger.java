package com.woozoo.quiz.infrastructure.generation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

// 환경 변수로 바꾼 모델, 출력 상한, 타임아웃이 실제로 들어갔는지 서버가 뜰 때 한 번 남긴다.
// 키 이름을 잘못 쓰면 값이 조용히 기본값으로 돌아가서, 실패한 뒤에야 알게 된다. 주소와 키는 남기지 않는다
@Slf4j
@Component
@ConditionalOnProperty(name = "quiz.generation.generator", havingValue = "llm")
class LlmSettingsLogger {

    private final Environment environment;

    LlmSettingsLogger(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    void logSettings() {
        log.info("LLM 설정: 생성 모델 {}, 출력 상한 {}, 추론 수준 {}, 판정 모델 {}, 판정 출력 상한 {}, 판정 추론 수준 {}, 타임아웃 {}, 재시도 {}",
                value("spring.ai.openai.chat.model"),
                value("spring.ai.openai.chat.max-tokens"),
                value("quiz.generation.reasoning-effort"),
                value("quiz.generation.judge-model"),
                value("quiz.generation.judge-max-tokens"),
                value("quiz.generation.judge-reasoning-effort"),
                value("spring.ai.openai.timeout"),
                value("spring.ai.openai.max-retries"));
    }

    private String value(String key) {
        String value = environment.getProperty(key);
        return value == null || value.isBlank() ? "기본값" : value;
    }
}
