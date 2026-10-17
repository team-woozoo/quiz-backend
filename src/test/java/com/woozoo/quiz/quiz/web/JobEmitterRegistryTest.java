package com.woozoo.quiz.quiz.web;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class JobEmitterRegistryTest {

    private final JobEmitterRegistry registry = new JobEmitterRegistry(Duration.ofMinutes(1));

    @Test
    void 연결하지_않은_작업은_찾을_수_없다() {
        assertThat(registry.find(1L)).isEmpty();
    }

    @Test
    void 같은_작업에_다시_연결하면_새_연결만_남는다() {
        registry.connect(1L);
        SseEmitter second = registry.connect(1L);

        assertThat(registry.find(1L)).containsSame(second);
    }

    @Test
    void 작업마다_연결을_따로_둔다() {
        SseEmitter first = registry.connect(1L);
        SseEmitter second = registry.connect(2L);

        assertThat(registry.find(1L)).containsSame(first);
        assertThat(registry.find(2L)).containsSame(second);
    }

    @Test
    void 설정한_타임아웃으로_연결을_만든다() {
        assertThat(registry.connect(1L).getTimeout()).isEqualTo(Duration.ofMinutes(1).toMillis());
    }
}
