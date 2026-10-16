package com.woozoo.quiz.quiz.web;

import com.woozoo.quiz.quiz.domain.GenerationJob;
import com.woozoo.quiz.quiz.domain.GenerationStatus;

// quizId 는 DONE 일 때만, errorCode 는 FAILED 일 때만 값이 있다
public record GenerationJobResponse(
        long jobId,
        GenerationStatus status,
        Long quizId,
        String errorCode) {

    public static GenerationJobResponse from(GenerationJob job) {
        return new GenerationJobResponse(job.id(), job.status(), job.quizId(), job.errorCode());
    }
}
