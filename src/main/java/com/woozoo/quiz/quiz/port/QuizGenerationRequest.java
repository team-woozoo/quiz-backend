package com.woozoo.quiz.quiz.port;

import com.woozoo.quiz.quiz.domain.Difficulty;
import com.woozoo.quiz.quiz.domain.QuizFormat;

import java.util.List;

public record QuizGenerationRequest (
        List<SourceChunk> chunks, QuizFormat format,
        Difficulty difficulty, int questionCount,
        List<ExistingConcept> existingConcepts) {}
