package com.woozoo.quiz.quiz.domain;

public record StoredOption(long id, long questionId, int orderNo, String content, boolean isCorrect) {
}
