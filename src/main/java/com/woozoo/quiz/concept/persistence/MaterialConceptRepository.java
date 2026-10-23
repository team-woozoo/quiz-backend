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

    // AI 가 기존 개념 이름을 그대로 쓰는 것이 의도한 동작이라, 이름이 있으면 새로 만들지 않고 그 id 를 돌려준다.
    // DO NOTHING 은 충돌한 행을 RETURNING 하지 않아서, 이름을 같은 값으로 덮는 UPDATE 로 기존 id 를 받는다. 설명은 처음 것을 둔다
    public long findOrCreate(long materialId, String name, String description) {
        return jdbcClient.sql("""
                        INSERT INTO material_concept (material_id, name, description)
                        VALUES (:materialId, :name, :description)
                        ON CONFLICT (material_id, name) DO UPDATE SET name = EXCLUDED.name
                        RETURNING id
                        """)
                .param("materialId", materialId)
                .param("name", name)
                .param("description", description)
                .query(Long.class)
                .single();
    }

    // 내 자료의 개념이면 그 행을 잠그고 true 를 준다. 같은 개념에 대표 개념을 두 번 만들지 않도록, 확인과 생성을 한 트랜잭션에서 줄 세운다
    public boolean lockOwnedBy(long materialConceptId, long userId) {
        return jdbcClient.sql("""
                        SELECT mc.id
                        FROM material_concept mc
                        JOIN material m ON m.id = mc.material_id
                        WHERE mc.id = :materialConceptId AND m.owner_id = :userId
                        FOR UPDATE OF mc
                        """)
                .param("materialConceptId", materialConceptId)
                .param("userId", userId)
                .query(Long.class)
                .optional()
                .isPresent();
    }

    // 기존 개념을 다시 쓰면 이미 이어진 청크가 또 올 수 있어 겹치면 넘어간다
    public void linkChunk(long materialConceptId, long materialChunkId) {
        jdbcClient.sql("""
                        INSERT INTO concept_chunk (material_concept_id, material_chunk_id)
                        VALUES (:materialConceptId, :materialChunkId)
                        ON CONFLICT DO NOTHING
                        """)
                .param("materialConceptId", materialConceptId)
                .param("materialChunkId", materialChunkId)
                .update();
    }
}
