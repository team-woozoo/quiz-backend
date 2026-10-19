package com.woozoo.quiz.infrastructure.generation;

import com.woozoo.quiz.quiz.domain.Difficulty;
import com.woozoo.quiz.quiz.domain.QuizFormat;
import com.woozoo.quiz.quiz.port.ExistingConcept;
import com.woozoo.quiz.quiz.port.QuizGenerationRequest;
import com.woozoo.quiz.quiz.port.SourceChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuizGenerationUserMessageTest {

    @Test
    void 조건_기존_개념_청크를_태그로_나눠_적는다() {
        QuizGenerationRequest request = new QuizGenerationRequest(
                List.of(new SourceChunk(1, "TCP는 연결 지향 프로토콜이다.\n신뢰성 있는 전송을 보장한다."),
                        new SourceChunk(2, "UDP는 비연결형이다.")),
                QuizFormat.MULTIPLE_CHOICE, Difficulty.HARD, 10,
                List.of(new ExistingConcept("TCP 3-way handshake", "연결을 맺는 과정이다.")));

        assertThat(QuizGenerationUserMessage.from(request)).isEqualTo("""
                <conditions>
                format: MULTIPLE_CHOICE
                difficulty: HARD
                questionCount: 10
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
                List.of(new SourceChunk(1, "IP는 패킷을 전달한다.")), QuizFormat.OX, Difficulty.EASY, 5, List.of());

        assertThat(QuizGenerationUserMessage.from(request)).contains("""
                <existing_concepts>
                없음
                </existing_concepts>
                """);
    }

    // 인용을 원문과 글자 그대로 대조하므로, 코드가 든 슬라이드의 기호와 공백도 바꾸지 않고 넣어야 한다
    @Test
    void 원문의_기호와_공백을_바꾸지_않는다() {
        String code = "if (a < b && c > 0) {\n    return \"ok\";\n}";
        QuizGenerationRequest request = new QuizGenerationRequest(
                List.of(new SourceChunk(3, code)), QuizFormat.OX, Difficulty.NORMAL, 1, List.of());

        assertThat(QuizGenerationUserMessage.from(request))
                .contains("<chunk seq=\"3\">\n" + code + "\n</chunk>");
    }
}
