package com.woozoo.quiz.learning.domain;

import com.woozoo.quiz.quiz.domain.Difficulty;
import com.woozoo.quiz.quiz.domain.QuizFormat;

import java.time.Instant;

// 내 퀴즈 목록의 한 줄이다. 퀴즈 정보에 내 풀이 기록(푼 횟수, 가장 최근 결과)을 붙인다.
// 자료 없이 만든 퀴즈는 materialTitle 이, 아직 안 푼 퀴즈는 lastSubmission 이 null 이다
public record QuizSummary(
        long quizId,
        String materialTitle,
        QuizFormat format,
        Difficulty difficulty,
        int questionCount,
        Instant createdAt,
        int submissionCount,
        LastSubmission lastSubmission) {

    public record LastSubmission(int correctCount, Instant submittedAt) {
    }
}
