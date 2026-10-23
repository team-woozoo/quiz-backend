package com.woozoo.quiz.quiz.port;

import com.woozoo.quiz.quiz.domain.CognitiveType;
import com.woozoo.quiz.quiz.domain.Difficulty;
import com.woozoo.quiz.quiz.domain.QuizFormat;

import java.util.List;
import java.util.Map;

// cognitiveTypeCounts 는 유형별로 만들 문제 수다. 합은 questionCount 와 같다
public record QuizGenerationRequest (
        List<SourceChunk> chunks, QuizFormat format,
        Difficulty difficulty, int questionCount,
        Map<CognitiveType, Integer> cognitiveTypeCounts,
        List<ExistingConcept> existingConcepts) {}
