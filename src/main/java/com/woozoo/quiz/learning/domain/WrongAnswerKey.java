package com.woozoo.quiz.learning.domain;

// 사용자와 문제가 둘 다 long 이라 순서가 바뀌면 남의 오답 노트를 건드린다. 그래서 묶는다
public record WrongAnswerKey(long userId, long questionId) {
}
