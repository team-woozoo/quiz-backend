package com.woozoo.quiz.concept.domain;

import java.util.List;

public record MaterialConcept(long id, String name, String description,
                              List<ConceptTag> tags, List<Integer> chunkSeqs, int questionCount) {
}
