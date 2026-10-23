package com.woozoo.quiz.learning.web;

import com.woozoo.quiz.learning.application.ReviewCommand;
import com.woozoo.quiz.learning.application.SubmitQuizCommand;
import com.woozoo.quiz.learning.domain.SubmittedAnswer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

public record SubmitQuizRequest (
        // 빈 제출을 받으면 모든 문제가 틀림으로 남아 오답 노트와 분석이 실제보다 나빠진다
        @NotEmpty @Valid
        List<AnswerRequest> answers){

    // long 이면 빠진 값이 0 으로 들어와 막을 수 없어 Long 으로 받는다
    public record AnswerRequest (@NotNull Long questionId,
                                  @NotNull Long optionId){}

    public SubmitQuizCommand toCommand(long userId, long quizId) {
        return new SubmitQuizCommand(userId, quizId, toAnswers());
    }

    // 오답 노트 복습도 같은 모양으로 답을 받는다
    public ReviewCommand toReviewCommand(long userId) {
        return new ReviewCommand(userId, toAnswers());
    }

    private List<SubmittedAnswer> toAnswers() {
        List<SubmittedAnswer> submittedAnswers = new ArrayList<>();
        for(AnswerRequest answerRequest : answers) {
            SubmittedAnswer submittedAnswer =
                    new SubmittedAnswer(answerRequest.questionId, answerRequest.optionId);
            submittedAnswers.add(submittedAnswer);
        }
        return submittedAnswers;
    }
}
