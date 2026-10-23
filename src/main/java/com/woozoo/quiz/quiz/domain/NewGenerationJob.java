package com.woozoo.quiz.quiz.domain;

public record NewGenerationJob(
        long userId,
        // 약점 퀴즈(WEAKNESS)는 자료 없이 만들어 null 이다
        Long materialId,
        QuizKind kind,
        QuizFormat format,
        Difficulty difficulty,
        // 약점 퀴즈가 겨냥하는 대표 개념. 일반 퀴즈는 null 이다
        Long userConceptId) {

    public NewGenerationJob(long userId, Long materialId, QuizKind kind, QuizFormat format, Difficulty difficulty) {
        this(userId, materialId, kind, format, difficulty, null);
    }
}
