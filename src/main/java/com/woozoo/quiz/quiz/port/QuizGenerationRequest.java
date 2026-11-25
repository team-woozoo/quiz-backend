package com.woozoo.quiz.quiz.port;

import com.woozoo.quiz.quiz.domain.CognitiveType;
import com.woozoo.quiz.quiz.domain.Difficulty;
import com.woozoo.quiz.quiz.domain.QuizFormat;
import com.woozoo.quiz.quiz.domain.SourceScope;

import java.util.List;
import java.util.Map;

// cognitiveTypeCounts 는 유형별로 만들 문제 수다. 합은 questionCount 와 같다.
// assignedConcepts 는 생성기가 문제를 붙일 개념이다. 생성기는 개념을 만들지 않고 no 로 가리킨다.
// focus 는 약점 퀴즈처럼 개념 하나로만 낼 때 있고, 자료 전체로 낼 때는 null 이다. 약점 퀴즈도 그 개념을 1번 배정 개념으로 함께 담는다.
// scope 는 근거 문제와 심화 문제 중 무엇으로 낼지다. 요청을 만드는 쪽이 반드시 정하게 기본값을 두지 않는다.
// 두 경우의 규칙이 달라 생성자 대신 forNormal, forWeakness 로 만든다
public record QuizGenerationRequest (
        List<SourceChunk> chunks, QuizFormat format,
        Difficulty difficulty, int questionCount,
        Map<CognitiveType, Integer> cognitiveTypeCounts,
        List<AssignedConcept> assignedConcepts,
        ConceptFocus focus,
        SourceScope scope) {

    // 자료 전체로 내는 일반 퀴즈다. 개념은 워커가 저장된 개념에 번호를 매겨 넘긴다
    public static QuizGenerationRequest forNormal(List<SourceChunk> chunks, QuizFormat format,
                                                  Difficulty difficulty, int questionCount,
                                                  Map<CognitiveType, Integer> cognitiveTypeCounts,
                                                  List<AssignedConcept> assignedConcepts,
                                                  SourceScope scope) {
        return new QuizGenerationRequest(chunks, format, difficulty,
                questionCount, cognitiveTypeCounts,
                assignedConcepts, null, scope);
    }

    // 약점 퀴즈다. 겨냥한 개념 하나를 1번 배정 개념으로 만들어 담는다
    public static QuizGenerationRequest forWeakness(List<SourceChunk> chunks, QuizFormat format,
                                                     Difficulty difficulty, int questionCount,
                                                     Map<CognitiveType, Integer> cognitiveTypeCounts,
                                                     ConceptFocus focus) {
        // 약점 퀴즈는 겨냥한 개념 하나로만 낸다. 그 개념을 1번으로 두고, 보낸 페이지를 모두 그 개념의 범위로 본다
        // HARD 여도 근거 문제로 낸다. 심화 프롬프트는 개념 하나로만 내는 방식을 아직 받쳐 주지 않는다
        AssignedConcept focusConcept = new AssignedConcept(1, focus.name(), focus.description(),
                chunks.stream().map(SourceChunk::seq).toList(), List.of(), 0);
        return new QuizGenerationRequest(chunks, format,
                difficulty, questionCount,
                cognitiveTypeCounts, List.of(focusConcept),
                focus, SourceScope.GROUNDED);
    }
}
