package com.woozoo.quiz.quiz.domain;

public record NewQuiz(
        long userId,
        Long materialId,
        QuizKind kind,
        QuizFormat format,
        Difficulty difficulty) {
}
