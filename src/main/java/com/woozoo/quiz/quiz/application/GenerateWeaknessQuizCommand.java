package com.woozoo.quiz.quiz.application;

import com.woozoo.quiz.quiz.domain.Difficulty;
import com.woozoo.quiz.quiz.domain.QuizFormat;

// materialConceptId 는 자료 분석과 약한 개념 API 가 주는 개념 id 다
public record GenerateWeaknessQuizCommand(long userId, long materialConceptId, QuizFormat format, Difficulty difficulty) {
}
