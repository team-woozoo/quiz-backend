package com.woozoo.quiz.learning.domain;

public record QuestionResult(long questionId, Long selectedOptionId, boolean correct) {
}
