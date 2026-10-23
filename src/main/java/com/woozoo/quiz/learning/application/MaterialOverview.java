package com.woozoo.quiz.learning.application;

import com.woozoo.quiz.learning.domain.MaterialStat;
import com.woozoo.quiz.material.domain.MaterialSummary;

// 자료 정보에 그 자료로 만든 문제 수와 내 풀이 결과를 붙인다.
// 안 푼 문제는 틀린 것으로 보아 정답 + 오답 = 문제 수가 되게 한다
public record MaterialOverview(MaterialSummary material, int questionCount, int answeredCount, int correctCount) {

    static MaterialOverview of(MaterialSummary material, MaterialStat stat) {
        return new MaterialOverview(material, stat.questionCount(), stat.answeredCount(), stat.correctCount());
    }

    public int wrongCount() {
        return questionCount - correctCount;
    }

    // 문제를 만든 적이 없으면 비율이 없다
    public Double accuracy() {
        return questionCount == 0 ? null : (double) correctCount / questionCount;
    }
}
