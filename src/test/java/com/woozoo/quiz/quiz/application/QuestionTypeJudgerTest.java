package com.woozoo.quiz.quiz.application;

import com.woozoo.quiz.quiz.domain.CognitiveType;
import com.woozoo.quiz.quiz.port.GeneratedConcept;
import com.woozoo.quiz.quiz.port.GeneratedOption;
import com.woozoo.quiz.quiz.port.GeneratedQuestion;
import com.woozoo.quiz.quiz.port.GeneratedQuiz;
import com.woozoo.quiz.quiz.port.JudgedQuestionType;
import com.woozoo.quiz.quiz.port.QuestionToJudgeType;
import com.woozoo.quiz.quiz.port.QuestionTypeJudgeException;
import com.woozoo.quiz.quiz.port.QuestionTypeJudgePort;
import com.woozoo.quiz.quiz.port.SourceChunk;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class QuestionTypeJudgerTest {

    private static final List<SourceChunk> CHUNKS = List.of(
            new SourceChunk(1, "TCP는 연결 지향 프로토콜이다."),
            new SourceChunk(2, "UDP는 비연결형 프로토콜이다."));

    private final QuestionTypeJudgePort port = mock(QuestionTypeJudgePort.class);
    private final QuestionTypeJudger judger = new QuestionTypeJudger(port);

    private static GeneratedQuestion question(String content, CognitiveType type, int chunkSeq) {
        return new GeneratedQuestion(content, "인용", type, "해설",
                List.of(new GeneratedOption("O", true), new GeneratedOption("X", false)), chunkSeq);
    }

    private static GeneratedConcept concept(String name, GeneratedQuestion... questions) {
        return new GeneratedConcept(name, name + " 설명", List.of(1), List.of(questions));
    }

    private static GeneratedQuiz quiz(GeneratedConcept... concepts) {
        return new GeneratedQuiz(List.of(concepts));
    }

    private static JudgedQuestionType judged(int index, CognitiveType type) {
        return new JudgedQuestionType(index, type);
    }

    private static List<String> contentsOf(GeneratedQuiz quiz) {
        return quiz.concepts().stream()
                .flatMap(concept -> concept.questions().stream())
                .map(GeneratedQuestion::content)
                .toList();
    }

    @Test
    void 두_모델의_유형이_같은_문제만_남긴다() {
        GeneratedQuiz quiz = quiz(concept("TCP",
                question("문제0", CognitiveType.REMEMBER, 1),
                question("문제1", CognitiveType.APPLY, 1),
                question("문제2", CognitiveType.UNDERSTAND, 1)));
        given(port.judge(any())).willReturn(List.of(
                judged(0, CognitiveType.REMEMBER),
                judged(1, CognitiveType.UNDERSTAND),
                judged(2, CognitiveType.UNDERSTAND)));

        GeneratedQuiz result = judger.keepMatched(CHUNKS, quiz);

        assertThat(result.concepts()).hasSize(1);
        assertThat(contentsOf(result)).containsExactly("문제0", "문제2");
    }

    @Test
    void 남은_문제가_있는_개념은_이름과_설명과_청크를_그대로_가진다() {
        GeneratedConcept concept = new GeneratedConcept("TCP", "연결 지향", List.of(1, 2), List.of(
                question("문제0", CognitiveType.REMEMBER, 1),
                question("문제1", CognitiveType.APPLY, 2)));
        given(port.judge(any())).willReturn(List.of(
                judged(0, CognitiveType.REMEMBER),
                judged(1, CognitiveType.REMEMBER)));

        GeneratedConcept result = judger.keepMatched(CHUNKS, quiz(concept)).concepts().getFirst();

        assertThat(result.name()).isEqualTo("TCP");
        assertThat(result.description()).isEqualTo("연결 지향");
        assertThat(result.chunkSeqs()).containsExactly(1, 2);
    }

    @Test
    void 문제가_다_빠진_개념은_남기지_않는다() {
        GeneratedQuiz quiz = quiz(
                concept("TCP", question("문제0", CognitiveType.APPLY, 1)),
                concept("UDP", question("문제1", CognitiveType.REMEMBER, 2)));
        given(port.judge(any())).willReturn(List.of(
                judged(0, CognitiveType.REMEMBER),
                judged(1, CognitiveType.REMEMBER)));

        GeneratedQuiz result = judger.keepMatched(CHUNKS, quiz);

        assertThat(result.concepts()).extracting(GeneratedConcept::name).containsExactly("UDP");
    }

    @Test
    void 모두_불일치하면_빈_퀴즈를_돌려준다() {
        GeneratedQuiz quiz = quiz(concept("TCP", question("문제0", CognitiveType.APPLY, 1)));
        given(port.judge(any())).willReturn(List.of(judged(0, CognitiveType.REMEMBER)));

        assertThat(judger.keepMatched(CHUNKS, quiz).concepts()).isEmpty();
    }

    // 번호가 개념마다 0 으로 돌아가면 다른 개념의 문제와 판정이 섞인다
    @Test
    void 번호는_개념을_넘어_이어진다() {
        GeneratedQuiz quiz = quiz(
                concept("TCP",
                        question("문제0", CognitiveType.REMEMBER, 1),
                        question("문제1", CognitiveType.REMEMBER, 1)),
                concept("UDP",
                        question("문제2", CognitiveType.APPLY, 2)));
        given(port.judge(any())).willReturn(List.of(
                judged(0, CognitiveType.REMEMBER),
                judged(1, CognitiveType.UNDERSTAND),
                judged(2, CognitiveType.APPLY)));

        GeneratedQuiz result = judger.keepMatched(CHUNKS, quiz);

        assertThat(contentsOf(result)).containsExactly("문제0", "문제2");
    }

    @Test
    void 응답_순서가_달라도_번호로_문제를_찾는다() {
        GeneratedQuiz quiz = quiz(concept("TCP",
                question("문제0", CognitiveType.REMEMBER, 1),
                question("문제1", CognitiveType.APPLY, 1)));
        given(port.judge(any())).willReturn(List.of(
                judged(1, CognitiveType.APPLY),
                judged(0, CognitiveType.REMEMBER)));

        assertThat(contentsOf(judger.keepMatched(CHUNKS, quiz))).containsExactly("문제0", "문제1");
    }

    // 위치로 비교하면 빠진 문제 뒤의 판정이 한 칸씩 밀려 맞는 문제까지 떨어진다
    @Test
    void 판정이_빠진_문제만_뺀다() {
        GeneratedQuiz quiz = quiz(concept("TCP",
                question("문제0", CognitiveType.REMEMBER, 1),
                question("문제1", CognitiveType.UNDERSTAND, 1),
                question("문제2", CognitiveType.APPLY, 1)));
        given(port.judge(any())).willReturn(List.of(
                judged(0, CognitiveType.REMEMBER),
                judged(2, CognitiveType.APPLY)));

        assertThat(contentsOf(judger.keepMatched(CHUNKS, quiz))).containsExactly("문제0", "문제2");
    }

    @Test
    void 같은_번호가_두_번_오면_먼저_온_판정을_쓴다() {
        GeneratedQuiz quiz = quiz(concept("TCP", question("문제0", CognitiveType.REMEMBER, 1)));
        given(port.judge(any())).willReturn(List.of(
                judged(0, CognitiveType.REMEMBER),
                judged(0, CognitiveType.APPLY)));

        assertThat(contentsOf(judger.keepMatched(CHUNKS, quiz))).containsExactly("문제0");
    }

    @Test
    void 보내지_않은_번호의_판정은_무시한다() {
        GeneratedQuiz quiz = quiz(concept("TCP", question("문제0", CognitiveType.REMEMBER, 1)));
        given(port.judge(any())).willReturn(List.of(
                judged(0, CognitiveType.REMEMBER),
                judged(7, CognitiveType.APPLY)));

        assertThat(contentsOf(judger.keepMatched(CHUNKS, quiz))).containsExactly("문제0");
    }

    @Test
    void 문제마다_번호와_근거_청크의_본문을_붙여_보낸다() {
        GeneratedQuiz quiz = quiz(
                concept("TCP", question("문제0", CognitiveType.REMEMBER, 1)),
                concept("UDP", question("문제1", CognitiveType.REMEMBER, 2)));
        given(port.judge(any())).willReturn(List.of());

        judger.keepMatched(CHUNKS, quiz);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<QuestionToJudgeType>> sent = ArgumentCaptor.forClass(List.class);
        verify(port).judge(sent.capture());
        assertThat(sent.getValue()).extracting(QuestionToJudgeType::index).containsExactly(0, 1);
        assertThat(sent.getValue()).extracting(QuestionToJudgeType::content).containsExactly("문제0", "문제1");
        assertThat(sent.getValue()).extracting(QuestionToJudgeType::sourceChunk)
                .containsExactly("TCP는 연결 지향 프로토콜이다.", "UDP는 비연결형 프로토콜이다.");
    }

    @Test
    void 문제가_없으면_판정을_부르지_않는다() {
        GeneratedQuiz empty = quiz();

        assertThat(judger.keepMatched(CHUNKS, empty)).isSameAs(empty);
        verify(port, never()).judge(any());
    }

    @Test
    void 판정이_실패하면_받은_퀴즈를_그대로_돌려준다() {
        GeneratedQuiz quiz = quiz(concept("TCP", question("문제0", CognitiveType.APPLY, 1)));
        given(port.judge(any())).willThrow(new QuestionTypeJudgeException("호출 실패"));

        assertThat(judger.keepMatched(CHUNKS, quiz)).isSameAs(quiz);
    }

    // 호출 실패만 살린다. 코드 버그까지 삼키면 라벨을 확인하지 않은 채 저장되는 것을 알아챌 수 없다
    @Test
    void 판정_실패가_아닌_오류는_그대로_올린다() {
        GeneratedQuiz quiz = quiz(concept("TCP", question("문제0", CognitiveType.APPLY, 1)));
        given(port.judge(any())).willThrow(new IllegalStateException("버그"));

        assertThatThrownBy(() -> judger.keepMatched(CHUNKS, quiz))
                .isInstanceOf(IllegalStateException.class);
    }
}
