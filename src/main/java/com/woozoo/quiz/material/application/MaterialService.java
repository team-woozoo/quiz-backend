package com.woozoo.quiz.material.application;

import com.woozoo.quiz.global.error.BusinessException;
import com.woozoo.quiz.global.error.ErrorCode;
import com.woozoo.quiz.material.domain.MaterialChunk;
import com.woozoo.quiz.material.domain.NewMaterial;
import com.woozoo.quiz.material.domain.PageText;
import com.woozoo.quiz.material.port.ExtractedPage;
import com.woozoo.quiz.material.port.TextExtractionException;
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

        List<ExtractedPage> extractedPages = extract(command);

        List<PageText> pageTexts = extractedPages.stream()
                .map(extractedPage -> new PageText(extractedPage.pageNo(),
                        extractedPage.text()))
                .toList();

        if(pageTexts.isEmpty()) {
            throw new BusinessException(ErrorCode.EXTRACTION_FAILED);
        }

        NewMaterial newMaterial = new NewMaterial(command.ownerId(), command.title(),
                command.originalFilename(), command.fileType());
        List<MaterialChunk> chunks = MaterialChunk.fromPages(pageTexts);

        return materialWriter.save(newMaterial, chunks);
    }

    private List<ExtractedPage> extract(UploadMaterialCommand command) {
        try {
            return textExtractionPort.extractPages(command.content(), command.fileType());
        }catch (TextExtractionException e) {
            throw new BusinessException(ErrorCode.EXTRACTION_FAILED, e);
        }
    }
}
