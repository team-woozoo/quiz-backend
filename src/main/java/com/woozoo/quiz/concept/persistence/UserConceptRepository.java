package com.woozoo.quiz.concept.persistence;

import com.woozoo.quiz.concept.domain.ConceptPage;
import com.woozoo.quiz.concept.domain.UserConcept;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class UserConceptRepository {

    private final JdbcClient jdbcClient;

    public UserConceptRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<Long> findMappedUserConceptId(long materialConceptId) {
        return jdbcClient.sql("""
                        SELECT user_concept_id FROM concept_mapping WHERE material_concept_id = :materialConceptId
                        """)
                .param("materialConceptId", materialConceptId)
                .query(Long.class)
                .optional();
    }

    // 자료 개념의 이름과 설명으로 대표 개념을 새로 만들고 둘을 잇는다. 대표 개념 이름은 이후 바꾸지 않는다
    public long createMappedUserConcept(long materialConceptId, long userId) {
        long userConceptId = jdbcClient.sql("""
                        INSERT INTO user_concept (user_id, name, description)
                        SELECT :userId, name, description FROM material_concept WHERE id = :materialConceptId
                        RETURNING id
                        """)
                .param("userId", userId)
                .param("materialConceptId", materialConceptId)
                .query(Long.class)
                .single();
        jdbcClient.sql("""
                        INSERT INTO concept_mapping (material_concept_id, user_concept_id)
                        VALUES (:materialConceptId, :userConceptId)
                        """)
                .param("materialConceptId", materialConceptId)
                .param("userConceptId", userConceptId)
                .update();
        return userConceptId;
    }

    public Optional<UserConcept> findById(long userConceptId) {
        return jdbcClient.sql("""
                        SELECT id, user_id, name, description FROM user_concept WHERE id = :userConceptId
                        """)
                .param("userConceptId", userConceptId)
                .query(UserConcept.class)
                .optional();
    }

    // 대표 개념에 묶인 모든 자료 개념의 페이지를 자료와 페이지 순서로 준다
    public List<ConceptPage> findPages(long userConceptId) {
        return jdbcClient.sql("""
                        SELECT c.id AS chunk_id, c.content, cc.material_concept_id
                        FROM concept_mapping cm
                        JOIN concept_chunk cc ON cc.material_concept_id = cm.material_concept_id
                        JOIN material_chunk c ON c.id = cc.material_chunk_id
                        WHERE cm.user_concept_id = :userConceptId
                        ORDER BY c.material_id, c.seq
                        """)
                .param("userConceptId", userConceptId)
                .query(ConceptPage.class)
                .list();
    }
}
