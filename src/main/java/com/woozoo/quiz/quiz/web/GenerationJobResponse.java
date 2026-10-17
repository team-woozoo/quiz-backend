package com.woozoo.quiz.quiz.web;

import com.woozoo.quiz.quiz.domain.GenerationJob;
import com.woozoo.quiz.quiz.domain.GenerationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

// quizId 는 DONE 일 때만, errorCode 는 FAILED 일 때만 값이 있다
public record GenerationJobResponse(
        long jobId,
        GenerationStatus status,
        @Schema(nullable = true, description = "DONE 일 때만 값이 있다")
        Long quizId,
        @Schema(nullable = true, description = "FAILED 일 때만 값이 있다")
        String errorCode) {

    public static GenerationJobResponse from(GenerationJob job) {
        return new GenerationJobResponse(job.id(), job.status(), job.quizId(), job.errorCode());
    }
}
