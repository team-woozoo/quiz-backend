package com.woozoo.quiz.learning.persistence;

import com.woozoo.quiz.TestcontainersConfiguration;
import com.woozoo.quiz.auth.persistence.UserRepository;
import com.woozoo.quiz.concept.persistence.MaterialConceptRepository;
import com.woozoo.quiz.learning.QuizFixture;
import com.woozoo.quiz.learning.domain.NewSubmission;
import com.woozoo.quiz.learning.domain.QuestionResult;
import com.woozoo.quiz.learning.domain.QuizSummary;
import com.woozoo.quiz.quiz.domain.Difficulty;
import com.woozoo.quiz.quiz.domain.QuizFormat;
import com.woozoo.quiz.material.persistence.MaterialRepository;
import com.woozoo.quiz.quiz.persistence.QuizRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class SubmissionRepositoryTest {

    @Autowired
    SubmissionRepository submissionRepository;
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

    QuizFixture.Created quiz;

    @BeforeEach
    void setup() {
        quiz = new QuizFixture(userRepository, materialRepository, materialConceptRepository, quizRepository)
                .createQuiz("submission@test.com", 3);
    }

    record SavedAnswer(long questionId, Long selectedOptionId, boolean isCorrect) {
    }

    private List<SavedAnswer> answersOf(long submissionId) {
        return jdbcClient.sql("""
                        SELECT question_id, selected_option_id, is_correct
                        FROM submission_answer
                        WHERE submission_id = :submissionId
                        ORDER BY question_id
                        """)
                .param("submissionId", submissionId)
                .query(SavedAnswer.class)
                .list();
    }

    @Test
    void 제출을_저장하면_사용자와_퀴즈가_남는다() {
        long submissionId = submissionRepository.save(new NewSubmission(quiz.userId(), quiz.quizId()));

        record SavedSubmission(long userId, long quizId) {
        }
        SavedSubmission saved = jdbcClient.sql("SELECT user_id, quiz_id FROM submission WHERE id = :id")
                .param("id", submissionId)
                .query(SavedSubmission.class)
                .single();
        assertThat(saved).isEqualTo(new SavedSubmission(quiz.userId(), quiz.quizId()));
    }

    @Test
    void 같은_퀴즈를_여러_번_제출하면_제출이_따로_쌓인다() {
        long first = submissionRepository.save(new NewSubmission(quiz.userId(), quiz.quizId()));
        long second = submissionRepository.save(new NewSubmission(quiz.userId(), quiz.quizId()));

        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void 문제별_결과를_저장하고_안_푼_문제는_고른_보기가_null_이다() {
        long submissionId = submissionRepository.save(new NewSubmission(quiz.userId(), quiz.quizId()));
        long q1 = quiz.questionIds().get(0);
        long q2 = quiz.questionIds().get(1);
        long q3 = quiz.questionIds().get(2);

        submissionRepository.saveAnswers(submissionId, List.of(
                new QuestionResult(q1, quiz.correctOptionOf(q1), true),
                new QuestionResult(q2, quiz.wrongOptionOf(q2), false),
                new QuestionResult(q3, null, false)));

        assertThat(answersOf(submissionId)).containsExactly(
                new SavedAnswer(q1, quiz.correctOptionOf(q1), true),
                new SavedAnswer(q2, quiz.wrongOptionOf(q2), false),
                new SavedAnswer(q3, null, false));
    }

    private QuizFixture fixture() {
        return new QuizFixture(userRepository, materialRepository, materialConceptRepository, quizRepository);
    }

    // 제출 하나를 저장한다. correctCount 만큼 앞에서부터 맞히고 나머지는 틀린다
    private long submitWith(QuizFixture.Created quiz, int correctCount) {
        long submissionId = submissionRepository.save(new NewSubmission(quiz.userId(), quiz.quizId()));
        List<Long> questionIds = quiz.questionIds();
        submissionRepository.saveAnswers(submissionId, java.util.stream.IntStream.range(0, questionIds.size())
                .mapToObj(i -> i < correctCount
                        ? new QuestionResult(questionIds.get(i), quiz.correctOptionOf(questionIds.get(i)), true)
                        : new QuestionResult(questionIds.get(i), quiz.wrongOptionOf(questionIds.get(i)), false))
                .toList());
        return submissionId;
    }

    @Test
    void 내_퀴즈_목록은_최근_퀴즈부터_퀴즈_정보와_문제_수를_준다() {
        QuizFixture.Created newer = fixture().createQuiz(quiz.userId(), 2);

        List<QuizSummary> summaries = submissionRepository.findQuizSummaries(quiz.userId(), null);

        assertThat(summaries).extracting(QuizSummary::quizId).containsExactly(newer.quizId(), quiz.quizId());
        assertThat(summaries).extracting(QuizSummary::questionCount).containsExactly(2, 3);
        assertThat(summaries.getFirst().materialTitle()).isEqualTo("네트워크 5강");
        assertThat(summaries.getFirst().format()).isEqualTo(QuizFormat.OX);
        assertThat(summaries.getFirst().difficulty()).isEqualTo(Difficulty.NORMAL);
        assertThat(summaries.getFirst().createdAt()).isNotNull();
    }

    @Test
    void 안_푼_퀴즈는_푼_횟수가_0_이고_최근_제출이_없다() {
        QuizSummary summary = submissionRepository.findQuizSummaries(quiz.userId(), null).getFirst();

        assertThat(summary.submissionCount()).isZero();
        assertThat(summary.lastSubmission()).isNull();
    }

    @Test
    void 여러_번_풀면_횟수와_가장_최근_제출의_맞힌_수를_준다() {
        submitWith(quiz, 1);
        submitWith(quiz, 3);

        QuizSummary summary = submissionRepository.findQuizSummaries(quiz.userId(), null).getFirst();

        assertThat(summary.submissionCount()).isEqualTo(2);
        assertThat(summary.lastSubmission()).isNotNull();
        assertThat(summary.lastSubmission().correctCount()).isEqualTo(3);
        assertThat(summary.lastSubmission().submittedAt()).isNotNull();
    }

    @Test
    void 남의_퀴즈는_내_목록에_없다() {
        QuizFixture.Created others = fixture().createQuiz("others@test.com", 1);

        assertThat(submissionRepository.findQuizSummaries(quiz.userId(), null))
                .extracting(QuizSummary::quizId)
                .containsExactly(quiz.quizId())
                .doesNotContain(others.quizId());
    }

    // 오답 노트 복습은 여러 퀴즈의 문제를 한 번에 보내서 퀴즈 없이 남는다
    @Test
    void 복습_제출은_퀴즈_없이_REVIEW_로_남는다() {
        long submissionId = submissionRepository.saveReview(quiz.userId());

        record SavedSubmission(long userId, Long quizId, String kind) {
        }
        assertThat(jdbcClient.sql("SELECT user_id, quiz_id, kind FROM submission WHERE id = :id")
                .param("id", submissionId).query(SavedSubmission.class).single())
                .isEqualTo(new SavedSubmission(quiz.userId(), null, "REVIEW"));
    }

    // 기록 탭의 날짜별 점수는 그날 퀴즈를 푼 결과라, 나중에 복습한 답이 섞이면 안 된다
    @Test
    void 복습_제출은_퀴즈의_푼_횟수와_최근_점수에_섞이지_않는다() {
        submitWith(quiz, 1);
        long reviewId = submissionRepository.saveReview(quiz.userId());
        long q2 = quiz.questionIds().get(1);
        submissionRepository.saveAnswers(reviewId, List.of(new QuestionResult(q2, quiz.correctOptionOf(q2), true)));

        QuizSummary summary = submissionRepository.findQuizSummaries(quiz.userId(), null).getFirst();

        assertThat(summary.submissionCount()).isEqualTo(1);
        assertThat(summary.lastSubmission().correctCount()).isEqualTo(1);
    }

    // 실패한 INSERT 는 트랜잭션을 깨뜨려, 제약마다 테스트를 나눈다
    @Test
    void 퀴즈_제출에_퀴즈가_없으면_DB_가_막는다() {
        assertThatThrownBy(() -> jdbcClient.sql("INSERT INTO submission (user_id, kind) VALUES (:userId, 'QUIZ')")
                .param("userId", quiz.userId()).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 복습_제출에_퀴즈가_있으면_DB_가_막는다() {
        assertThatThrownBy(() -> jdbcClient.sql("""
                        INSERT INTO submission (user_id, quiz_id, kind) VALUES (:userId, :quizId, 'REVIEW')
                        """)
                .param("userId", quiz.userId()).param("quizId", quiz.quizId()).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
