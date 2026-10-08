package com.woozoo.quiz.infrastructure.extraction;

import com.woozoo.quiz.material.domain.FileType;
import com.woozoo.quiz.material.port.ExtractedPage;
import com.woozoo.quiz.material.port.TextExtractionException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringAiTextExtractionAdapterTest {

    private final SpringAiTextExtractionAdapter springAiTextExtractionAdapter =
            new SpringAiTextExtractionAdapter();

    private static byte[] pdfWithPages(String... pageTexts) throws IOException {
        try(PDDocument document = new PDDocument();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            // 기본 폰트는 한글을 그리지 못한다. 한글이 필요하면 TTF 를 PDType0Font 로 로드해야 한다.
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            for(String text : pageTexts){
                PDPage page = new PDPage();
                document.addPage(page);
                if(text.isEmpty()){
                    continue;
                }
                try(PDPageContentStream contentStream = new PDPageContentStream(document, page)){
                    contentStream.beginText();
                    contentStream.setFont(font, 12);
                    contentStream.newLineAtOffset(72, 700);
                    contentStream.showText(text);
                    contentStream.endText();
                }
            }
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    @Test
    void 빈_페이지를_건너뛰어도_원본_페이지_번호를_유지한다() throws IOException {
        byte[] pdf = pdfWithPages("first page", "", "third page");

        List<ExtractedPage> extractedPages =
                springAiTextExtractionAdapter.extractPages(pdf, FileType.PDF);

        assertThat(extractedPages).extracting(ExtractedPage::pageNo).containsExactly(1,3);
    }

    @Test
    void 연속된_공백을_하나로_줄인다() throws IOException {
        byte[] pdf = pdfWithPages("hello           world");

        List<ExtractedPage> extractedPages =
                springAiTextExtractionAdapter.extractPages(pdf, FileType.PDF);

        assertThat(extractedPages.getFirst().text()).isEqualTo("hello world");
    }

    @Test
    void PDF가_아니면_추출_예외를_던진다() {
        byte[] notPdf = "this is not pdf file".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> springAiTextExtractionAdapter.extractPages(notPdf, FileType.PDF))
                .isInstanceOf(TextExtractionException.class);
    }
}
