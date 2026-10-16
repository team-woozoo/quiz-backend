package com.woozoo.quiz.quiz.web;

import com.woozoo.quiz.quiz.application.GenerateQuizCommand;
import com.woozoo.quiz.quiz.domain.Difficulty;
import com.woozoo.quiz.quiz.domain.QuizFormat;
import jakarta.validation.constraints.NotNull;

public record GenerateQuizRequest(
        @NotNull
        QuizFormat format,

        @NotNull
        Difficulty difficulty
) {

    public GenerateQuizCommand toCommand(long userId, long materialId) {
        return new GenerateQuizCommand(userId, materialId, format, difficulty);
    }
}
