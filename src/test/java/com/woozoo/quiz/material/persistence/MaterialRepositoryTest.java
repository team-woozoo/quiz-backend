package com.woozoo.quiz.material.persistence;

import com.woozoo.quiz.TestcontainersConfiguration;
import com.woozoo.quiz.material.domain.FileType;
import com.woozoo.quiz.material.domain.MaterialChunk;
import com.woozoo.quiz.material.domain.MaterialSummary;
import com.woozoo.quiz.material.domain.NewMaterial;
import com.woozoo.quiz.material.domain.StoredChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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
                new NewMaterial(ownerId, "운영체제 5강", "05.pdf", FileType.PDF, 30);

        long id = materialRepository.save(newMaterial);

        NewMaterial result = jdbcClient.sql("""
                SELECT owner_id, title, original_filename, file_type, total_page_count
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
                new NewMaterial(ownerId, "운영체제 5강", "05.pdf", FileType.PDF, 30));

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

    @Test
    void 자료의_청크를_아이디와_함께_seq_순서로_조회한다() {
        long materialId = materialRepository.save(
                new NewMaterial(ownerId, "운영체제 5강", "05.pdf", FileType.PDF, 30));
        long otherMaterialId = materialRepository.save(
                new NewMaterial(ownerId, "운영체제 6강", "06.pdf", FileType.PDF, 30));

        // 저장 순서와 seq 순서를 다르게 넣어 ORDER BY 를 확인한다
        materialRepository.saveChunks(materialId, List.of(
                new MaterialChunk(2, 2, "second page"),
                new MaterialChunk(1, 1, "first page")));
        materialRepository.saveChunks(otherMaterialId, List.of(
                new MaterialChunk(1, 1, "other material")));

        List<StoredChunk> result = materialRepository.findChunks(materialId);

        assertThat(result).extracting(StoredChunk::seq, StoredChunk::content)
                .containsExactly(tuple(1, "first page"), tuple(2, "second page"));
        assertThat(result).extracting(StoredChunk::id).doesNotHaveDuplicates();
    }

    @Test
    void 주인일_때만_자료가_있다고_답한다() {
        long materialId = materialRepository.save(
                new NewMaterial(ownerId, "운영체제 5강", "05.pdf", FileType.PDF, 30));

        assertThat(materialRepository.existsByIdAndOwnerId(materialId, ownerId)).isTrue();
        assertThat(materialRepository.existsByIdAndOwnerId(materialId, ownerId + 1)).isFalse();
        assertThat(materialRepository.existsByIdAndOwnerId(-1L, ownerId)).isFalse();
    }

    @Test
    void 내_자료를_최근_것부터_페이지_수와_함께_조회한다() {
        long older = materialRepository.save(new NewMaterial(ownerId, "운영체제 5강", "05.pdf", FileType.PDF, 30));
        long newer = materialRepository.save(new NewMaterial(ownerId, "운영체제 6강", "06.pdf", FileType.PDF, 30));
        materialRepository.saveChunks(older, List.of(
                new MaterialChunk(1, 1, "first page"),
                new MaterialChunk(2, 2, "second page")));
        long otherOwnerId = jdbcClient.sql("""
                INSERT INTO users (email, password_hash, nickname)
                VALUES ('other@example.com', 'hash', 'other')
                RETURNING id
                """)
                .query(Long.class)
                .single();
        materialRepository.save(new NewMaterial(otherOwnerId, "남의 자료", "other.pdf", FileType.PDF, 30));

        List<MaterialSummary> result = materialRepository.findSummariesByOwnerId(ownerId);

        assertThat(result).extracting(MaterialSummary::id, MaterialSummary::title, MaterialSummary::pageCount)
                .containsExactly(tuple(newer, "운영체제 6강", 0), tuple(older, "운영체제 5강", 2));
        assertThat(result).allSatisfy(summary -> assertThat(summary.createdAt()).isNotNull());
    }

    @Test
    void 청크가_없는_자료는_빈_목록을_돌려준다() {
        long materialId = materialRepository.save(
                new NewMaterial(ownerId, "운영체제 5강", "05.pdf", FileType.PDF, 30));

        assertThat(materialRepository.findChunks(materialId)).isEmpty();
    }
}
