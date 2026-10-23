package com.woozoo.quiz.learning.web;

import com.woozoo.quiz.learning.application.ConceptStatus;
import com.woozoo.quiz.learning.application.MaterialConceptScore;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record MaterialConceptResponse(
        long conceptId,
        String conceptName,
        @Schema(description = "개념이 나온 PDF 페이지 번호(1부터), 작은 번호부터. 떨어져 있을 수 있어 범위가 아니라 목록으로 준다")
        List<Integer> pageNos,
        @Schema(description = "이 개념의 문제 수")
        int questionCount,
        @Schema(description = "그중 한 번이라도 푼 문제 수")
        int answeredCount,
        @Schema(description = "가장 최근 답이 맞은 문제 수")
        int correctCount,
        @Schema(nullable = true, description = "correctCount / questionCount (0 ~ 1). 안 푼 문제도 틀린 것으로 센다. 문제가 없으면 null")
        Double accuracy,
        @Schema(description = """
                WEAK: 정답률이 기준보다 낮다, GOOD: 기준 이상이지만 다 맞히지 못했다,
                PERFECT: 모두 맞혔다, UNCHECKED: 하나도 풀지 않았다""")
        ConceptStatus status) {

    public static MaterialConceptResponse from(MaterialConceptScore score) {
        return new MaterialConceptResponse(
                score.conceptId(),
                score.conceptName(),
                score.pageNos(),
                score.questionCount(),
                score.answeredCount(),
                score.correctCount(),
                score.accuracy(),
                score.status());
    }
}
