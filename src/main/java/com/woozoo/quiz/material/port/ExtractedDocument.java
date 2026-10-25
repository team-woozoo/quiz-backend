package com.woozoo.quiz.material.port;

import java.util.List;

// pages 는 글자가 있는 페이지만 담아서, 원본 PDF 의 쪽수는 totalPageCount 로 따로 준다
public record ExtractedDocument(int totalPageCount, List<ExtractedPage> pages) {
}
