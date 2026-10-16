package com.woozoo.quiz.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

// @Async 메서드는 Spring Boot 가 만들어 두는 applicationTaskExecutor 스레드 풀에서 돈다
@Configuration
@EnableAsync
public class AsyncConfig {
}
