package com.woozoo.quiz.material.application;

import com.woozoo.quiz.TestcontainersConfiguration;
import com.woozoo.quiz.global.error.BusinessException;
import com.woozoo.quiz.global.error.ErrorCode;
import com.woozoo.quiz.material.domain.FileType;
import com.woozoo.quiz.material.domain.MaterialChunk;
import com.woozoo.quiz.material.domain.NewMaterial;
import com.woozoo.quiz.material.port.ExtractedPage;
import com.woozoo.quiz.material.port.TextExtractionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class MaterialServiceTest {

    @Autowired
    MaterialWriter materialWriter;
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

    // 추출 포트는 가짜로 바꿔 PDF 없이 추출 결과를 정하고, 저장은 진짜 DB 에 한다
    private MaterialService serviceExtracting(List<ExtractedPage> pages) {
        return new MaterialService((content, fileType) -> pages, materialWriter);
    }

    private UploadMaterialCommand command() {
        return new UploadMaterialCommand(new byte[0], ownerId, "운영체제 5강", "05.pdf", FileType.PDF);
    }

    @Test
    void 업로드하면_자료와_청크를_저장한다() {
        MaterialService materialService = serviceExtracting(List.of(
                new ExtractedPage(1, "first page"),
                new ExtractedPage(3, "third page")));

        long materialId = materialService.upload(command());

        NewMaterial material = jdbcClient.sql("""
                SELECT owner_id, title, original_filename, file_type
                FROM material
                WHERE id = :id
                """)
                .param("id", materialId)
                .query(NewMaterial.class)
                .single();
        assertThat(material).isEqualTo(new NewMaterial(ownerId, "운영체제 5강", "05.pdf", FileType.PDF));

        List<MaterialChunk> chunks = jdbcClient.sql("""
                SELECT seq, page_no, content
                FROM material_chunk
                WHERE material_id = :materialId
                ORDER BY seq
                """)
                .param("materialId", materialId)
                .query(MaterialChunk.class)
                .list();
        assertThat(chunks).containsExactly(
                new MaterialChunk(1, 1, "first page"),
                new MaterialChunk(2, 3, "third page"));
    }

    @Test
    void 추출된_텍스트가_없으면_예외를_던지고_아무것도_저장하지_않는다() {
        MaterialService materialService = serviceExtracting(List.of());

        assertThatThrownBy(() -> materialService.upload(command()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EXTRACTION_FAILED);

        assertThat(materialCount()).isZero();
    }

    @Test
    void PDF를_읽지_못하면_원인을_담아_추출_실패로_바꾸고_아무것도_저장하지_않는다() {
        TextExtractionException cause = new TextExtractionException("PDF를 읽을 수 없습니다", null);
        MaterialService materialService = new MaterialService((content, fileType) -> {
            throw cause;
        }, materialWriter);

        assertThatThrownBy(() -> materialService.upload(command()))
                .isInstanceOf(BusinessException.class)
                .hasCause(cause)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EXTRACTION_FAILED);

        assertThat(materialCount()).isZero();
    }

    private long materialCount() {
        return jdbcClient.sql("SELECT COUNT(*) FROM material WHERE owner_id = :ownerId")
                .param("ownerId", ownerId)
                .query(Long.class)
                .single();
    }
}
