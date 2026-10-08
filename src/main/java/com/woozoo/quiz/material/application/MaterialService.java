package com.woozoo.quiz.material.application;

import com.woozoo.quiz.material.domain.MaterialChunk;
import com.woozoo.quiz.material.domain.NewMaterial;
import com.woozoo.quiz.material.domain.PageText;
import com.woozoo.quiz.material.port.ExtractedPage;
import com.woozoo.quiz.material.port.TextExtractionPort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MaterialService {

    private final TextExtractionPort textExtractionPort;
    private final MaterialWriter materialWriter;

    public MaterialService(TextExtractionPort textExtractionPort,
                           MaterialWriter materialWriter) {
        this.textExtractionPort = textExtractionPort;
        this.materialWriter = materialWriter;
    }

    public long upload(UploadMaterialCommand command) {
        List<ExtractedPage> extractedPages =
                textExtractionPort.extractPages(command.content(), command.fileType());

        List<PageText> pageTexts = extractedPages.stream()
                .map(extractedPage -> new PageText(extractedPage.pageNo(),
                        extractedPage.text()))
                .toList();

        if(pageTexts.isEmpty()) {
            // TODO: 전역 예외 처리에서 EXTRACTION_FAILED 로 바꾼다
            throw new IllegalArgumentException("텍스트를 추출할 수 없는 PDF 입니다");
        }

        NewMaterial newMaterial = new NewMaterial(command.ownerId(), command.title(),
                command.originalFilename(), command.fileType());
        List<MaterialChunk> chunks = MaterialChunk.fromPages(pageTexts);

        return materialWriter.save(newMaterial, chunks);
    }
}
