package com.woozoo.quiz.learning.web;

import com.woozoo.quiz.learning.application.WrongAnswerNote;
import com.woozoo.quiz.learning.domain.WrongAnswerQuestion;
import com.woozoo.quiz.quiz.domain.CognitiveType;
import com.woozoo.quiz.quiz.domain.QuizFormat;
import com.woozoo.quiz.quiz.domain.StoredOption;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

// 앱이 오답 노트에서 바로 채점하고 해설과 근거를 보여 줄 수 있게, 퀴즈 조회의 문제와 같은 값에 오답 기록을 더한다
public record WrongAnswerResponse(
        long questionId,
        long quizId,
        QuizFormat format,
        String content,
        CognitiveType cognitiveType,
        String conceptName,
        @Schema(description = "개념이 나온 자료. 자료 없이 만든 약점 퀴즈의 문제도 원래 자료가 나온다")
        long materialId,
        String materialTitle,
        String explanation,
        String sourceQuote,
        @Schema(nullable = true, description = "근거가 있는 PDF 페이지 번호(1부터)")
        Integer sourcePageNo,
        @Schema(description = "orderNo 순서. 정답 보기는 isCorrect 가 true 다")
        List<OptionResponse> options,
        @Schema(description = "이 문제를 틀린 횟수. 퀴즈와 오답 노트에서 틀린 것을 모두 센다")
        int wrongCount,
        @Schema(description = "가장 최근에 틀린 시각")
        Instant lastWrongAt) {

    public static WrongAnswerResponse from(WrongAnswerNote note) {
        WrongAnswerQuestion question = note.question();
        return new WrongAnswerResponse(
                question.questionId(),
                question.quizId(),
                question.format(),
                question.content(),
                question.cognitiveType(),
                question.conceptName(),
                question.materialId(),
                question.materialTitle(),
                question.explanation(),
                question.sourceQuote(),
                question.sourcePageNo(),
                note.options().stream().map(OptionResponse::from).toList(),
                question.wrongCount(),
                question.lastWrongAt());
    }

    public record OptionResponse(long optionId, int orderNo, String content, boolean isCorrect) {

        static OptionResponse from(StoredOption option) {
            return new OptionResponse(option.id(), option.orderNo(), option.content(), option.isCorrect());
        }
    }
}
