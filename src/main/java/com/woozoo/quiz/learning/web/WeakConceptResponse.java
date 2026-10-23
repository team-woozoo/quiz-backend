package com.woozoo.quiz.learning.web;

import com.woozoo.quiz.learning.application.WeakConceptScore;
import io.swagger.v3.oas.annotations.media.Schema;

public record WeakConceptResponse(
        long conceptId,
        String conceptName,
        @Schema(description = "개념이 나온 강의 자료. 자료가 다르면 이름이 같아도 다른 개념이다")
        long materialId,
        String materialTitle,
        @Schema(description = "이 개념에서 푼 문제 수. 같은 문제를 여러 번 풀었으면 한 번만 센다")
        int answeredCount,
        @Schema(description = "그중 가장 최근 답이 맞은 문제 수")
        int correctAnswerCount,
        @Schema(description = "맞힌 수 / 푼 수 (0 ~ 1)")
        double accuracy) {

    public static WeakConceptResponse from(WeakConceptScore score) {
        return new WeakConceptResponse(
                score.conceptId(),
                score.conceptName(),
                score.materialId(),
                score.materialTitle(),
                score.answeredCount(),
                score.correctAnswerCount(),
                score.accuracy());
    }
}
