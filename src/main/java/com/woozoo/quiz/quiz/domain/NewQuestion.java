package com.woozoo.quiz.quiz.domain;

public record NewQuestion(
        long quizId,
        int orderNo,
        long materialConceptId,
        long sourceChunkId,
        String sourceQuote,
        CognitiveType cognitiveType,
        String content,
        String explanation) {
}
