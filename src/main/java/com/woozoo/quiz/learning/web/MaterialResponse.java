package com.woozoo.quiz.learning.web;

import com.woozoo.quiz.learning.application.MaterialOverview;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record MaterialResponse(
        long materialId,
        String title,
        String originalFilename,
        @Schema(description = "글자가 있어 저장된 페이지 수")
        int pageCount,
        @Schema(description = "올린 시각")
        Instant createdAt,
        @Schema(description = "이 자료로 만든 퀴즈들의 문제 수 합")
        int questionCount,
        @Schema(description = "그중 한 번이라도 푼 문제 수")
        int answeredCount,
        @Schema(description = "가장 최근 답이 맞은 문제 수")
        int correctCount,
        @Schema(description = "questionCount - correctCount. 안 푼 문제도 틀린 것으로 센다")
        int wrongCount,
        @Schema(nullable = true, description = "correctCount / questionCount (0 ~ 1). 문제를 만든 적이 없으면 null")
        Double accuracy,
        @Schema(description = "이 자료로 퀴즈를 만드는 중이면 true")
        boolean generating) {

    public static MaterialResponse from(MaterialOverview overview) {
        return new MaterialResponse(
                overview.material().id(),
                overview.material().title(),
                overview.material().originalFilename(),
                overview.material().pageCount(),
                overview.material().createdAt(),
                overview.questionCount(),
                overview.answeredCount(),
                overview.correctCount(),
                overview.wrongCount(),
                overview.accuracy(),
                overview.material().generating());
    }
}
