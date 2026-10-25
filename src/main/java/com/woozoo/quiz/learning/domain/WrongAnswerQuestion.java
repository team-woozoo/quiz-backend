package com.woozoo.quiz.learning.domain;

import com.woozoo.quiz.quiz.domain.CognitiveType;
import com.woozoo.quiz.quiz.domain.QuizFormat;

import java.time.Instant;

// 오답 노트의 한 문제. 다시 풀고 근거를 볼 수 있게 문제 내용과 해설, 근거, 개념, 자료를 같이 담는다.
// 자료는 퀴즈가 아니라 개념에서 가져와, 자료 없이 만든 약점 퀴즈의 문제도 자료를 보여 줄 수 있다
public record WrongAnswerQuestion(
        long questionId,
        long quizId,
        QuizFormat format,
        String content,
        CognitiveType cognitiveType,
        String explanation,
        String sourceQuote,
        Integer sourcePageNo,
        int totalPageCount,
        String conceptName,
        long materialId,
        String materialTitle,
        int wrongCount,
        Instant lastWrongAt) {
}
