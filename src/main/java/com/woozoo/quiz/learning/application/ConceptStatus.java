package com.woozoo.quiz.learning.application;

// 자료 분석 화면에서 개념 옆에 붙는 상태. 선언 순서가 화면에 보여 줄 순서다(부족한 개념부터)
public enum ConceptStatus {
    // 정답률이 기준보다 낮다
    WEAK,
    // 기준 이상이지만 다 맞히지는 못했다
    GOOD,
    // 그 개념의 문제를 모두 맞혔다
    PERFECT,
    // 그 개념의 문제를 하나도 풀지 않았다
    UNCHECKED;

    // 안 푼 문제는 틀린 것으로 보아 문제 수 전체로 나눈다
    static ConceptStatus of(int questionCount, int answeredCount, int correctCount, double weakBelow) {
        if (answeredCount == 0) {
            return UNCHECKED;
        }
        if (correctCount == questionCount) {
            return PERFECT;
        }
        return (double) correctCount / questionCount < weakBelow ? WEAK : GOOD;
    }
}
