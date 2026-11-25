package com.woozoo.quiz.infrastructure.generation;

import com.woozoo.quiz.concept.domain.ConceptTag;
import com.woozoo.quiz.quiz.domain.CognitiveType;
import com.woozoo.quiz.quiz.domain.Difficulty;
import com.woozoo.quiz.quiz.domain.QuizFormat;
import com.woozoo.quiz.quiz.domain.SourceScope;
import com.woozoo.quiz.quiz.port.ConceptFocus;
import com.woozoo.quiz.quiz.port.AssignedConcept;
import com.woozoo.quiz.quiz.port.QuizGenerationRequest;
import com.woozoo.quiz.quiz.port.SourceChunk;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QuizGenerationUserMessageTest {

    // 워커가 넘기는 것과 같이 유형 순서대로 담는다
    private static Map<CognitiveType, Integer> mix() {
        Map<CognitiveType, Integer> counts = new EnumMap<>(CognitiveType.class);
        counts.put(CognitiveType.REMEMBER, 2);
        counts.put(CognitiveType.UNDERSTAND, 2);
        counts.put(CognitiveType.APPLY, 3);
        counts.put(CognitiveType.ANALYZE, 2);
        counts.put(CognitiveType.EVALUATE, 1);
        return counts;
    }

    @Test
    void 유형별_개수가_없으면_cognitiveTypes_줄을_쓰지_않는다() {
        QuizGenerationRequest request = QuizGenerationRequest.forNormal(
                List.of(new SourceChunk(1, "IP는 패킷을 전달한다.")), QuizFormat.OX, Difficulty.EASY, 5, Map.of(), List.of(), SourceScope.GROUNDED);

        assertThat(QuizGenerationUserMessage.from(request)).doesNotContain("cognitiveTypes");
    }

    @Test
    void 조건_배정_개념_청크를_태그로_나눠_적는다() {
        QuizGenerationRequest request = QuizGenerationRequest.forNormal(
                List.of(new SourceChunk(1, "TCP는 연결 지향 프로토콜이다.\n신뢰성 있는 전송을 보장한다."),
                        new SourceChunk(2, "UDP는 비연결형이다.")),
                QuizFormat.MULTIPLE_CHOICE, Difficulty.HARD, 10, mix(),
                List.of(new AssignedConcept(1, "TCP 3-way handshake", "연결을 맺는 과정이다.",
                                List.of(1), List.of(ConceptTag.RULE, ConceptTag.CONTRAST), 2),
                        new AssignedConcept(2, "UDP", "비연결형 전송이다.", List.of(2), List.of(), 0)),
                SourceScope.GROUNDED);

        assertThat(QuizGenerationUserMessage.from(request)).isEqualTo("""
                <conditions>
                format: MULTIPLE_CHOICE
                difficulty: HARD
                questionCount: 10
                cognitiveTypes: REMEMBER 2, UNDERSTAND 2, APPLY 3, ANALYZE 2, EVALUATE 1
                </conditions>

                <concepts>
                <concept no="1" chunkSeqs="1" tags="RULE,CONTRAST" questionCount="2">
                name: TCP 3-way handshake
                description: 연결을 맺는 과정이다.
                </concept>
                <concept no="2" chunkSeqs="2" tags="없음" questionCount="0">
                name: UDP
                description: 비연결형 전송이다.
                </concept>
                </concepts>

                <material>
                <chunk seq="1">
                TCP는 연결 지향 프로토콜이다.
                신뢰성 있는 전송을 보장한다.
                </chunk>
                <chunk seq="2">
                UDP는 비연결형이다.
                </chunk>
                </material>
                """);
    }

    // 개념이 여러 청크에 걸치면 청크 번호를 쉼표로 잇는다
    @Test
    void 개념의_청크_번호를_쉼표로_잇는다() {
        QuizGenerationRequest request = QuizGenerationRequest.forNormal(
                List.of(new SourceChunk(1, "IP는 패킷을 전달한다.")), QuizFormat.OX, Difficulty.EASY, 5, Map.of(),
                List.of(new AssignedConcept(1, "IP", "패킷 전달", List.of(1, 4, 7), List.of(ConceptTag.RULE), 0)),
                SourceScope.GROUNDED);

        assertThat(QuizGenerationUserMessage.from(request))
                .contains("<concept no=\"1\" chunkSeqs=\"1,4,7\" tags=\"RULE\" questionCount=\"0\">");
    }

    // 약점 퀴즈는 개념 목록 대신 겨냥한 개념과 이미 낸 문제를 보낸다
    @Test
    void 겨냥한_개념이_있으면_개념_목록_대신_개념과_이미_낸_문제를_적는다() {
        QuizGenerationRequest request = QuizGenerationRequest.forWeakness(
                List.of(new SourceChunk(1, "TCP는 연결 지향 프로토콜이다.")), QuizFormat.OX, Difficulty.NORMAL, 5, Map.of(),
                new ConceptFocus("TCP 연결 과정", "연결을 맺는 과정이다.", List.of("TCP는 연결형인가?")));

        String message = QuizGenerationUserMessage.from(request);

        assertThat(message).doesNotContain("<concepts>").contains("""
                <focus_concept>
                name: TCP 연결 과정
                description: 연결을 맺는 과정이다.
                </focus_concept>

                <asked_questions>
                - TCP는 연결형인가?
                </asked_questions>

                <material>
                """);
    }

    @Test
    void 이미_낸_문제가_없으면_없음이라고_적는다() {
        QuizGenerationRequest request = QuizGenerationRequest.forWeakness(
                List.of(new SourceChunk(1, "IP는 패킷을 전달한다.")), QuizFormat.OX, Difficulty.NORMAL, 5, Map.of(),
                new ConceptFocus("IP", "패킷 전달", List.of()));

        assertThat(QuizGenerationUserMessage.from(request)).contains("""
                <asked_questions>
                없음
                </asked_questions>
                """);
    }

    // 인용을 원문과 글자 그대로 대조하므로, 코드가 든 슬라이드의 기호와 공백도 바꾸지 않고 넣어야 한다
    @Test
    void 원문의_기호와_공백을_바꾸지_않는다() {
        String code = "if (a < b && c > 0) {\n    return \"ok\";\n}";
        QuizGenerationRequest request = QuizGenerationRequest.forNormal(
                List.of(new SourceChunk(3, code)), QuizFormat.OX, Difficulty.NORMAL, 1, Map.of(), List.of(), SourceScope.GROUNDED);

        assertThat(QuizGenerationUserMessage.from(request))
                .contains("<chunk seq=\"3\">\n" + code + "\n</chunk>");
    }
}
