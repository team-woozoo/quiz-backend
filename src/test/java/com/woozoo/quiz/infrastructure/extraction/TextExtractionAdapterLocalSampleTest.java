package com.woozoo.quiz.infrastructure.extraction;

import com.woozoo.quiz.material.domain.FileType;
import com.woozoo.quiz.material.port.ExtractedPage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextExtractionAdapterLocalSampleTest {

    private final SpringAiTextExtractionAdapter springAiTextExtractionAdapter =
            new SpringAiTextExtractionAdapter();

    private static final Path SIMPLE_PDF =
            Path.of("src/test/resources/private/sample.pdf");

    @Test
    void 로컬_강의자료의_추출_결과를_확인한다() throws IOException {
        Assumptions.assumeTrue(Files.isRegularFile(SIMPLE_PDF),
                "로컬 강의자료가 없어 건너뜀");

        byte[] content = Files.readAllBytes(SIMPLE_PDF);
        List<ExtractedPage> extractedPages =
                springAiTextExtractionAdapter.extractPages(content, FileType.PDF);

        List<Integer> pageNos = extractedPages.stream()
                .map(ExtractedPage::pageNo)
                .toList();

        int totalPages;
        try (PDDocument pdf = Loader.loadPDF(content)) {
            totalPages = pdf.getNumberOfPages();
        }

        assertThat(pageNos)
                .doesNotContainNull()
                .doesNotHaveDuplicates()
                .isSorted()
                .allMatch(pageNo -> pageNo >= 1 && pageNo <= totalPages);

        assertThat(extractedPages)
                .extracting(ExtractedPage::text)
                .allSatisfy(text -> assertThat(text).isNotBlank());

        for (ExtractedPage page : extractedPages) {
            System.out.println("===== p." + page.pageNo() + " =====");
            System.out.println(page.text());
        }
    }
}
