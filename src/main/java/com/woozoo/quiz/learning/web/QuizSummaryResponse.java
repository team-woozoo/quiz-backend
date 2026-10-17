package com.woozoo.quiz.learning.web;

import com.woozoo.quiz.learning.domain.QuizSummary;
import com.woozoo.quiz.quiz.domain.Difficulty;
import com.woozoo.quiz.quiz.domain.QuizFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record QuizSummaryResponse(
        long quizId,
        @Schema(nullable = true, description = "약점 퀴즈처럼 자료 없이 만든 퀴즈는 null")
        String materialTitle,
        QuizFormat format,
        Difficulty difficulty,
        int questionCount,
        Instant createdAt,
        @Schema(description = "이 퀴즈를 제출한 횟수")
        int submissionCount,
        @Schema(nullable = true, description = "가장 최근 제출의 결과. 아직 안 풀었으면 null")
        LastSubmissionResponse lastSubmission) {

    public static QuizSummaryResponse from(QuizSummary summary) {
        return new QuizSummaryResponse(
                summary.quizId(),
                summary.materialTitle(),
                summary.format(),
                summary.difficulty(),
                summary.questionCount(),
                summary.createdAt(),
                summary.submissionCount(),
                summary.lastSubmission() == null ? null : LastSubmissionResponse.from(summary.lastSubmission()));
    }

    public record LastSubmissionResponse(int correctCount, Instant submittedAt) {

        static LastSubmissionResponse from(QuizSummary.LastSubmission lastSubmission) {
            return new LastSubmissionResponse(lastSubmission.correctCount(), lastSubmission.submittedAt());
        }
    }
}
