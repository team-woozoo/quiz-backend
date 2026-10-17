package com.woozoo.quiz.learning.application;

import com.woozoo.quiz.learning.domain.SubmittedAnswer;

import java.util.List;

public record SubmitQuizCommand (long userId, long quizId, List<SubmittedAnswer> answers) {
}
