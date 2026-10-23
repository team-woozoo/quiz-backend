package com.woozoo.quiz.learning.domain;

public record ConceptStat (long conceptId, String conceptName, long materialId,
                           String materialTitle, int answeredCount, int correctAnswerCount) {
}
