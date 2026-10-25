package com.woozoo.quiz.material.domain;

// totalPageCount 는 글자 없는 페이지까지 센 원본 PDF 의 쪽수다
public record NewMaterial(long ownerId, String title, String originalFilename, FileType fileType,
                          int totalPageCount) {
}
