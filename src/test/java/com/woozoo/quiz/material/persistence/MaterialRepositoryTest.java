package com.woozoo.quiz.material.persistence;

import com.woozoo.quiz.TestcontainersConfiguration;
import com.woozoo.quiz.material.domain.FileType;
import com.woozoo.quiz.material.domain.MaterialChunk;
import com.woozoo.quiz.material.domain.NewMaterial;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class MaterialRepositoryTest {

    @Autowired
    MaterialRepository materialRepository;
    @Autowired
    JdbcClient jdbcClient;

    private long ownerId;

    @BeforeEach
    void setUp() {
        ownerId = jdbcClient.sql("""
                INSERT INTO users (email, password_hash, nickname)
                VALUES ('test@example.com', 'hash', 'tester')
                RETURNING id
                """)
                .query(Long.class)
                .single();
    }

    @Test
    void 자료를_저장하고_그_아이디로_조회한다(){
        NewMaterial newMaterial =
                new NewMaterial(ownerId, "운영체제 5강", "05.pdf", FileType.PDF);

        long id = materialRepository.save(newMaterial);

        NewMaterial result = jdbcClient.sql("""
                SELECT owner_id, title, original_filename, file_type
                FROM material
                WHERE id = :id
                """)
                .param("id", id)
                .query(NewMaterial.class)
                .single();

        assertThat(result).isEqualTo(newMaterial);
    }

    @Test
    void 청크가_순서대로_저장된다(){
        long materialId = materialRepository.save(
                new NewMaterial(ownerId, "운영체제 5강", "05.pdf", FileType.PDF));

        List<MaterialChunk> chunks = List.of(
                new MaterialChunk(1,1,"first page"),
                new MaterialChunk(2,3,"third page")
        );

        materialRepository.saveChunks(materialId, chunks);

        List<MaterialChunk> result = jdbcClient.sql("""
                SELECT seq, page_no, content
                FROM material_chunk
                WHERE material_id = :materialId
                ORDER BY seq
                """)
                .param("materialId", materialId)
                .query(MaterialChunk.class)
                .list();

        assertThat(result).isEqualTo(chunks);
    }
}
