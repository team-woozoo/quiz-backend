package com.woozoo.quiz.learning.domain;

import com.woozoo.quiz.quiz.domain.CognitiveType;

public record CognitiveTypeStat (CognitiveType cognitiveType, int answeredCount, int correctAnswerCount) {
}
