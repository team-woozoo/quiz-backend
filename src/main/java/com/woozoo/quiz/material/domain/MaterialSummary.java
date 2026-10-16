package com.woozoo.quiz.material.domain;

import java.time.Instant;

// 내 자료 목록에 보여 줄 값이다. pageCount 는 글자가 있어 저장된 페이지 수다
public record MaterialSummary(long id, String title, String originalFilename, int pageCount, Instant createdAt) {
}
