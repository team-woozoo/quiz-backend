package com.woozoo.quiz.learning.domain;

import com.woozoo.quiz.quiz.domain.StoredOption;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 정답은 앱이 보낸 값이 아니라 DB 의 보기로 판단한다. 안 푼 문제와 잘못된 답안을 어떻게 다루는지 본다
class QuizGraderTest {

    // 퀴즈 하나: 문제 101, 102, 103 이 이 순서로 있고 문제마다 보기 두 개 중 하나가 정답이다
    private static final List<StoredOption> OPTIONS = List.of(
            new StoredOption(501, 101, 1, "O", true),
            new StoredOption(502, 101, 2, "X", false),
            new StoredOption(511, 102, 1, "O", false),
            new StoredOption(512, 102, 2, "X", true),
            new StoredOption(521, 103, 1, "O", true),
            new StoredOption(522, 103, 2, "X", false));

    private static SubmittedAnswer answer(long questionId, long optionId) {
        return new SubmittedAnswer(questionId, optionId);
    }

    @Test
    void 정답_보기를_고르면_맞고_다른_보기를_고르면_틀린다() {
        List<QuestionResult> results = QuizGrader.grade(OPTIONS, List.of(
                answer(101, 501),
                answer(102, 511),
                answer(103, 521)));

        assertThat(results).containsExactly(
                new QuestionResult(101, 501L, true),
                new QuestionResult(102, 511L, false),
                new QuestionResult(103, 521L, true));
    }

    @Test
    void 보내지_않은_문제는_고른_보기_없이_틀린_것으로_나온다() {
        List<QuestionResult> results = QuizGrader.grade(OPTIONS, List.of(answer(102, 512)));

        assertThat(results).containsExactly(
                new QuestionResult(101, null, false),
                new QuestionResult(102, 512L, true),
                new QuestionResult(103, null, false));
    }

    // 결과 화면은 퀴즈의 문제 순서대로 그린다
    @Test
    void 답안을_보낸_순서와_상관없이_퀴즈의_문제_순서로_나온다() {
        List<QuestionResult> results = QuizGrader.grade(OPTIONS, List.of(
                answer(103, 521),
                answer(101, 502)));

        assertThat(results).extracting(QuestionResult::questionId).containsExactly(101L, 102L, 103L);
    }

    @Test
    void 답안이_없으면_모든_문제가_안_푼_것으로_나온다() {
        List<QuestionResult> results = QuizGrader.grade(OPTIONS, List.of());

        assertThat(results).hasSize(3).allSatisfy(result -> {
            assertThat(result.selectedOptionId()).isNull();
            assertThat(result.correct()).isFalse();
        });
    }

    @Test
    void 같은_문제를_두_번_보내면_거부한다() {
        assertThatThrownBy(() -> QuizGrader.grade(OPTIONS, List.of(
                answer(101, 501),
                answer(101, 502))))
                .isInstanceOf(InvalidSubmissionException.class)
                .hasMessageContaining("questionId=101");
    }

    @Test
    void 이_퀴즈에_없는_보기를_보내면_거부한다() {
        assertThatThrownBy(() -> QuizGrader.grade(OPTIONS, List.of(answer(101, 9999))))
                .isInstanceOf(InvalidSubmissionException.class)
                .hasMessageContaining("optionId=9999");
    }

    // 다른 문제의 정답 보기를 보내 맞은 것으로 만들 수 없어야 한다
    @Test
    void 다른_문제의_보기를_보내면_정답_보기라도_거부한다() {
        assertThatThrownBy(() -> QuizGrader.grade(OPTIONS, List.of(answer(101, 512))))
                .isInstanceOf(InvalidSubmissionException.class)
                .hasMessageContaining("questionId=101")
                .hasMessageContaining("optionId=512");
    }

    @Test
    void 퀴즈에_없는_문제_번호로_보내도_보기가_맞지_않아_거부한다() {
        assertThatThrownBy(() -> QuizGrader.grade(OPTIONS, List.of(answer(999, 501))))
                .isInstanceOf(InvalidSubmissionException.class);
    }

    @Test
    void 보기가_넷인_객관식도_정답_보기_하나만_맞는다() {
        List<StoredOption> multipleChoice = List.of(
                new StoredOption(601, 201, 1, "A", false),
                new StoredOption(602, 201, 2, "B", false),
                new StoredOption(603, 201, 3, "C", true),
                new StoredOption(604, 201, 4, "D", false));

        assertThat(QuizGrader.grade(multipleChoice, List.of(answer(201, 603))))
                .containsExactly(new QuestionResult(201, 603L, true));
        assertThat(QuizGrader.grade(multipleChoice, List.of(answer(201, 604))))
                .containsExactly(new QuestionResult(201, 604L, false));
    }
}
