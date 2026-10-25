package com.woozoo.quiz.learning.persistence;

import com.woozoo.quiz.learning.domain.QuestionResult;
import com.woozoo.quiz.learning.domain.WrongAnswerKey;
import com.woozoo.quiz.learning.domain.WrongAnswerQuestion;
import com.woozoo.quiz.quiz.domain.CognitiveType;
import com.woozoo.quiz.quiz.domain.QuizFormat;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
public class WrongAnswerRepository {

    private final JdbcClient jdbcClient;

    public WrongAnswerRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    // 해결했던 문제를 다시 틀리면 오답 노트로 돌아오게 resolved_at 을 비운다
    public void recordWrong(WrongAnswerKey key) {
        jdbcClient.sql("""
                        INSERT INTO wrong_answer(user_id, question_id)
                        VALUES (:userId, :questionId)
                        ON CONFLICT (user_id, question_id)
                        DO UPDATE SET wrong_count = wrong_answer.wrong_count + 1,
                        last_wrong_at = now(),
                        resolved_at   = NULL
                        """)
                .param("userId", key.userId())
                .param("questionId", key.questionId())
                .update();
    }

    // 이미 해결된 행은 건드리지 않아 처음 해결한 시각이 남는다
    public void resolve(WrongAnswerKey key) {
        jdbcClient.sql("""
                        UPDATE wrong_answer
                        SET resolved_at = now()
                        WHERE user_id = :userId
                        AND question_id = :questionId
                        AND resolved_at IS NULL
                        """)
                .param("userId", key.userId())
                .param("questionId", key.questionId())
                .update();
    }

    // 채점 결과를 오답 노트에 반영한다. 퀴즈 제출과 오답 노트 복습이 같은 규칙을 쓴다
    public void apply(long userId, List<QuestionResult> results) {
        for (QuestionResult result : results) {
            WrongAnswerKey key = new WrongAnswerKey(userId, result.questionId());
            if (result.correct()) {
                resolve(key);
            } else {
                recordWrong(key);
            }
        }
    }

    // 해결하지 않은 오답을 처음 틀린 순서로 준다
    public List<WrongAnswerQuestion> findUnresolved(long userId) {
        return jdbcClient.sql("""
                        SELECT q.id AS question_id, q.quiz_id, qz.format, q.content, q.cognitive_type, q.explanation,
                               q.source_quote, c.page_no AS source_page_no, m.total_page_count, mc.name AS concept_name,
                               m.id AS material_id, m.title AS material_title, wa.wrong_count, wa.last_wrong_at
                        FROM wrong_answer wa
                        JOIN question q ON q.id = wa.question_id
                        JOIN quiz qz ON qz.id = q.quiz_id
                        JOIN material_chunk c ON c.id = q.source_chunk_id
                        JOIN material_concept mc ON mc.id = q.material_concept_id
                        JOIN material m ON m.id = mc.material_id
                        WHERE wa.user_id = :userId AND wa.resolved_at IS NULL
                        ORDER BY wa.id
                        """)
                .param("userId", userId)
                .query((rs, rowNum) -> toWrongAnswerQuestion(rs))
                .list();
    }

    // Postgres 드라이버는 timestamptz 를 Instant 로 바로 주지 않아 OffsetDateTime 으로 받아 바꾼다
    private static WrongAnswerQuestion toWrongAnswerQuestion(ResultSet rs) throws SQLException {
        return new WrongAnswerQuestion(
                rs.getLong("question_id"),
                rs.getLong("quiz_id"),
                QuizFormat.valueOf(rs.getString("format")),
                rs.getString("content"),
                CognitiveType.valueOf(rs.getString("cognitive_type")),
                rs.getString("explanation"),
                rs.getString("source_quote"),
                rs.getObject("source_page_no", Integer.class),
                rs.getInt("total_page_count"),
                rs.getString("concept_name"),
                rs.getLong("material_id"),
                rs.getString("material_title"),
                rs.getInt("wrong_count"),
                rs.getObject("last_wrong_at", OffsetDateTime.class).toInstant());
    }
}
