package com.woozoo.quiz.quiz.domain;

public record NewGenerationJob(
        long userId,
        Long materialId,
        QuizKind kind,
        QuizFormat format,
        Difficulty difficulty) {
}