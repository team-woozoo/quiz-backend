package com.woozoo.quiz.material.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialChunkTest {

    @Test
    void 빈_페이지가_빠져도_seq는_이어지고_텍스트를_그대로_옮긴다(){
        List<PageText> pageTexts = List.of(
                new PageText(1, "first page"),
                new PageText(2, "second page"),
                new PageText(4, "fourth page")
        );

        List<MaterialChunk> chunks = MaterialChunk.fromPages(pageTexts);

        assertThat(chunks).extracting(MaterialChunk::seq).containsExactly(1, 2, 3);
        assertThat(chunks).extracting(MaterialChunk::pageNo).containsExactly(1, 2, 4);
        assertThat(chunks).extracting(MaterialChunk::content)
                .containsExactly("first page", "second page", "fourth page");
    }

    @Test
    void 빈_목록이면_빈_목록을_반환한다(){
        List<PageText> pageTexts = List.of();
        List<MaterialChunk> chunks = MaterialChunk.fromPages(pageTexts);

        assertThat(chunks).isEmpty();
    }
}
