package com.woozoo.quiz.quiz.port;

import java.util.List;

// 약점 퀴즈에서 문제를 모을 개념 하나와, 그 개념으로 이미 낸 문제 문장이다. 같은 문제를 다시 내지 않게 넘긴다
public record ConceptFocus(String name, String description, List<String> askedQuestions) {
}
