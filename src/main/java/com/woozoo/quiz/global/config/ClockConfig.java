package com.woozoo.quiz.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

//서비스가 현재 시각을 직접 읽지 않고 주입받아, 테스트에서 시각을 고정할 수 있게 한다
@Configuration
public class ClockConfig {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
