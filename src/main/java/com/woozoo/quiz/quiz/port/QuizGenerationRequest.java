package com.woozoo.quiz.quiz.port;

import com.woozoo.quiz.quiz.domain.CognitiveType;
import com.woozoo.quiz.quiz.domain.Difficulty;
import com.woozoo.quiz.quiz.domain.QuizFormat;
import com.woozoo.quiz.quiz.domain.SourceScope;

import java.util.List;
import java.util.Map;

// cognitiveTypeCounts 는 유형별로 만들 문제 수다. 합은 questionCount 와 같다.
// focus 는 약점 퀴즈처럼 개념 하나로만 낼 때 있고, 자료 전체로 낼 때는 null 이다.
// scope 는 근거 문제와 심화 문제 중 무엇으로 낼지다. 요청을 만드는 쪽이 반드시 정하게 기본값을 두지 않는다
public record QuizGenerationRequest (
        List<SourceChunk> chunks, QuizFormat format,
        Difficulty difficulty, int questionCount,
        Map<CognitiveType, Integer> cognitiveTypeCounts,
        List<ExistingConcept> existingConcepts,
        ConceptFocus focus,
        SourceScope scope) {

    public QuizGenerationRequest(List<SourceChunk> chunks, QuizFormat format, Difficulty difficulty,
                                 int questionCount, Map<CognitiveType, Integer> cognitiveTypeCounts,
                                 List<ExistingConcept> existingConcepts, SourceScope scope) {
        this(chunks, format, difficulty, questionCount, cognitiveTypeCounts, existingConcepts, null, scope);
    }
}
