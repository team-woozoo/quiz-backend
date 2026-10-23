package com.woozoo.quiz.learning.application;

public record WeakConceptScore(
        long conceptId,
        String conceptName,
        long materialId,
        String materialTitle,
        int answeredCount,
        int correctAnswerCount,
        double accuracy) {
}