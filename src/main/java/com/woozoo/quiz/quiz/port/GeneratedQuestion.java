package com.woozoo.quiz.quiz.port;

import com.woozoo.quiz.quiz.domain.CognitiveType;

import java.util.List;

public record GeneratedQuestion(String content, String sourceQuote,
                                CognitiveType cognitiveType, String explanation,
                                List<GeneratedOption> options,
                                int sourceChunkSeq) {
}
