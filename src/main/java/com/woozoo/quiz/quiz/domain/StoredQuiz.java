package com.woozoo.quiz.quiz.domain;

public record StoredQuiz(
        long id,
        long userId,
        Long materialId,
        QuizKind kind,
        QuizFormat format,
        Difficulty difficulty) {
}
