package com.woozoo.quiz.quiz.persistence;

import com.woozoo.quiz.quiz.domain.NewQuestion;
import com.woozoo.quiz.quiz.domain.NewQuiz;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class QuizRepository {

    private final JdbcClient jdbcClient;

    public QuizRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public long save(NewQuiz newQuiz) {
        return jdbcClient.sql("""
                        INSERT INTO quiz (user_id, material_id, kind, format, difficulty)
                        VALUES (:userId, :materialId, :kind, :format, :difficulty)
                        RETURNING id
                        """)
                .param("userId", newQuiz.userId())
                .param("materialId", newQuiz.materialId())
                .param("kind", newQuiz.kind().name())
                .param("format", newQuiz.format().name())
                .param("difficulty", newQuiz.difficulty().name())
                .query(Long.class)
                .single();
    }

    public long saveQuestion(NewQuestion newQuestion) {
        return jdbcClient.sql("""
                        INSERT INTO question (quiz_id, order_no, material_concept_id, source_chunk_id,
                                              source_quote, cognitive_type, content, explanation)
                        VALUES (:quizId, :orderNo, :materialConceptId, :sourceChunkId,
                                :sourceQuote, :cognitiveType, :content, :explanation)
                        RETURNING id
                        """)
                .param("quizId", newQuestion.quizId())
                .param("orderNo", newQuestion.orderNo())
                .param("materialConceptId", newQuestion.materialConceptId())
                .param("sourceChunkId", newQuestion.sourceChunkId())
                .param("sourceQuote", newQuestion.sourceQuote())
                .param("cognitiveType", newQuestion.cognitiveType().name())
                .param("content", newQuestion.content())
                .param("explanation", newQuestion.explanation())
                .query(Long.class)
                .single();
    }

    public void saveOption(long questionId, int orderNo, String content, boolean isCorrect) {
        jdbcClient.sql("""
                        INSERT INTO question_option (question_id, order_no, content, is_correct)
                        VALUES (:questionId, :orderNo, :content, :isCorrect)
                        """)
                .param("questionId", questionId)
                .param("orderNo", orderNo)
                .param("content", content)
                .param("isCorrect", isCorrect)
                .update();
    }
}
