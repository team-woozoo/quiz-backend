package com.woozoo.quiz.quiz.domain;

// 문제의 근거가 어디까지인지 나타낸다.
// GROUNDED 는 근거 문제로, 정답이 자료 안에 있고 인용을 원문과 대조해 확인한다.
// EXTENDED 는 심화 문제로, 자료의 원리를 처음 보는 상황에 쓰게 한다. 상황은 새로 만들지만 정답을 가르는 지식은 자료에 있다.
// 어느 종류로 낼지는 애플리케이션이 정하고, 어떻게 만들지(프롬프트)는 생성 어댑터가 정한다
public enum SourceScope {
    GROUNDED,
    EXTENDED
}
