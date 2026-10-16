package com.woozoo.quiz.quiz.application;

import com.woozoo.quiz.quiz.domain.StoredOption;
import com.woozoo.quiz.quiz.domain.StoredQuestion;
import com.woozoo.quiz.quiz.domain.StoredQuiz;

import java.util.List;

public record QuizDetail(StoredQuiz quiz, List<QuestionDetail> questions) {

    public record QuestionDetail(StoredQuestion question, List<StoredOption> options) {
    }
}
