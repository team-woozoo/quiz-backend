package com.woozoo.quiz.infrastructure.extraction;

import com.woozoo.quiz.material.domain.FileType;
import com.woozoo.quiz.material.port.ExtractedPage;
import com.woozoo.quiz.material.port.TextExtractionException;
import com.woozoo.quiz.material.port.TextExtractionPort;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class SpringAiTextExtractionAdapter implements TextExtractionPort {

    private static final PdfDocumentReaderConfig DEFAULT_CONFIG =
            PdfDocumentReaderConfig.builder()
                    .withPagesPerDocument(1)
                    .build();

    @Override
    public List<ExtractedPage> extractPages(byte[] content, FileType fileType) {
        Resource resource = new ByteArrayResource(content);

        try (CloseablePdfReader reader = new CloseablePdfReader(resource, DEFAULT_CONFIG)){

            List<Document> documents = reader.get();

            return documents.stream()
                    .map(document -> {
                        // 리더가 글자 없는 페이지를 결과에서 빼므로, 페이지 번호는 목록 순서가 아니라 메타데이터에서 꺼낸다.
                        Integer pageNo = (Integer) document.getMetadata()
                                .get(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER);
                        String text = normalize(Objects.requireNonNull(document.getText(),
                                "PDF 페이지 Document 에 텍스트가 없다"));
                        return new ExtractedPage(pageNo, text);
                    })
                    .toList();
        } catch (Exception e) {
            throw new TextExtractionException("PDF를 읽을 수 없습니다", e);
        }
    }

    private static String normalize(String text) {
        return text.lines()
                .map(line -> line.strip().replaceAll("[ \\t]+", " "))
                .collect(Collectors.joining("\n"))
                .replaceAll("\n{3,}", "\n\n")
                .strip();
    }

    // PagePdfDocumentReader 는 PDDocument 를 열기만 하고 닫지 않는다. 필드가 protected 라 상속해서 닫는다.
    private static class CloseablePdfReader extends PagePdfDocumentReader implements AutoCloseable {
        CloseablePdfReader(Resource resource, PdfDocumentReaderConfig config) {
            super(resource, config);
        }
        @Override
        public void close() throws IOException {
            document.close();
        }
    }
}
