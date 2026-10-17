package com.woozoo.quiz.learning.persistence;

import com.woozoo.quiz.learning.domain.WrongAnswerKey;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

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
}
