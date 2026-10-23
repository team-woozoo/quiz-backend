package com.woozoo.quiz.concept.domain;

// 대표 개념이 나온 페이지(청크) 하나와, 그 페이지를 가진 자료 개념이다. 약점 퀴즈의 문제를 원래 자료 개념에 붙일 때 쓴다
public record ConceptPage(long chunkId, String content, long materialConceptId) {
}
