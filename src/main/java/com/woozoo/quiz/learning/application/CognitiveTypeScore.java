package com.woozoo.quiz.learning.application;

import com.woozoo.quiz.quiz.domain.CognitiveType;

public record CognitiveTypeScore(CognitiveType cognitiveType, int answeredCount,
                                 int correctAnswerCount, Double accuracy, boolean isEnoughData) {
}
