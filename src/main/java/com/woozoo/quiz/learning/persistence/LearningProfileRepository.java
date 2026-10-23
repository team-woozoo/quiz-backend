package com.woozoo.quiz.learning.persistence;

import com.woozoo.quiz.learning.domain.CognitiveTypeStat;
import com.woozoo.quiz.learning.domain.ConceptStat;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class LearningProfileRepository {

    private final JdbcClient jdbcClient;

    public LearningProfileRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<CognitiveTypeStat> countByCognitiveType(long userId) {
        return jdbcClient.sql(LatestAnswers.WITH_LATEST + """
                SELECT q.cognitive_type, COUNT(*) AS answered_count,
                COUNT(*) FILTER (WHERE l.is_correct) AS correct_answer_count
                FROM latest l
                JOIN question q ON q.id = l.question_id
                GROUP BY q.cognitive_type
                """)
                .param("userId", userId)
                .query(CognitiveTypeStat.class)
                .list();
    }

    public List<ConceptStat> findWeakConcepts(long userId, int minAnswered, int limit) {
        return jdbcClient.sql(LatestAnswers.WITH_LATEST + """
                SELECT mc.id AS concept_id, mc.name AS concept_name, m.id AS material_id, m.title AS material_title,
                COUNT(*) AS answered_count, COUNT(*) FILTER (WHERE l.is_correct) AS correct_answer_count
                FROM latest l
                JOIN question q ON l.question_id = q.id
                JOIN material_concept mc ON q.material_concept_id = mc.id
                JOIN material m ON mc.material_id = m.id
                GROUP BY mc.id, mc.name, m.id, m.title
                HAVING COUNT(*) >= :minAnswered
                ORDER BY COUNT(*) FILTER (WHERE l.is_correct)::float / COUNT(*), COUNT(*) DESC, mc.id
                LIMIT :limit
                """)
                .param("userId", userId)
                .param("minAnswered", minAnswered)
                .param("limit", limit)
                .query(ConceptStat.class)
                .list();
    }
}
