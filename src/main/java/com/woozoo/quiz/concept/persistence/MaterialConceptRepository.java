package com.woozoo.quiz.concept.persistence;

import com.woozoo.quiz.concept.domain.MaterialConcept;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MaterialConceptRepository {

    private final JdbcClient jdbcClient;

    public MaterialConceptRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    // 같은 자료로 다시 생성할 때 AI 에게 넘겨, 이미 있는 개념은 같은 이름으로 쓰게 한다
    public List<MaterialConcept> findByMaterialId(long materialId) {
        return jdbcClient.sql("""
                        SELECT id, name, description
                        FROM material_concept
                        WHERE material_id = :materialId
                        ORDER BY id
                        """)
                .param("materialId", materialId)
                .query(MaterialConcept.class)
                .list();
    }
}
