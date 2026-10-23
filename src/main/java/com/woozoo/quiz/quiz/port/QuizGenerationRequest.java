package com.woozoo.quiz.quiz.port;

import com.woozoo.quiz.quiz.domain.CognitiveType;
import com.woozoo.quiz.quiz.domain.Difficulty;
import com.woozoo.quiz.quiz.domain.QuizFormat;

import java.util.List;
import java.util.Map;

// cognitiveTypeCounts 는 유형별로 만들 문제 수다. 합은 questionCount 와 같다.
// focus 는 약점 퀴즈처럼 개념 하나로만 낼 때 있고, 자료 전체로 낼 때는 null 이다
public record QuizGenerationRequest (
        List<SourceChunk> chunks, QuizFormat format,
        Difficulty difficulty, int questionCount,
        Map<CognitiveType, Integer> cognitiveTypeCounts,
        List<ExistingConcept> existingConcepts,
        ConceptFocus focus) {

    public QuizGenerationRequest(List<SourceChunk> chunks, QuizFormat format, Difficulty difficulty, int questionCount,
                                 Map<CognitiveType, Integer> cognitiveTypeCounts, List<ExistingConcept> existingConcepts) {
        this(chunks, format, difficulty, questionCount, cognitiveTypeCounts, existingConcepts, null);
    }
}
