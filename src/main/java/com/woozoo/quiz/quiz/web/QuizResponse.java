package com.woozoo.quiz.quiz.web;

import com.woozoo.quiz.quiz.application.QuizDetail;
import com.woozoo.quiz.quiz.domain.CognitiveType;
import com.woozoo.quiz.quiz.domain.Difficulty;
import com.woozoo.quiz.quiz.domain.QuizFormat;
import com.woozoo.quiz.quiz.domain.StoredOption;
import com.woozoo.quiz.quiz.domain.StoredQuestion;

import java.util.List;

public record QuizResponse(
        long quizId,
        Long materialId,
        QuizFormat format,
        Difficulty difficulty,
        List<QuestionResponse> questions) {

    public static QuizResponse from(QuizDetail detail) {
        return new QuizResponse(
                detail.quiz().id(),
                detail.quiz().materialId(),
                detail.quiz().format(),
                detail.quiz().difficulty(),
                detail.questions().stream().map(QuestionResponse::from).toList());
    }

    // sourcePageNo 는 근거가 원본 PDF 몇 쪽에 있는지다
    public record QuestionResponse(
            long questionId,
            int orderNo,
            String content,
            CognitiveType cognitiveType,
            String conceptName,
            String explanation,
            String sourceQuote,
            Integer sourcePageNo,
            List<OptionResponse> options) {

        static QuestionResponse from(QuizDetail.QuestionDetail detail) {
            StoredQuestion question = detail.question();
            return new QuestionResponse(
                    question.id(),
                    question.orderNo(),
                    question.content(),
                    question.cognitiveType(),
                    question.conceptName(),
                    question.explanation(),
                    question.sourceQuote(),
                    question.sourcePageNo(),
                    detail.options().stream().map(OptionResponse::from).toList());
        }
    }

    public record OptionResponse(long optionId, int orderNo, String content, boolean isCorrect) {

        static OptionResponse from(StoredOption option) {
            return new OptionResponse(option.id(), option.orderNo(), option.content(), option.isCorrect());
        }
    }
}
