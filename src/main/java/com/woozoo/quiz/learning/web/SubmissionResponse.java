package com.woozoo.quiz.learning.web;

import com.woozoo.quiz.learning.application.SubmissionResult;
import com.woozoo.quiz.learning.domain.QuestionResult;

import java.util.List;

// 고른 보기, 정답, 해설은 앱이 퀴즈 조회로 이미 받았다. 그래서 문제마다 맞았는지만 돌려준다.
// 점수는 저장하지 않고 문제별 결과에서 센다
public record SubmissionResponse(
        long submissionId,
        int correctCount,
        int totalCount,
        List<QuestionResultResponse> results) {

    public static SubmissionResponse from(SubmissionResult result) {
        List<QuestionResult> results = result.results();
        int correctCount = (int) results.stream().filter(QuestionResult::correct).count();

        return new SubmissionResponse(
                result.submissionId(),
                correctCount,
                results.size(),
                results.stream().map(QuestionResultResponse::from).toList());
    }

    public record QuestionResultResponse(long questionId, boolean correct) {

        static QuestionResultResponse from(QuestionResult result) {
            return new QuestionResultResponse(result.questionId(), result.correct());
        }
    }
}
