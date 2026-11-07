package com.woozoo.quiz.infrastructure.validation;

import com.woozoo.quiz.quiz.domain.CognitiveType;
import com.woozoo.quiz.quiz.port.JudgedQuestionType;
import com.woozoo.quiz.quiz.port.QuestionToJudgeType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

// 가짜 생성기가 모든 문제를 REMEMBER 로 내므로, REMEMBER 가 아닌 판정은 곧 탈락이다
class FakeQuestionTypeJudgeAdapterTest {

    private static QuestionToJudgeType question(int index) {
        return new QuestionToJudgeType(index, "문제", "인용", "해설", List.of(), "청크");
    }

    private static List<QuestionToJudgeType> questions(int count) {
        return IntStream.range(0, count).mapToObj(FakeQuestionTypeJudgeAdapterTest::question).toList();
    }

    private static List<Integer> rejectedIndexes(List<JudgedQuestionType> judged) {
        return judged.stream()
                .filter(question -> question.cognitiveType() != CognitiveType.REMEMBER)
                .map(JudgedQuestionType::index)
                .toList();
    }

    @Test
    void 주기마다_한_문제를_불일치로_판정한다() {
        FakeQuestionTypeJudgeAdapter adapter = new FakeQuestionTypeJudgeAdapter(5);

        assertThat(rejectedIndexes(adapter.judge(questions(10)))).containsExactly(4, 9);
    }

    @Test
    void 주기보다_문제가_적으면_모두_일치로_판정한다() {
        FakeQuestionTypeJudgeAdapter adapter = new FakeQuestionTypeJudgeAdapter(5);

        assertThat(rejectedIndexes(adapter.judge(questions(4)))).isEmpty();
    }

    @Test
    void 주기가_0_이면_탈락시키지_않는다() {
        FakeQuestionTypeJudgeAdapter adapter = new FakeQuestionTypeJudgeAdapter(0);

        assertThat(rejectedIndexes(adapter.judge(questions(10)))).isEmpty();
    }

    @Test
    void 받은_문제마다_판정을_하나씩_돌려준다() {
        FakeQuestionTypeJudgeAdapter adapter = new FakeQuestionTypeJudgeAdapter(5);

        assertThat(adapter.judge(questions(3))).extracting(JudgedQuestionType::index).containsExactly(0, 1, 2);
    }

    @Test
    void 주기는_목록_위치가_아니라_받은_번호로_센다() {
        FakeQuestionTypeJudgeAdapter adapter = new FakeQuestionTypeJudgeAdapter(5);

        assertThat(rejectedIndexes(adapter.judge(List.of(question(4))))).containsExactly(4);
    }
}
