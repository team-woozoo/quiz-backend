package com.woozoo.quiz.quiz.port;

import com.woozoo.quiz.concept.domain.ConceptTag;

import java.util.List;

public record AssignedConcept(int no, String name, String description,
                              List<Integer> chunkSeqs, List<ConceptTag> tags, int questionCount) {
}
