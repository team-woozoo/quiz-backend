package com.woozoo.quiz.quiz.application;

import com.woozoo.quiz.quiz.domain.Difficulty;
import com.woozoo.quiz.quiz.domain.QuizFormat;

public record GenerateQuizCommand(long userId, long materialId, QuizFormat format, Difficulty difficulty) {
}
