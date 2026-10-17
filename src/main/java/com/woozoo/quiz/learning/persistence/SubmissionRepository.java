package com.woozoo.quiz.learning.persistence;

import com.woozoo.quiz.learning.domain.NewSubmission;
import com.woozoo.quiz.learning.domain.QuestionResult;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

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
}
