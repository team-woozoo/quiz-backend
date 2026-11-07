package com.woozoo.quiz.quiz.port;

public class QuestionTypeJudgeException extends RuntimeException {
    public QuestionTypeJudgeException(String message, Throwable cause) {
        super(message, cause);
    }

    public QuestionTypeJudgeException(String message) {
        super(message);
    }
}
