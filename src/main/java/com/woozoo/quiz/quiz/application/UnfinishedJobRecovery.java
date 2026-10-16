package com.woozoo.quiz.quiz.application;

import com.woozoo.quiz.global.error.ErrorCode;
import com.woozoo.quiz.quiz.persistence.GenerationJobRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// 생성 중에 서버가 재시작되면 그 작업은 이어 갈 스레드가 없어 PROCESSING 으로 영영 남는다.
// 서버가 한 대라 뜨는 시점에 끝나지 않은 작업은 모두 이전 프로세스가 남긴 것이다. 여러 대가 되면 started_at 으로 골라야 한다
@Slf4j
@Component
public class UnfinishedJobRecovery {

    private final GenerationJobRepository generationJobRepository;

    public UnfinishedJobRecovery(GenerationJobRepository generationJobRepository) {
        this.generationJobRepository = generationJobRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void failUnfinishedJobs() {
        int count = generationJobRepository.failAllUnfinished(ErrorCode.GENERATION_FAILED);
        if (count > 0) {
            log.warn("재시작 전에 끝나지 않은 생성 작업 {}개를 실패로 바꿨다", count);
        }
    }
}
