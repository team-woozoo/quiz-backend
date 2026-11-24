package com.woozoo.quiz.material.application;

import com.woozoo.quiz.global.error.BusinessException;
import com.woozoo.quiz.global.error.ErrorCode;
import com.woozoo.quiz.material.domain.MaterialChunk;
import com.woozoo.quiz.material.domain.NewMaterial;
import com.woozoo.quiz.material.domain.PageText;
import com.woozoo.quiz.material.persistence.MaterialRepository;
import com.woozoo.quiz.material.port.ExtractedConcept;
import com.woozoo.quiz.material.port.ExtractedDocument;
import com.woozoo.quiz.material.port.MaterialAnalysis;
import com.woozoo.quiz.material.port.MaterialAnalysisException;
import com.woozoo.quiz.material.port.MaterialAnalysisPort;
import com.woozoo.quiz.material.port.TextExtractionException;
import com.woozoo.quiz.material.port.TextExtractionPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

@Service
@Slf4j
public class MaterialService {

    // PDF 파일은 항상 이 바이트로 시작한다
    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final int MAX_NAME_LENGTH = 255;
    // V6 마이그레이션이 만든 유일 인덱스 이름이다. 오류 메시지에서 어느 제약에 걸렸는지 가리는 데 쓴다
    private static final String CONTENT_HASH_INDEX = "uq_material_owner_id_content_hash";

    private final MaterialRepository materialRepository;
    private final TextExtractionPort textExtractionPort;
    private final MaterialAnalysisPort materialAnalysisPort;
    private final ExtractedConceptValidator extractedConceptValidator;
    private final MaterialWriter materialWriter;
    private final int maxPages;

    public MaterialService(MaterialRepository materialRepository,
                           TextExtractionPort textExtractionPort,
                           MaterialAnalysisPort materialAnalysisPort,
                           ExtractedConceptValidator extractedConceptValidator,
                           MaterialWriter materialWriter,
                           @Value("${material.upload.max-pages}") int maxPages) {
        this.materialRepository = materialRepository;
        this.textExtractionPort = textExtractionPort;
        this.materialAnalysisPort = materialAnalysisPort;
        this.extractedConceptValidator = extractedConceptValidator;
        this.materialWriter = materialWriter;
        this.maxPages = maxPages;
    }

    public long upload(UploadMaterialCommand command) {
        // 확장자나 Content-Type 은 앱이 마음대로 붙일 수 있어 파일 내용으로 확인한다
        if (!isPdf(command.content())) {
            throw new BusinessException(ErrorCode.INVALID_FILE);
        }

        // 같은 파일을 다시 올리면 분석을 다시 돌리지 않고, 개념이 자료 둘로 갈라지지 않게 AI 를 부르기 전에 막는다
        String contentHash = contentHashOf(command.content());
        if (materialRepository.existsByOwnerIdAndContentHash(command.ownerId(), contentHash)) {
            throw new BusinessException(ErrorCode.MATERIAL_ALREADY_EXISTS);
        }

        ExtractedDocument extracted = extract(command);

        List<PageText> pageTexts = extracted.pages().stream()
                .map(extractedPage -> new PageText(extractedPage.pageNo(),
                        extractedPage.text()))
                .toList();

        if (pageTexts.isEmpty()) {
            throw new BusinessException(ErrorCode.EXTRACTION_FAILED);
        }
        // 페이지가 많을수록 문제 생성 때 AI 에 보내는 글자와 비용이 늘어난다
        if (pageTexts.size() > maxPages) {
            throw new BusinessException(ErrorCode.TOO_MANY_PAGES);
        }

        String originalFilename = fileNameOf(command.originalFilename());
        NewMaterial newMaterial = new NewMaterial(command.ownerId(), titleOf(command.title(), originalFilename),
                originalFilename, command.fileType(), extracted.totalPageCount(), contentHash);
        List<MaterialChunk> chunks = MaterialChunk.fromPages(pageTexts);

        // 분석은 트랜잭션 밖에서 한다. 실패하거나 학습 자료가 아니면 자료도 청크도 저장하지 않는다
        List<ExtractedConcept> concepts = extractedConceptValidator.validate(chunks, analyze(chunks));
        // 학습 자료라면서 쓸 개념이 하나도 없으면 문제를 만들 수 없다. 모델이 이상하게 답한 것이라 다시 시도하게 한다
        if (concepts.isEmpty()) {
            throw new BusinessException(ErrorCode.ANALYSIS_FAILED);
        }

        return save(newMaterial, chunks, concepts);
    }

    private ExtractedDocument extract(UploadMaterialCommand command) {
        try {
            return textExtractionPort.extractPages(command.content(), command.fileType());
        } catch (TextExtractionException e) {
            throw new BusinessException(ErrorCode.EXTRACTION_FAILED, e);
        }
    }

    // 학습 자료로 받을 때만 개념을 돌려준다. 판단 이유는 자료 내용을 요약하므로 부적합일 때만 확인용으로 남긴다
    private List<ExtractedConcept> analyze(List<MaterialChunk> chunks) {
        MaterialAnalysis analysis;
        try {
            analysis = materialAnalysisPort.analyze(chunks);
        } catch (MaterialAnalysisException e) {
            throw new BusinessException(ErrorCode.ANALYSIS_FAILED, e);
        }

        if (!analysis.suitable()) {
            log.info("학습 자료가 아니라고 판단했다: {}", analysis.reason());
            throw new BusinessException(ErrorCode.NOT_STUDY_MATERIAL);
        }
        return analysis.concepts();
    }

    // 같은 파일을 거의 동시에 올리면 둘 다 중복 검사를 지나 분석까지 마치고, 나중에 저장하는 쪽이 유일 인덱스에 걸린다.
    // 같은 트랜잭션의 개념 이름 제약에 걸린 것은 검증 버그라 중복으로 바꾸지 않고 그대로 던져 500 으로 드러낸다
    private long save(NewMaterial newMaterial, List<MaterialChunk> chunks, List<ExtractedConcept> concepts) {
        try {
            return materialWriter.save(newMaterial, chunks, concepts);
        } catch (DuplicateKeyException e) {
            if (e.getMessage() != null && e.getMessage().contains(CONTENT_HASH_INDEX)) {
                throw new BusinessException(ErrorCode.MATERIAL_ALREADY_EXISTS, e);
            }
            throw e;
        }
    }

    private boolean isPdf(byte[] content) {
        return content != null
                && content.length >= PDF_SIGNATURE.length
                && Arrays.equals(content, 0, PDF_SIGNATURE.length, PDF_SIGNATURE, 0, PDF_SIGNATURE.length);
    }

    // 브라우저나 기기에 따라 경로가 붙어 오기도 해서 마지막 이름만 남기고, 컬럼 길이에 맞춰 자른다
    private String fileNameOf(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "material.pdf";
        }
        String name = originalFilename.substring(
                Math.max(originalFilename.lastIndexOf('/'), originalFilename.lastIndexOf('\\')) + 1).strip();
        if (name.isEmpty()) {
            return "material.pdf";
        }
        return truncate(name);
    }

    // 제목을 따로 입력하지 않으면 확장자를 뺀 파일 이름을 쓴다
    private String titleOf(String title, String fileName) {
        if (title != null && !title.isBlank()) {
            return truncate(title.strip());
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private String truncate(String text) {
        return text.length() > MAX_NAME_LENGTH ? text.substring(0, MAX_NAME_LENGTH) : text;
    }

    // 파일 바이트의 SHA-256 이다. SHA-256 은 모든 JDK 가 반드시 제공하는 알고리즘이라 없으면 실행 환경이 잘못된 것이다
    private String contentHashOf(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
