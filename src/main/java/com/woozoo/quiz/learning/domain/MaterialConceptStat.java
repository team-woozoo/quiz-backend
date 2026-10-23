package com.woozoo.quiz.learning.domain;

import java.util.List;

// 자료의 개념 하나와 그 개념이 나온 페이지, 그 개념 문제들의 문제 수·푼 수·맞힌 수다
public record MaterialConceptStat(
        long conceptId,
        String conceptName,
        List<Integer> pageNos,
        int questionCount,
        int answeredCount,
        int correctCount) {
}
