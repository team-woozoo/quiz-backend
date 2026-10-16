package com.woozoo.quiz.quiz.port;

import java.util.List;

public record GeneratedConcept (String name, String description,
                                List<Integer> chunkSeqs, List<GeneratedQuestion> questions) {
}
