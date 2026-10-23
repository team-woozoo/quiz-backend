package com.woozoo.quiz.learning.application;

import java.util.List;

// accuracy 는 안 푼 문제를 틀린 것으로 본 정답률이고, 그 개념의 문제가 없으면 null 이다
public record MaterialConceptScore(
        long conceptId,
        String conceptName,
        List<Integer> pageNos,
        int questionCount,
        int answeredCount,
        int correctCount,
        Double accuracy,
        ConceptStatus status) {
}
