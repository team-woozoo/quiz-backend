package com.woozoo.quiz.quiz.domain;

public record GenerationJob(
        long id,
        long userId,
        Long materialId,
        QuizKind kind,
        QuizFormat format,
        Difficulty difficulty,
        GenerationStatus status,
        Long quizId,
        String errorCode,
        Long userConceptId) {
}
