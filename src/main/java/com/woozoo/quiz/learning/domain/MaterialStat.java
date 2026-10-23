package com.woozoo.quiz.learning.domain;

// 자료 하나로 만든 퀴즈들의 문제 수와, 그중 푼 문제와 맞힌 문제 수다. 푼 문제는 최근 답 기준으로 문제마다 한 번 센다
public record MaterialStat(long materialId, int questionCount, int answeredCount, int correctCount) {

    public static MaterialStat empty(long materialId) {
        return new MaterialStat(materialId, 0, 0, 0);
    }
}
