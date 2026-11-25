package com.woozoo.quiz.concept.persistence;

import com.woozoo.quiz.concept.domain.ConceptTag;
import com.woozoo.quiz.concept.domain.MaterialConcept;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

@Repository
public class MaterialConceptRepository {

    private final JdbcClient jdbcClient;

    public MaterialConceptRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    // 생성기에 보낼 개념을 저장 순서(자료 순서)대로 준다. 워커가 이 순서로 번호를 매긴다.
    // 청크와 문제는 개념마다 여럿이라 조인하면 서로 곱해져, 각자 서브쿼리로 구한다
    public List<MaterialConcept> findForGeneration(long materialId) {
        return jdbcClient.sql("""
                        SELECT mc.id, mc.name, mc.description, mc.tags,
                        ARRAY(SELECT c.seq
                        FROM concept_chunk cc
                        JOIN material_chunk c ON c.id = cc.material_chunk_id
                        WHERE cc.material_concept_id = mc.id
                        ORDER BY c.seq) AS chunk_seqs,
                        (SELECT COUNT(*) FROM question q WHERE q.material_concept_id = mc.id) AS question_count
                        FROM material_concept mc
                        WHERE mc.material_id = :materialId
                        ORDER BY mc.id
                        """)
                .param("materialId", materialId)
                .query((rs, rowNum) -> toMaterialConcept(rs))
                .list();
    }

    // 배열 컬럼은 레코드로 바로 옮겨지지 않아 직접 꺼낸다. 분석 전에 생긴 개념은 태그가 NULL 이라 빈 목록으로 본다
    private static MaterialConcept toMaterialConcept(ResultSet rs) throws SQLException {
        Array tagArray = rs.getArray("tags");
        List<ConceptTag> tags = tagArray == null ? List.of()
                : Arrays.stream((String[]) tagArray.getArray()).map(ConceptTag::valueOf).toList();
        Integer[] chunkSeqs = (Integer[]) rs.getArray("chunk_seqs").getArray();
        return new MaterialConcept(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("description"),
                tags,
                List.of(chunkSeqs),
                rs.getInt("question_count"));
    }

    // 업로드 분석으로 나온 개념을 넣는다. 개념은 여기서만 생긴다. 이름은 검증기가 이미 겹치지 않게 정리했다
    public long save(long materialId, String name, String description, List<ConceptTag> tags) {
        return jdbcClient.sql("""
                        INSERT INTO material_concept (material_id, name, description, tags)
                        VALUES (:materialId, :name, :description, :tags)
                        RETURNING id
                        """)
                .param("materialId", materialId)
                .param("name", name)
                .param("description", description)
                .param("tags", tags.stream().map(Enum::name).toArray(String[]::new))
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

    // 업로드 분석으로 나온 개념을 페이지에 잇는다. 검증기가 같은 청크 번호를 이미 빼서 겹치지 않지만,
    // 겹쳐도 같은 연결이라 오류 대신 넘어간다
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
