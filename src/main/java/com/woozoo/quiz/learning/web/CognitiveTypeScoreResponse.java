package com.woozoo.quiz.learning.web;

import com.woozoo.quiz.learning.application.CognitiveTypeScore;
import com.woozoo.quiz.quiz.domain.CognitiveType;
import io.swagger.v3.oas.annotations.media.Schema;

public record CognitiveTypeScoreResponse(
        CognitiveType cognitiveType,
        @Schema(description = "이 유형에서 푼 문제 수. 같은 문제를 여러 번 풀었으면 한 번만 센다")
        int answeredCount,
        @Schema(description = "그중 가장 최근 답이 맞은 문제 수")
        int correctAnswerCount,
        @Schema(nullable = true, description = "맞힌 수 / 푼 수 (0 ~ 1). 이 유형을 푼 적이 없으면 null")
        Double accuracy,
        @Schema(description = "푼 문제가 기준 수 이상이면 true. false 면 정답률을 믿기 어려워 데이터 부족으로 보여 준다")
        boolean isEnoughData) {

    public static CognitiveTypeScoreResponse from(CognitiveTypeScore score) {
        return new CognitiveTypeScoreResponse(
                score.cognitiveType(),
                score.answeredCount(),
                score.correctAnswerCount(),
                score.accuracy(),
                score.isEnoughData());
    }
}
