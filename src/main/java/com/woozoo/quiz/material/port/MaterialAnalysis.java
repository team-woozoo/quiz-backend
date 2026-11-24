package com.woozoo.quiz.material.port;

import java.util.List;

// 학습 자료로 받을 만한지(suitable)와 자료의 개념 목록이다. suitable 이 false 면 개념은 비어 있다.
// reason 은 판단을 확인하는 데 쓰고 저장하지 않는다
public record MaterialAnalysis(boolean suitable, String reason, List<ExtractedConcept> concepts) {
}
