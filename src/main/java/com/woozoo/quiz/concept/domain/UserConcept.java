package com.woozoo.quiz.concept.domain;

// 사용자 단위 대표 개념. 여러 자료의 같은 개념을 하나로 묶는 기준이고, 약점 분석과 약점 퀴즈가 이것을 겨냥한다
public record UserConcept(long id, long userId, String name, String description) {
}
