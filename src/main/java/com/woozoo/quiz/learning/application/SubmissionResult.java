package com.woozoo.quiz.learning.application;

import com.woozoo.quiz.learning.domain.QuestionResult;

import java.util.List;

public record SubmissionResult(long submissionId, List<QuestionResult> results) {}
