package com.woozoo.quiz.infrastructure.generation;

import com.woozoo.quiz.quiz.domain.CognitiveType;
import com.woozoo.quiz.quiz.domain.Difficulty;
import com.woozoo.quiz.quiz.domain.QuizFormat;
import com.woozoo.quiz.quiz.port.ConceptFocus;
import com.woozoo.quiz.quiz.port.ExistingConcept;
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
        QuizGenerationRequest request = new QuizGenerationRequest(
                List.of(new SourceChunk(1, "IP는 패킷을 전달한다.")), QuizFormat.OX, Difficulty.EASY, 5, Map.of(), List.of());

        assertThat(QuizGenerationUserMessage.from(request)).doesNotContain("cognitiveTypes");
    }

    @Test
    void 조건_기존_개념_청크를_태그로_나눠_적는다() {
        QuizGenerationRequest request = new QuizGenerationRequest(
                List.of(new SourceChunk(1, "TCP는 연결 지향 프로토콜이다.\n신뢰성 있는 전송을 보장한다."),
                        new SourceChunk(2, "UDP는 비연결형이다.")),
                QuizFormat.MULTIPLE_CHOICE, Difficulty.HARD, 10, mix(),
                List.of(new ExistingConcept("TCP 3-way handshake", "연결을 맺는 과정이다.")));

        assertThat(QuizGenerationUserMessage.from(request)).isEqualTo("""
                <conditions>
                format: MULTIPLE_CHOICE
                difficulty: HARD
                questionCount: 10
                cognitiveTypes: REMEMBER 2, UNDERSTAND 2, APPLY 3, ANALYZE 2, EVALUATE 1
                </conditions>

                <existing_concepts>
                - TCP 3-way handshake: 연결을 맺는 과정이다.
                </existing_concepts>

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

    @Test
    void 기존_개념이_없으면_없음이라고_적는다() {
        QuizGenerationRequest request = new QuizGenerationRequest(
                List.of(new SourceChunk(1, "IP는 패킷을 전달한다.")), QuizFormat.OX, Difficulty.EASY, 5, Map.of(), List.of());

        assertThat(QuizGenerationUserMessage.from(request)).contains("""
                <existing_concepts>
                없음
                </existing_concepts>
                """);
    }

    // 약점 퀴즈는 기존 개념 목록 대신 겨냥한 개념과 이미 낸 문제를 보낸다
    @Test
    void 겨냥한_개념이_있으면_기존_개념_대신_개념과_이미_낸_문제를_적는다() {
        QuizGenerationRequest request = new QuizGenerationRequest(
                List.of(new SourceChunk(1, "TCP는 연결 지향 프로토콜이다.")), QuizFormat.OX, Difficulty.NORMAL, 5, Map.of(),
                List.of(), new ConceptFocus("TCP 연결 과정", "연결을 맺는 과정이다.", List.of("TCP는 연결형인가?")));

        String message = QuizGenerationUserMessage.from(request);

        assertThat(message).doesNotContain("<existing_concepts>").contains("""
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
        QuizGenerationRequest request = new QuizGenerationRequest(
                List.of(new SourceChunk(1, "IP는 패킷을 전달한다.")), QuizFormat.OX, Difficulty.NORMAL, 5, Map.of(),
                List.of(), new ConceptFocus("IP", "패킷 전달", List.of()));

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
        QuizGenerationRequest request = new QuizGenerationRequest(
                List.of(new SourceChunk(3, code)), QuizFormat.OX, Difficulty.NORMAL, 1, Map.of(), List.of());

        assertThat(QuizGenerationUserMessage.from(request))
                .contains("<chunk seq=\"3\">\n" + code + "\n</chunk>");
    }
}
