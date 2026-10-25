package com.woozoo.quiz.material.port;

import com.woozoo.quiz.material.domain.FileType;

public interface TextExtractionPort {

    /**
     * PDF 에서 페이지별 텍스트를 뽑고, 원본 PDF 의 전체 쪽수를 함께 준다.
     * 글자가 없는 페이지는 결과에서 빼고, 페이지 번호는 원본 PDF 의 번호를 그대로 쓴다.
     * 텍스트의 연속 공백은 하나로 줄인다.
     *
     * @throws TextExtractionException 파일을 PDF 로 읽을 수 없을 때 (손상, 암호 등)
     */
    ExtractedDocument extractPages(byte[] content, FileType fileType);
}
