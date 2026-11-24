package com.woozoo.quiz.material.port;

import com.woozoo.quiz.concept.domain.ConceptTag;

import java.util.List;

// 분석이 자료에서 뽑은 개념이다. 저장 전이라 청크는 DB id 가 아니라 자료 안의 순번(seq)으로 가리킨다.
// 응답 스키마로 그대로 쓰이므로 필드 이름이 분석 프롬프트의 JSON 키와 같아야 한다
public record ExtractedConcept(String name, String description, List<ConceptTag> tags, List<Integer> chunkSeqs) {
}
