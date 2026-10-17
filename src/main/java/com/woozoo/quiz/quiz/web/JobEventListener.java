package com.woozoo.quiz.quiz.web;

import com.woozoo.quiz.quiz.application.JobStatusChanged;
import com.woozoo.quiz.quiz.application.QuizGenerationService;
import com.woozoo.quiz.quiz.domain.GenerationJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Optional;

@Slf4j
@Component
public class JobEventListener {

    private final JobEmitterRegistry jobEmitterRegistry;
    private final QuizGenerationService quizGenerationService;

    public JobEventListener(JobEmitterRegistry jobEmitterRegistry, QuizGenerationService quizGenerationService) {
        this.jobEmitterRegistry = jobEmitterRegistry;
        this.quizGenerationService = quizGenerationService;
    }

    @EventListener
    public void sendJobStatus(JobStatusChanged event) {
        try {
            Optional<SseEmitter> emitter = jobEmitterRegistry.find(event.jobId());
            if (emitter.isEmpty()) {
                return;
            }

            Optional<GenerationJob> job = quizGenerationService.findJob(event.jobId());

            if (job.isEmpty()) {
                return;
            }

            GenerationJobResponse jobStatus = GenerationJobResponse.from(job.get());
            jobEmitterRegistry.send(emitter.get(), jobStatus);
        } catch (RuntimeException e) {
            log.warn("작업 상태 알림 실패: jobId={}", event.jobId(), e);
        }
    }
}
