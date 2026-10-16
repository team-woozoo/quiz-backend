package com.woozoo.quiz.material.application;

import com.woozoo.quiz.TestcontainersConfiguration;
import com.woozoo.quiz.global.error.BusinessException;
import com.woozoo.quiz.global.error.ErrorCode;
import com.woozoo.quiz.material.domain.FileType;
import com.woozoo.quiz.material.domain.MaterialChunk;
import com.woozoo.quiz.material.domain.NewMaterial;
import com.woozoo.quiz.material.persistence.MaterialRepository;
import com.woozoo.quiz.material.port.ExtractedPage;
import com.woozoo.quiz.material.port.TextExtractionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class MaterialServiceTest {

    @Autowired
    MaterialWriter materialWriter;
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

    private static final int MAX_PAGES = 3;
    // 서비스는 앞 바이트로 PDF 인지만 본다. 추출은 가짜 포트가 하므로 나머지 내용은 상관없다
    private static final byte[] PDF_CONTENT = "%PDF-1.7 fake".getBytes(StandardCharsets.US_ASCII);

    // 추출 포트는 가짜로 바꿔 PDF 없이 추출 결과를 정하고, 저장은 진짜 DB 에 한다
    private MaterialService serviceExtracting(List<ExtractedPage> pages) {
        return new MaterialService((content, fileType) -> pages, materialWriter, materialRepository, MAX_PAGES);
    }

    private UploadMaterialCommand command() {
        return command("운영체제 5강", "05.pdf");
    }

    private UploadMaterialCommand command(String title, String originalFilename) {
        return new UploadMaterialCommand(PDF_CONTENT, ownerId, title, originalFilename, FileType.PDF);
    }

    private NewMaterial savedMaterial(long materialId) {
        return jdbcClient.sql("""
                SELECT owner_id, title, original_filename, file_type
                FROM material
                WHERE id = :id
                """)
                .param("id", materialId)
                .query(NewMaterial.class)
                .single();
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
        }, materialWriter, materialRepository, MAX_PAGES);

        assertThatThrownBy(() -> materialService.upload(command()))
                .isInstanceOf(BusinessException.class)
                .hasCause(cause)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EXTRACTION_FAILED);

        assertThat(materialCount()).isZero();
    }

    @Test
    void PDF_로_시작하지_않는_파일은_추출하지_않고_거부한다() {
        MaterialService materialService = new MaterialService((content, fileType) -> {
            throw new AssertionError("PDF 가 아니면 추출까지 가지 않아야 한다");
        }, materialWriter, materialRepository, MAX_PAGES);
        UploadMaterialCommand notPdf = new UploadMaterialCommand(
                "hello".getBytes(StandardCharsets.UTF_8), ownerId, "제목", "note.pdf", FileType.PDF);

        assertThatThrownBy(() -> materialService.upload(notPdf))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_FILE);
        assertThat(materialCount()).isZero();
    }

    @Test
    void 글자가_있는_페이지가_제한보다_많으면_거부한다() {
        MaterialService materialService = serviceExtracting(IntStream.rangeClosed(1, MAX_PAGES + 1)
                .mapToObj(pageNo -> new ExtractedPage(pageNo, "page " + pageNo))
                .toList());

        assertThatThrownBy(() -> materialService.upload(command()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TOO_MANY_PAGES);
        assertThat(materialCount()).isZero();
    }

    @Test
    void 제목이_비어_있으면_확장자를_뺀_파일_이름을_제목으로_쓴다() {
        MaterialService materialService = serviceExtracting(List.of(new ExtractedPage(1, "page")));

        long materialId = materialService.upload(command("  ", "운영체제 5강.pdf"));

        assertThat(savedMaterial(materialId).title()).isEqualTo("운영체제 5강");
    }

    @Test
    void 파일_이름에_붙은_경로는_떼고_저장한다() {
        MaterialService materialService = serviceExtracting(List.of(new ExtractedPage(1, "page")));

        long materialId = materialService.upload(command("제목", "C:\\Users\\me\\lecture/05.pdf"));

        assertThat(savedMaterial(materialId).originalFilename()).isEqualTo("05.pdf");
    }

    private long materialCount() {
        return jdbcClient.sql("SELECT COUNT(*) FROM material WHERE owner_id = :ownerId")
                .param("ownerId", ownerId)
                .query(Long.class)
                .single();
    }
}
