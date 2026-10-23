package com.woozoo.quiz.material.persistence;

import com.woozoo.quiz.material.domain.MaterialChunk;
import com.woozoo.quiz.material.domain.MaterialSummary;
import com.woozoo.quiz.material.domain.NewMaterial;
import com.woozoo.quiz.material.domain.StoredChunk;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class MaterialRepository {

    // 생성 중인 작업이 있으면 앱이 목록에 "문제 만드는 중" 을 보여 준다
    private static final String SUMMARY_SELECT = """
            SELECT m.id, m.title, m.original_filename, m.created_at,
                   (SELECT count(*) FROM material_chunk c WHERE c.material_id = m.id) AS page_count,
                   EXISTS (SELECT 1 FROM generation_job j
                           WHERE j.material_id = m.id AND j.status IN ('PENDING', 'PROCESSING')) AS generating
            FROM material m
            """;

    private final JdbcClient jdbcClient;

    public MaterialRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public long save(NewMaterial newMaterial) {
        return jdbcClient.sql("""
                        INSERT INTO material (owner_id, title, original_filename, file_type)
                        VALUES (:ownerId, :title, :originalFilename, :fileType)
                        RETURNING id
                        """)
                .param("ownerId", newMaterial.ownerId())
                .param("title", newMaterial.title())
                .param("originalFilename", newMaterial.originalFilename())
                .param("fileType", newMaterial.fileType().name())
                .query(Long.class)
                .single();
    }

    public boolean existsByIdAndOwnerId(long materialId, long ownerId) {
        return jdbcClient.sql("""
                        SELECT EXISTS (SELECT 1 FROM material WHERE id = :materialId AND owner_id = :ownerId)
                        """)
                .param("materialId", materialId)
                .param("ownerId", ownerId)
                .query(Boolean.class)
                .single();
    }

    // TODO: 청크가 많아져 저장이 느려지면 NamedParameterJdbcTemplate.batchUpdate 로 바꾼다 (JdbcClient 에는 배치가 없다)
    public void saveChunks(long materialId, List<MaterialChunk> chunks) {
        for (MaterialChunk chunk : chunks) {
            jdbcClient.sql("""
                            INSERT INTO material_chunk (material_id, seq, page_no, content)
                            VALUES (:materialId, :seq, :pageNo, :content)
                            """)
                    .param("materialId", materialId)
                    .param("seq", chunk.seq())
                    .param("pageNo", chunk.pageNo())
                    .param("content", chunk.content())
                    .update();
        }
    }

    // 최근에 올린 자료가 먼저 오게 한다
    public List<MaterialSummary> findSummariesByOwnerId(long ownerId) {
        return jdbcClient.sql(SUMMARY_SELECT + """
                        WHERE m.owner_id = :ownerId
                        ORDER BY m.id DESC
                        """)
                .param("ownerId", ownerId)
                .query((rs, rowNum) -> toSummary(rs))
                .list();
    }

    // 남의 자료면 비어 있다
    public Optional<MaterialSummary> findSummary(long materialId, long ownerId) {
        return jdbcClient.sql(SUMMARY_SELECT + """
                        WHERE m.id = :materialId AND m.owner_id = :ownerId
                        """)
                .param("materialId", materialId)
                .param("ownerId", ownerId)
                .query((rs, rowNum) -> toSummary(rs))
                .optional();
    }

    // Postgres 드라이버는 timestamptz 를 Instant 로 바로 주지 않아 OffsetDateTime 으로 받아 바꾼다
    private static MaterialSummary toSummary(ResultSet rs) throws SQLException {
        return new MaterialSummary(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("original_filename"),
                rs.getInt("page_count"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getBoolean("generating"));
    }

    public List<StoredChunk> findChunks(long materialId) {
        return jdbcClient.sql("""
                        SELECT id, seq, content
                        FROM material_chunk
                        WHERE material_id = :materialId
                        ORDER BY seq
                        """)
                .param("materialId", materialId)
                .query(StoredChunk.class)
                .list();
    }
}
