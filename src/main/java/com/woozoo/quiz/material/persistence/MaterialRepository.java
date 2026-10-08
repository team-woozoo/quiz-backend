package com.woozoo.quiz.material.persistence;

import com.woozoo.quiz.material.domain.MaterialChunk;
import com.woozoo.quiz.material.domain.NewMaterial;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MaterialRepository {

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
}
