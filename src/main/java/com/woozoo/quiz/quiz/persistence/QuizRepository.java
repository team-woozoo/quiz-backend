package com.woozoo.quiz.quiz.persistence;

import com.woozoo.quiz.quiz.domain.NewQuestion;
import com.woozoo.quiz.quiz.domain.NewQuiz;
import com.woozoo.quiz.quiz.domain.StoredOption;
import com.woozoo.quiz.quiz.domain.StoredQuestion;
import com.woozoo.quiz.quiz.domain.StoredQuiz;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;
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

    // 약점 퀴즈가 어느 대표 개념을 보완하려고 만든 것인지 남긴다. 나중에 약점이 나아졌는지 볼 때 쓴다
    public void saveWeaknessTarget(long quizId, long userConceptId) {
        jdbcClient.sql("""
                        INSERT INTO weakness_target (quiz_id, user_concept_id)
                        VALUES (:quizId, :userConceptId)
                        """)
                .param("quizId", quizId)
                .param("userConceptId", userConceptId)
                .update();
    }

    // 내가 만든 퀴즈에서 이 개념들로 낸 문제 문장을 최근 것부터 준다
    public List<String> findRecentQuestionContents(long userId, List<Long> materialConceptIds, int limit) {
        if (materialConceptIds.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql("""
                        SELECT q.content
                        FROM question q
                        JOIN quiz qz ON qz.id = q.quiz_id
                        WHERE qz.user_id = :userId AND q.material_concept_id IN (:materialConceptIds)
                        ORDER BY q.id DESC
                        LIMIT :limit
                        """)
                .param("userId", userId)
                .param("materialConceptIds", materialConceptIds)
                .param("limit", limit)
                .query(String.class)
                .list();
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

    // 여러 퀴즈에 흩어진 문제의 보기를 한 번에 가져온다. 내 퀴즈의 문제만 주어서, 남의 문제는 보기가 없는 것처럼 보인다
    public List<StoredOption> findOptionsOfMyQuestions(long userId, Collection<Long> questionIds) {
        if (questionIds.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql("""
                        SELECT o.id, o.question_id, o.order_no, o.content, o.is_correct
                        FROM question_option o
                        JOIN question q ON q.id = o.question_id
                        JOIN quiz qz ON qz.id = q.quiz_id
                        WHERE qz.user_id = :userId AND o.question_id IN (:questionIds)
                        ORDER BY o.question_id, o.order_no
                        """)
                .param("userId", userId)
                .param("questionIds", questionIds)
                .query(StoredOption.class)
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
