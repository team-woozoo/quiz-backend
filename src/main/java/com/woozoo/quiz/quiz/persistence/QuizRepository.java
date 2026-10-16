package com.woozoo.quiz.quiz.persistence;

import com.woozoo.quiz.quiz.domain.NewQuestion;
import com.woozoo.quiz.quiz.domain.NewQuiz;
import com.woozoo.quiz.quiz.domain.StoredOption;
import com.woozoo.quiz.quiz.domain.StoredQuestion;
import com.woozoo.quiz.quiz.domain.StoredQuiz;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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

    public Optional<StoredQuiz> findById(long quizId) {
        return jdbcClient.sql("""
                        SELECT id, user_id, material_id, kind, format, difficulty
                        FROM quiz
                        WHERE id = :quizId
                        """)
                .param("quizId", quizId)
                .query(StoredQuiz.class)
                .optional();
    }

    public List<StoredQuestion> findQuestions(long quizId) {
        return jdbcClient.sql("""
                        SELECT q.id, q.order_no, q.content, q.cognitive_type, q.explanation, q.source_quote,
                               c.page_no AS source_page_no, mc.name AS concept_name
                        FROM question q
                        JOIN material_chunk c ON c.id = q.source_chunk_id
                        JOIN material_concept mc ON mc.id = q.material_concept_id
                        WHERE q.quiz_id = :quizId
                        ORDER BY q.order_no
                        """)
                .param("quizId", quizId)
                .query(StoredQuestion.class)
                .list();
    }

    // 문제마다 따로 조회하지 않고 퀴즈의 보기를 한 번에 가져온다
    public List<StoredOption> findOptions(long quizId) {
        return jdbcClient.sql("""
                        SELECT o.id, o.question_id, o.order_no, o.content, o.is_correct
                        FROM question_option o
                        JOIN question q ON q.id = o.question_id
                        WHERE q.quiz_id = :quizId
                        ORDER BY q.order_no, o.order_no
                        """)
                .param("quizId", quizId)
                .query(StoredOption.class)
                .list();
    }
}
