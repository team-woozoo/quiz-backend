package com.woozoo.quiz.material.domain;

import java.util.ArrayList;
import java.util.List;

public record MaterialChunk(int seq, Integer pageNo, String content) {

    public static List<MaterialChunk> fromPages(List<PageText> pages) {
        List<MaterialChunk> chunks = new ArrayList<>(pages.size());
        for(int i = 0; i < pages.size(); ++i) {
            PageText page = pages.get(i);
            chunks.add(new MaterialChunk(i + 1, page.pageNo(), page.text()));
        }
        return List.copyOf(chunks);
    }
}
