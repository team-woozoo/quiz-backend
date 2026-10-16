package com.woozoo.quiz.quiz.domain;

public record NewGenerationJob(
        long userId,
        // 약점 퀴즈(WEAKNESS)는 자료 없이 만들어 null 이다
        Long materialId,
        QuizKind kind,
        QuizFormat format,
        Difficulty difficulty) {
}