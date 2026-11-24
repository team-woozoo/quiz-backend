package com.woozoo.quiz.material.application;

import com.woozoo.quiz.TestcontainersConfiguration;
import com.woozoo.quiz.concept.domain.ConceptTag;
import com.woozoo.quiz.global.error.BusinessException;
import com.woozoo.quiz.global.error.ErrorCode;
import com.woozoo.quiz.material.domain.FileType;
import com.woozoo.quiz.material.domain.MaterialChunk;
import com.woozoo.quiz.material.domain.NewMaterial;
import com.woozoo.quiz.material.persistence.MaterialRepository;
import com.woozoo.quiz.material.port.ExtractedConcept;
import com.woozoo.quiz.material.port.ExtractedDocument;
import com.woozoo.quiz.material.port.ExtractedPage;
import com.woozoo.quiz.material.port.MaterialAnalysis;
import com.woozoo.quiz.material.port.MaterialAnalysisException;
import com.woozoo.quiz.material.port.MaterialAnalysisPort;
import com.woozoo.quiz.material.port.TextExtractionException;
import com.woozoo.quiz.material.port.TextExtractionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class MaterialServiceTest {

    @Autowired
    MaterialRepository materialRepository;
    @Autowired
    ExtractedConceptValidator extractedConceptValidator;
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

    private static final int MAX_PAGES = 3;
    // 글자 있는 페이지보다 크게 잡아, 저장되는 쪽수가 청크 수가 아니라 원본 쪽수인지 가린다
    private static final int TOTAL_PAGES = 5;
    // 서비스는 앞 바이트로 PDF 인지만 본다. 추출은 가짜 포트가 하므로 나머지 내용은 상관없다
    private static final byte[] PDF_CONTENT = "%PDF-1.7 fake".getBytes(StandardCharsets.US_ASCII);

    private static final List<ExtractedPage> TWO_PAGES = List.of(
            new ExtractedPage(1, "first page"),
            new ExtractedPage(3, "third page"));

    // 청크마다 개념 하나씩, 태그를 붙여 학습 자료로 받는 분석이다
    private static final MaterialAnalysisPort ANALYZING = chunks -> new MaterialAnalysis(true, "학습 내용이 있다",
            chunks.stream()
                    .map(chunk -> new ExtractedConcept("개념 " + chunk.seq(), "설명 " + chunk.seq(),
                            List.of(ConceptTag.RULE), List.of(chunk.seq())))
                    .toList());

    private static final MaterialAnalysisPort NOT_CALLED = chunks -> {
        throw new AssertionError("분석까지 가지 않아야 한다");
    };

    // 추출과 분석은 가짜 포트로 바꿔 PDF 와 AI 없이 결과를 정하고, 검증과 저장은 진짜로 한다
    private MaterialService service(TextExtractionPort extraction, MaterialAnalysisPort analysis) {
        return new MaterialService(materialRepository, extraction, analysis, extractedConceptValidator,
                materialWriter, MAX_PAGES);
    }

    private MaterialService serviceExtracting(List<ExtractedPage> pages) {
        return service((content, fileType) -> new ExtractedDocument(TOTAL_PAGES, pages), ANALYZING);
    }

    private MaterialService serviceAnalyzing(MaterialAnalysisPort analysis) {
        return service((content, fileType) -> new ExtractedDocument(TOTAL_PAGES, TWO_PAGES), analysis);
    }

    private UploadMaterialCommand command() {
        return command("운영체제 5강", "05.pdf");
    }

    private UploadMaterialCommand command(String title, String originalFilename) {
        return new UploadMaterialCommand(PDF_CONTENT, ownerId, title, originalFilename, FileType.PDF);
    }

    private NewMaterial savedMaterial(long materialId) {
        return jdbcClient.sql("""
                SELECT owner_id, title, original_filename, file_type, total_page_count, content_hash
                FROM material
                WHERE id = :id
                """)
                .param("id", materialId)
                .query(NewMaterial.class)
                .single();
    }

    private static String sha256Hex(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private static void assertErrorCode(Throwable thrown, ErrorCode errorCode) {
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }

    @Test
    void 업로드하면_원본_쪽수와_파일_해시와_함께_자료와_청크를_저장한다() throws Exception {
        long materialId = serviceExtracting(TWO_PAGES).upload(command());

        assertThat(savedMaterial(materialId)).isEqualTo(new NewMaterial(ownerId, "운영체제 5강", "05.pdf",
                FileType.PDF, TOTAL_PAGES, sha256Hex(PDF_CONTENT)));

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
    void 분석한_개념을_태그와_함께_저장하고_가리킨_페이지에_잇는다() {
        long materialId = serviceExtracting(TWO_PAGES).upload(command());

        List<String> concepts = jdbcClient.sql("""
                SELECT name || ':' || description || ':' || array_to_string(tags, ',')
                FROM material_concept
                WHERE material_id = :materialId
                ORDER BY id
                """)
                .param("materialId", materialId)
                .query(String.class)
                .list();
        assertThat(concepts).containsExactly("개념 1:설명 1:RULE", "개념 2:설명 2:RULE");

        List<String> links = jdbcClient.sql("""
                SELECT mc.name || ':' || c.page_no
                FROM concept_chunk cc
                JOIN material_concept mc ON mc.id = cc.material_concept_id
                JOIN material_chunk c ON c.id = cc.material_chunk_id
                WHERE mc.material_id = :materialId
                ORDER BY mc.id
                """)
                .param("materialId", materialId)
                .query(String.class)
                .list();
        assertThat(links).containsExactly("개념 1:1", "개념 2:3");
    }

    @Test
    void 같은_파일을_다시_올리면_분석하지_않고_거부한다() {
        serviceExtracting(TWO_PAGES).upload(command());
        MaterialService again = service((content, fileType) -> new ExtractedDocument(TOTAL_PAGES, TWO_PAGES),
                NOT_CALLED);

        assertErrorCode(catchThrowable(() -> again.upload(command("다른 제목", "other.pdf"))),
                ErrorCode.MATERIAL_ALREADY_EXISTS);
        assertThat(materialCount()).isOne();
    }

    @Test
    void 같은_파일이라도_다른_사용자는_올릴_수_있다() {
        serviceExtracting(TWO_PAGES).upload(command());
        long otherId = jdbcClient.sql("""
                INSERT INTO users (email, password_hash, nickname)
                VALUES ('other@example.com', 'hash', 'other')
                RETURNING id
                """)
                .query(Long.class)
                .single();

        long materialId = serviceExtracting(TWO_PAGES).upload(
                new UploadMaterialCommand(PDF_CONTENT, otherId, "제목", "05.pdf", FileType.PDF));

        assertThat(savedMaterial(materialId).ownerId()).isEqualTo(otherId);
    }

    @Test
    void 학습_자료가_아니라고_판단하면_거부하고_아무것도_저장하지_않는다() {
        MaterialService service = serviceAnalyzing(chunks -> new MaterialAnalysis(false, "강의 일정만 있다", List.of()));

        assertErrorCode(catchThrowable(() -> service.upload(command())), ErrorCode.NOT_STUDY_MATERIAL);
        assertThat(materialCount()).isZero();
    }

    @Test
    void 분석이_실패하면_원인을_담아_분석_실패로_바꾸고_아무것도_저장하지_않는다() {
        MaterialAnalysisException cause = new MaterialAnalysisException("LLM 응답을 쓸 수 없다");
        MaterialService service = serviceAnalyzing(chunks -> {
            throw cause;
        });

        Throwable thrown = catchThrowable(() -> service.upload(command()));

        assertErrorCode(thrown, ErrorCode.ANALYSIS_FAILED);
        assertThat(thrown).hasCause(cause);
        assertThat(materialCount()).isZero();
    }

    // 학습 자료라면서 개념이 없거나, 검증에서 다 빠지면 문제를 만들 수 없다
    @Test
    void 검증을_거쳐_남은_개념이_없으면_분석_실패로_보고_아무것도_저장하지_않는다() {
        MaterialService service = serviceAnalyzing(chunks -> new MaterialAnalysis(true, "학습 내용이 있다", List.of(
                new ExtractedConcept("보내지 않은 페이지만 가리킨 개념", "설명", List.of(), List.of(99)))));

        assertErrorCode(catchThrowable(() -> service.upload(command())), ErrorCode.ANALYSIS_FAILED);
        assertThat(materialCount()).isZero();
    }

    @Test
    void 검증이_고친_개념으로_저장한다() {
        MaterialService service = serviceAnalyzing(chunks -> new MaterialAnalysis(true, "학습 내용이 있다", List.of(
                new ExtractedConcept("TCP", "앞쪽 설명", List.of(ConceptTag.RULE), List.of(1, 99)),
                new ExtractedConcept("TCP ", "뒤쪽 설명", List.of(ConceptTag.CONTRAST), List.of(2)))));

        long materialId = service.upload(command());

        List<String> concepts = jdbcClient.sql("""
                SELECT name || ':' || description || ':' || array_to_string(tags, ',')
                FROM material_concept
                WHERE material_id = :materialId
                """)
                .param("materialId", materialId)
                .query(String.class)
                .list();
        assertThat(concepts).containsExactly("TCP:앞쪽 설명:RULE,CONTRAST");
    }

    // 같은 파일을 거의 동시에 올리면 둘 다 사전 검사를 지나, 나중에 저장하는 쪽이 유일 인덱스에 걸린다
    @Test
    void 저장하다_파일_해시_인덱스에_걸리면_이미_올린_자료로_바꾼다() {
        DuplicateKeyException duplicate = new DuplicateKeyException(
                "duplicate key value violates unique constraint \"uq_material_owner_id_content_hash\"");
        MaterialService service = new MaterialService(materialRepository,
                (content, fileType) -> new ExtractedDocument(TOTAL_PAGES, TWO_PAGES), ANALYZING,
                extractedConceptValidator, writerThrowing(duplicate), MAX_PAGES);

        Throwable thrown = catchThrowable(() -> service.upload(command()));

        assertErrorCode(thrown, ErrorCode.MATERIAL_ALREADY_EXISTS);
        assertThat(thrown).hasCause(duplicate);
    }

    // 다른 유일 제약은 우리 코드의 버그라 중복으로 숨기지 않는다
    @Test
    void 저장하다_다른_유일_제약에_걸리면_그대로_던진다() {
        DuplicateKeyException duplicate = new DuplicateKeyException(
                "duplicate key value violates unique constraint \"material_concept_material_id_name_key\"");
        MaterialService service = new MaterialService(materialRepository,
                (content, fileType) -> new ExtractedDocument(TOTAL_PAGES, TWO_PAGES), ANALYZING,
                extractedConceptValidator, writerThrowing(duplicate), MAX_PAGES);

        assertThat(catchThrowable(() -> service.upload(command()))).isSameAs(duplicate);
    }

    @Test
    void 추출된_텍스트가_없으면_예외를_던지고_아무것도_저장하지_않는다() {
        assertErrorCode(catchThrowable(() -> serviceExtracting(List.of()).upload(command())),
                ErrorCode.EXTRACTION_FAILED);
        assertThat(materialCount()).isZero();
    }

    @Test
    void PDF를_읽지_못하면_원인을_담아_추출_실패로_바꾸고_아무것도_저장하지_않는다() {
        TextExtractionException cause = new TextExtractionException("PDF를 읽을 수 없습니다", null);
        MaterialService materialService = service((content, fileType) -> {
            throw cause;
        }, NOT_CALLED);

        Throwable thrown = catchThrowable(() -> materialService.upload(command()));

        assertErrorCode(thrown, ErrorCode.EXTRACTION_FAILED);
        assertThat(thrown).hasCause(cause);
        assertThat(materialCount()).isZero();
    }

    @Test
    void PDF_로_시작하지_않는_파일은_추출하지_않고_거부한다() {
        MaterialService materialService = service((content, fileType) -> {
            throw new AssertionError("PDF 가 아니면 추출까지 가지 않아야 한다");
        }, NOT_CALLED);
        UploadMaterialCommand notPdf = new UploadMaterialCommand(
                "hello".getBytes(StandardCharsets.UTF_8), ownerId, "제목", "note.pdf", FileType.PDF);

        assertErrorCode(catchThrowable(() -> materialService.upload(notPdf)), ErrorCode.INVALID_FILE);
        assertThat(materialCount()).isZero();
    }

    @Test
    void 글자가_있는_페이지가_제한보다_많으면_분석하지_않고_거부한다() {
        MaterialService materialService = service((content, fileType) -> new ExtractedDocument(TOTAL_PAGES,
                IntStream.rangeClosed(1, MAX_PAGES + 1)
                        .mapToObj(pageNo -> new ExtractedPage(pageNo, "page " + pageNo))
                        .toList()), NOT_CALLED);

        assertErrorCode(catchThrowable(() -> materialService.upload(command())), ErrorCode.TOO_MANY_PAGES);
        assertThat(materialCount()).isZero();
    }

    @Test
    void 제목이_비어_있으면_확장자를_뺀_파일_이름을_제목으로_쓴다() {
        long materialId = serviceExtracting(TWO_PAGES).upload(command("  ", "운영체제 5강.pdf"));

        assertThat(savedMaterial(materialId).title()).isEqualTo("운영체제 5강");
    }

    @Test
    void 파일_이름에_붙은_경로는_떼고_저장한다() {
        long materialId = serviceExtracting(TWO_PAGES).upload(command("제목", "C:\\Users\\me\\lecture/05.pdf"));

        assertThat(savedMaterial(materialId).originalFilename()).isEqualTo("05.pdf");
    }

    // 저장 단계에서만 실패를 내려고, 저장소 없이 save 만 바꾼다
    private MaterialWriter writerThrowing(RuntimeException failure) {
        return new MaterialWriter(materialRepository, null) {
            @Override
            public long save(NewMaterial material, List<MaterialChunk> materialChunks,
                             List<ExtractedConcept> extractedConcepts) {
                throw failure;
            }
        };
    }

    private long materialCount() {
        return jdbcClient.sql("SELECT COUNT(*) FROM material WHERE owner_id = :ownerId")
                .param("ownerId", ownerId)
                .query(Long.class)
                .single();
    }
}
