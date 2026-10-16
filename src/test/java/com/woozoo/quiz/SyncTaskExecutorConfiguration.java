package com.woozoo.quiz;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

// @Async 작업을 호출한 스레드에서 바로 돌린다. 생성 요청이 돌아왔을 때 이미 끝나 있어 기다리지 않고 결과를 볼 수 있다
@TestConfiguration(proxyBeanMethods = false)
public class SyncTaskExecutorConfiguration {

    @Bean
    TaskExecutor taskExecutor() {
        return new SyncTaskExecutor();
    }
}
