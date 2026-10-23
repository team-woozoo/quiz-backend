package com.woozoo.quiz.learning.persistence;

import com.woozoo.quiz.TestcontainersConfiguration;
import com.woozoo.quiz.auth.persistence.UserRepository;
import com.woozoo.quiz.concept.persistence.MaterialConceptRepository;
import com.woozoo.quiz.learning.QuizFixture;
import com.woozoo.quiz.learning.domain.QuestionResult;
import com.woozoo.quiz.learning.domain.WrongAnswerKey;
import com.woozoo.quiz.learning.domain.WrongAnswerQuestion;
import com.woozoo.quiz.quiz.domain.CognitiveType;
import com.woozoo.quiz.quiz.domain.Difficulty;
import com.woozoo.quiz.quiz.domain.NewQuestion;
import com.woozoo.quiz.quiz.domain.NewQuiz;
import com.woozoo.quiz.quiz.domain.QuizFormat;
import com.woozoo.quiz.quiz.domain.QuizKind;
import com.woozoo.quiz.material.persistence.MaterialRepository;
import com.woozoo.quiz.quiz.persistence.QuizRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// 테스트는 한 트랜잭션이라 now() 가 계속 같은 값이다. 시각이 바뀌는지는 보지 않고, 횟수와 해결 여부로 본다
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class WrongAnswerRepositoryTest {

    @Autowired
    WrongAnswerRepository wrongAnswerRepository;
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
    WrongAnswerKey key;

    @BeforeEach
    void setup() {
        fixture = new QuizFixture(userRepository, materialRepository, materialConceptRepository, quizRepository);
        quiz = fixture.createQuiz("wrong@test.com", 2);
        key = new WrongAnswerKey(quiz.userId(), quiz.questionIds().getFirst());
    }

    record Row(int wrongCount, OffsetDateTime resolvedAt) {
    }

    private Optional<Row> find(WrongAnswerKey key) {
        return jdbcClient.sql("""
                        SELECT wrong_count, resolved_at
                        FROM wrong_answer
                        WHERE user_id = :userId AND question_id = :questionId
                        """)
                .param("userId", key.userId())
                .param("questionId", key.questionId())
                .query((rs, rowNum) -> new Row(rs.getInt("wrong_count"),
                        rs.getObject("resolved_at", OffsetDateTime.class)))
                .optional();
    }

    @Test
    void 처음_틀리면_횟수_1_로_오답_노트에_들어간다() {
        wrongAnswerRepository.recordWrong(key);

        assertThat(find(key)).contains(new Row(1, null));
    }

    @Test
    void 다시_틀리면_새로_만들지_않고_횟수가_는다() {
        wrongAnswerRepository.recordWrong(key);
        wrongAnswerRepository.recordWrong(key);

        assertThat(find(key)).contains(new Row(2, null));
        Long rows = jdbcClient.sql("SELECT count(*) FROM wrong_answer WHERE user_id = :userId")
                .param("userId", quiz.userId())
                .query(Long.class)
                .single();
        assertThat(rows).isEqualTo(1L);
    }

    @Test
    void 맞히면_해결된다() {
        wrongAnswerRepository.recordWrong(key);

        wrongAnswerRepository.resolve(key);

        assertThat(find(key)).hasValueSatisfying(row -> assertThat(row.resolvedAt()).isNotNull());
    }

    @Test
    void 이미_해결된_문제를_다시_맞혀도_처음_해결_시각이_남는다() {
        wrongAnswerRepository.recordWrong(key);
        OffsetDateTime firstResolvedAt = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        jdbcClient.sql("""
                        UPDATE wrong_answer SET resolved_at = :resolvedAt
                        WHERE user_id = :userId AND question_id = :questionId
                        """)
                .param("resolvedAt", firstResolvedAt)
                .param("userId", key.userId())
                .param("questionId", key.questionId())
                .update();

        wrongAnswerRepository.resolve(key);

        assertThat(find(key)).hasValueSatisfying(row ->
                assertThat(row.resolvedAt().toInstant()).isEqualTo(firstResolvedAt.toInstant()));
    }

    @Test
    void 해결된_문제를_다시_틀리면_오답_노트로_돌아오고_횟수가_는다() {
        wrongAnswerRepository.recordWrong(key);
        wrongAnswerRepository.resolve(key);

        wrongAnswerRepository.recordWrong(key);

        assertThat(find(key)).contains(new Row(2, null));
    }

    @Test
    void 오답_노트에_없는_문제를_맞히면_아무것도_생기지_않는다() {
        wrongAnswerRepository.resolve(key);

        assertThat(find(key)).isEmpty();
    }

    @Test
    void 다른_사용자의_같은_문제_기록은_건드리지_않는다() {
        long otherUserId = fixture.createUser("other@test.com");
        WrongAnswerKey otherKey = new WrongAnswerKey(otherUserId, key.questionId());
        wrongAnswerRepository.recordWrong(key);
        wrongAnswerRepository.recordWrong(otherKey);

        wrongAnswerRepository.resolve(key);
        wrongAnswerRepository.recordWrong(key);

        assertThat(find(key)).contains(new Row(2, null));
        assertThat(find(otherKey)).contains(new Row(1, null));
    }

    @Test
    void 오답_목록은_문제_내용과_근거_개념_자료와_틀린_횟수를_준다() {
        wrongAnswerRepository.recordWrong(key);
        wrongAnswerRepository.recordWrong(key);

        assertThat(wrongAnswerRepository.findUnresolved(quiz.userId())).singleElement().satisfies(question -> {
            assertThat(question.questionId()).isEqualTo(key.questionId());
            assertThat(question.quizId()).isEqualTo(quiz.quizId());
            assertThat(question.format()).isEqualTo(QuizFormat.OX);
            assertThat(question.content()).isEqualTo("문제 1");
            assertThat(question.cognitiveType()).isEqualTo(CognitiveType.REMEMBER);
            assertThat(question.explanation()).isEqualTo("해설");
            assertThat(question.sourceQuote()).isEqualTo("TCP는 연결 지향 프로토콜이다.");
            assertThat(question.sourcePageNo()).isEqualTo(1);
            assertThat(question.conceptName()).isEqualTo("TCP");
            assertThat(question.materialTitle()).isEqualTo("네트워크 5강");
            assertThat(question.wrongCount()).isEqualTo(2);
            assertThat(question.lastWrongAt()).isNotNull();
        });
    }

    @Test
    void 해결한_오답과_남의_오답은_목록에_없고_처음_틀린_순서로_준다() {
        long first = quiz.questionIds().get(0);
        long second = quiz.questionIds().get(1);
        QuizFixture.Created others = fixture.createQuiz("others@test.com", 1);
        wrongAnswerRepository.recordWrong(new WrongAnswerKey(quiz.userId(), second));
        wrongAnswerRepository.recordWrong(new WrongAnswerKey(quiz.userId(), first));
        wrongAnswerRepository.recordWrong(new WrongAnswerKey(others.userId(), others.questionIds().getFirst()));
        QuizFixture.Created resolvedQuiz = fixture.createQuiz(quiz.userId(), 1);
        WrongAnswerKey resolved = new WrongAnswerKey(quiz.userId(), resolvedQuiz.questionIds().getFirst());
        wrongAnswerRepository.recordWrong(resolved);
        wrongAnswerRepository.resolve(resolved);

        assertThat(wrongAnswerRepository.findUnresolved(quiz.userId()))
                .extracting(WrongAnswerQuestion::questionId)
                .containsExactly(second, first);
    }

    // 약점 퀴즈는 자료 없이 만들지만, 문제의 개념이 나온 자료를 보여 준다
    @Test
    void 자료_없는_약점_퀴즈의_오답도_개념의_자료가_나온다() {
        long weaknessQuizId = quizRepository.save(
                new NewQuiz(quiz.userId(), null, QuizKind.WEAKNESS, QuizFormat.OX, Difficulty.NORMAL));
        long conceptId = jdbcClient.sql("SELECT material_concept_id FROM question WHERE id = :id")
                .param("id", key.questionId()).query(Long.class).single();
        long chunkId = jdbcClient.sql("SELECT source_chunk_id FROM question WHERE id = :id")
                .param("id", key.questionId()).query(Long.class).single();
        long weaknessQuestion = quizRepository.saveQuestion(new NewQuestion(weaknessQuizId, 1, conceptId, chunkId,
                "TCP는 연결 지향 프로토콜이다.", CognitiveType.APPLY, "약점 문제", "해설"));
        wrongAnswerRepository.recordWrong(new WrongAnswerKey(quiz.userId(), weaknessQuestion));

        assertThat(wrongAnswerRepository.findUnresolved(quiz.userId()))
                .singleElement()
                .satisfies(question -> {
                    assertThat(question.quizId()).isEqualTo(weaknessQuizId);
                    assertThat(question.materialTitle()).isEqualTo("네트워크 5강");
                });
    }

    // 퀴즈 제출과 오답 노트 복습이 같은 규칙으로 오답 노트를 바꾼다
    @Test
    void 채점_결과를_반영하면_맞힌_문제는_해결되고_틀린_문제는_횟수가_는다() {
        long first = quiz.questionIds().get(0);
        long second = quiz.questionIds().get(1);
        wrongAnswerRepository.recordWrong(new WrongAnswerKey(quiz.userId(), first));
        wrongAnswerRepository.recordWrong(new WrongAnswerKey(quiz.userId(), second));

        wrongAnswerRepository.apply(quiz.userId(), List.of(
                new QuestionResult(first, null, true),
                new QuestionResult(second, null, false)));

        assertThat(find(new WrongAnswerKey(quiz.userId(), first))).get().extracting(Row::resolvedAt).isNotNull();
        assertThat(find(new WrongAnswerKey(quiz.userId(), second))).contains(new Row(2, null));
    }
}
