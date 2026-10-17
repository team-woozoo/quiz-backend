package com.woozoo.quiz.learning.persistence;

import com.woozoo.quiz.learning.domain.NewSubmission;
import com.woozoo.quiz.learning.domain.QuestionResult;
import com.woozoo.quiz.learning.domain.QuizSummary;
import com.woozoo.quiz.quiz.domain.Difficulty;
import com.woozoo.quiz.quiz.domain.QuizFormat;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
public class SubmissionRepository {

    private final JdbcClient jdbcClient;

    public SubmissionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public long save(NewSubmission newSubmission) {
        return jdbcClient.sql("""
                INSERT INTO submission (user_id, quiz_id)
                VALUES (:userId, :quizId)
                RETURNING id
                """)
                .param("userId", newSubmission.userId())
                .param("quizId", newSubmission.quizId())
                .query(Long.class)
                .single();
    }

    public void saveAnswers(long submissionId, List<QuestionResult> results) {
        for(QuestionResult questionResult : results) {
            jdbcClient.sql("""
                    INSERT INTO submission_answer (submission_id, question_id, selected_option_id, is_correct)
                    VALUES (:submissionId, :questionId, :selectedOptionId, :isCorrect)
                    """)
                    .param("submissionId", submissionId)
                    .param("questionId", questionResult.questionId())
                    .param("selectedOptionId", questionResult.selectedOptionId())
                    .param("isCorrect", questionResult.correct())
                    .update();
        }
    }

    // 퀴즈마다 따로 조회하지 않고 한 번에 가져온다. LATERAL 로 퀴즈마다 가장 최근 제출 하나를 붙이고,
    // 안 푼 퀴즈는 그 제출이 없어 null 이 된다. 자료 없이 만든 퀴즈도 빠지지 않게 자료는 LEFT JOIN 한다.
    // 같은 순간에 제출이 둘이면(같은 트랜잭션 등) id 가 큰 것을 최근으로 본다
    public List<QuizSummary> findQuizSummaries(long userId) {
        return jdbcClient.sql("""
                        SELECT q.id, m.title AS material_title, q.format, q.difficulty, q.created_at,
                               (SELECT count(*) FROM question qu WHERE qu.quiz_id = q.id) AS question_count,
                               (SELECT count(*) FROM submission s WHERE s.quiz_id = q.id) AS submission_count,
                               last.submitted_at AS last_submitted_at,
                               (SELECT count(*) FROM submission_answer sa
                                WHERE sa.submission_id = last.id AND sa.is_correct) AS last_correct_count
                        FROM quiz q
                        LEFT JOIN material m ON m.id = q.material_id
                        LEFT JOIN LATERAL (
                            SELECT s.id, s.submitted_at
                            FROM submission s
                            WHERE s.quiz_id = q.id
                            ORDER BY s.submitted_at DESC, s.id DESC
                            LIMIT 1
                        ) last ON true
                        WHERE q.user_id = :userId
                        ORDER BY q.id DESC
                        """)
                .param("userId", userId)
                .query((rs, rowNum) -> toQuizSummary(rs))
                .list();
    }

    // Postgres 드라이버는 timestamptz 를 Instant 로 바로 주지 않아 OffsetDateTime 으로 받아 바꾼다
    private static QuizSummary toQuizSummary(ResultSet rs) throws SQLException {
        OffsetDateTime lastSubmittedAt = rs.getObject("last_submitted_at", OffsetDateTime.class);
        QuizSummary.LastSubmission lastSubmission = lastSubmittedAt == null
                ? null
                : new QuizSummary.LastSubmission(rs.getInt("last_correct_count"), lastSubmittedAt.toInstant());

        return new QuizSummary(
                rs.getLong("id"),
                rs.getString("material_title"),
                QuizFormat.valueOf(rs.getString("format")),
                Difficulty.valueOf(rs.getString("difficulty")),
                rs.getInt("question_count"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getInt("submission_count"),
                lastSubmission);
    }
}
