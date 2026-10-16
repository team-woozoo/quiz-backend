package com.woozoo.quiz.material.application;

import com.woozoo.quiz.global.error.BusinessException;
import com.woozoo.quiz.global.error.ErrorCode;
import com.woozoo.quiz.material.domain.MaterialChunk;
import com.woozoo.quiz.material.domain.MaterialSummary;
import com.woozoo.quiz.material.domain.NewMaterial;
import com.woozoo.quiz.material.domain.PageText;
import com.woozoo.quiz.material.persistence.MaterialRepository;
import com.woozoo.quiz.material.port.ExtractedPage;
import com.woozoo.quiz.material.port.TextExtractionException;
import com.woozoo.quiz.material.port.TextExtractionPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Service
public class MaterialService {

    // PDF 파일은 항상 이 바이트로 시작한다
    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final int MAX_NAME_LENGTH = 255;

    private final TextExtractionPort textExtractionPort;
    private final MaterialWriter materialWriter;
    private final MaterialRepository materialRepository;
    private final int maxPages;

    public MaterialService(TextExtractionPort textExtractionPort,
                           MaterialWriter materialWriter,
                           MaterialRepository materialRepository,
                           @Value("${material.upload.max-pages}") int maxPages) {
        this.textExtractionPort = textExtractionPort;
        this.materialWriter = materialWriter;
        this.materialRepository = materialRepository;
        this.maxPages = maxPages;
    }

    public long upload(UploadMaterialCommand command) {
        // 확장자나 Content-Type 은 앱이 마음대로 붙일 수 있어 파일 내용으로 확인한다
        if (!isPdf(command.content())) {
            throw new BusinessException(ErrorCode.INVALID_FILE);
        }

        List<ExtractedPage> extractedPages = extract(command);

        List<PageText> pageTexts = extractedPages.stream()
                .map(extractedPage -> new PageText(extractedPage.pageNo(),
                        extractedPage.text()))
                .toList();

        if(pageTexts.isEmpty()) {
            throw new BusinessException(ErrorCode.EXTRACTION_FAILED);
        }
        // 페이지가 많을수록 문제 생성 때 AI 에 보내는 글자와 비용이 늘어난다
        if (pageTexts.size() > maxPages) {
            throw new BusinessException(ErrorCode.TOO_MANY_PAGES);
        }

        String originalFilename = fileNameOf(command.originalFilename());
        NewMaterial newMaterial = new NewMaterial(command.ownerId(), titleOf(command.title(), originalFilename),
                originalFilename, command.fileType());
        List<MaterialChunk> chunks = MaterialChunk.fromPages(pageTexts);

        return materialWriter.save(newMaterial, chunks);
    }

    public List<MaterialSummary> getMyMaterials(long ownerId) {
        return materialRepository.findSummariesByOwnerId(ownerId);
    }

    private List<ExtractedPage> extract(UploadMaterialCommand command) {
        try {
            return textExtractionPort.extractPages(command.content(), command.fileType());
        }catch (TextExtractionException e) {
            throw new BusinessException(ErrorCode.EXTRACTION_FAILED, e);
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
}
