package com.woozoo.quiz.learning.application;

import com.woozoo.quiz.learning.domain.SubmittedAnswer;

import java.util.List;

public record ReviewCommand(long userId, List<SubmittedAnswer> answers) {
}
