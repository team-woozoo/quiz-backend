package com.woozoo.quiz.learning.application;

import com.woozoo.quiz.TestcontainersConfiguration;
import com.woozoo.quiz.auth.persistence.UserRepository;
import com.woozoo.quiz.concept.persistence.MaterialConceptRepository;
import com.woozoo.quiz.global.error.BusinessException;
import com.woozoo.quiz.global.error.ErrorCode;
import com.woozoo.quiz.learning.QuizFixture;
import com.woozoo.quiz.learning.domain.QuestionResult;
import com.woozoo.quiz.learning.domain.SubmittedAnswer;
import com.woozoo.quiz.material.persistence.MaterialRepository;
import com.woozoo.quiz.quiz.persistence.QuizRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 채점, 제출 저장, 오답 노트 갱신이 한 번의 제출로 맞게 이어지는지 실제 DB 로 본다
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class SubmissionServiceTest {

    @Autowired
    SubmissionService submissionService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    MaterialRepository materialRepository;
    @Autowired
    MaterialConceptRepository materialConceptRepository;
    @Autowired
    QuizRepository quizRepository;
    @Autowired
    JdbcClient jdbcClient;

    QuizFixture fixture;
    QuizFixture.Created quiz;
    long q1;
    long q2;
    long q3;

    @BeforeEach
    void setup() {
        fixture = new QuizFixture(userRepository, materialRepository, materialConceptRepository, quizRepository);
        quiz = fixture.createQuiz("submit@test.com", 3);
        q1 = quiz.questionIds().get(0);
        q2 = quiz.questionIds().get(1);
        q3 = quiz.questionIds().get(2);
    }

    private SubmissionResult submit(SubmittedAnswer... answers) {
        return submissionService.submit(new SubmitQuizCommand(quiz.userId(), quiz.quizId(), List.of(answers)));
    }

    private SubmittedAnswer correct(long questionId) {
        return new SubmittedAnswer(questionId, quiz.correctOptionOf(questionId));
    }

    private SubmittedAnswer wrong(long questionId) {
        return new SubmittedAnswer(questionId, quiz.wrongOptionOf(questionId));
    }

    private long count(String sql, long id) {
        return jdbcClient.sql(sql).param("id", id).query(Long.class).single();
    }

    // 해결되지 않은 오답 노트 문제의 id
    private List<Long> unresolvedWrongAnswers() {
        return jdbcClient.sql("""
                        SELECT question_id FROM wrong_answer
                        WHERE user_id = :userId AND resolved_at IS NULL
                        ORDER BY question_id
                        """)
                .param("userId", quiz.userId())
                .query(Long.class)
                .list();
    }

    private int wrongCountOf(long questionId) {
        return jdbcClient.sql("SELECT wrong_count FROM wrong_answer WHERE user_id = :userId AND question_id = :questionId")
                .param("userId", quiz.userId())
                .param("questionId", questionId)
                .query(Integer.class)
                .single();
    }

    @Test
    void 제출하면_문제별_결과를_돌려주고_제출과_결과를_저장한다() {
        SubmissionResult result = submit(correct(q1), wrong(q2));

        assertThat(result.results()).containsExactly(
                new QuestionResult(q1, quiz.correctOptionOf(q1), true),
                new QuestionResult(q2, quiz.wrongOptionOf(q2), false),
                new QuestionResult(q3, null, false));
        assertThat(count("SELECT count(*) FROM submission WHERE id = :id", result.submissionId())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM submission_answer WHERE submission_id = :id", result.submissionId()))
                .isEqualTo(3);
    }

    // 맞힌 문제까지 오답으로 기록되면 오답 노트가 모든 문제로 채워진다
    @Test
    void 틀린_문제와_안_푼_문제만_오답_노트에_들어간다() {
        submit(correct(q1), wrong(q2));

        assertThat(unresolvedWrongAnswers()).containsExactly(q2, q3);
    }

    @Test
    void 오답_노트의_문제를_다음_제출에서_맞히면_해결된다() {
        submit(wrong(q1), correct(q2), correct(q3));

        submit(correct(q1), correct(q2), correct(q3));

        assertThat(unresolvedWrongAnswers()).isEmpty();
    }

    @Test
    void 같은_문제를_또_틀리면_오답_횟수가_는다() {
        submit(wrong(q1), correct(q2), correct(q3));

        submit(wrong(q1), correct(q2), correct(q3));

        assertThat(wrongCountOf(q1)).isEqualTo(2);
    }

    @Test
    void 같은_퀴즈를_다시_제출하면_제출이_따로_쌓인다() {
        long first = submit(correct(q1)).submissionId();
        long second = submit(correct(q1)).submissionId();

        assertThat(second).isNotEqualTo(first);
        assertThat(count("SELECT count(*) FROM submission WHERE quiz_id = :id", quiz.quizId())).isEqualTo(2);
    }

    @Test
    void 남의_퀴즈에는_제출할_수_없다() {
        long otherUserId = fixture.createUser("other@test.com");
        SubmitQuizCommand command = new SubmitQuizCommand(otherUserId, quiz.quizId(), List.of(correct(q1)));

        assertThatThrownBy(() -> submissionService.submit(command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.QUIZ_NOT_FOUND);
    }

    @Test
    void 없는_퀴즈에는_제출할_수_없다() {
        SubmitQuizCommand command = new SubmitQuizCommand(quiz.userId(), -1L, List.of(correct(q1)));

        assertThatThrownBy(() -> submissionService.submit(command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.QUIZ_NOT_FOUND);
    }

    @Test
    void 잘못된_답안은_INVALID_SUBMISSION_이고_아무것도_저장하지_않는다() {
        // 문제 1 에 문제 2 의 정답 보기를 보낸다
        SubmittedAnswer otherQuestionsOption = new SubmittedAnswer(q1, quiz.correctOptionOf(q2));

        assertThatThrownBy(() -> submit(otherQuestionsOption))
                .isInstanceOf(BusinessException.class)
                .hasCauseInstanceOf(com.woozoo.quiz.learning.domain.InvalidSubmissionException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_SUBMISSION);
        assertThat(count("SELECT count(*) FROM submission WHERE quiz_id = :id", quiz.quizId())).isZero();
        assertThat(unresolvedWrongAnswers()).isEmpty();
    }

    @Test
    void 다른_퀴즈의_보기로는_제출할_수_없다() {
        QuizFixture.Created otherQuiz = fixture.createQuiz(quiz.userId(), 1);
        long otherQuestion = otherQuiz.questionIds().getFirst();
        SubmittedAnswer otherQuizOption = new SubmittedAnswer(otherQuestion, otherQuiz.correctOptionOf(otherQuestion));

        assertThatThrownBy(() -> submit(otherQuizOption))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_SUBMISSION);
    }
}
