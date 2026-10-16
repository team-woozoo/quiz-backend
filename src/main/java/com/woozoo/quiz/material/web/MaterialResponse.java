package com.woozoo.quiz.material.web;

import com.woozoo.quiz.material.domain.MaterialSummary;

import java.time.Instant;

public record MaterialResponse(long materialId, String title, String originalFilename, int pageCount, Instant createdAt) {

    public static MaterialResponse from(MaterialSummary summary) {
        return new MaterialResponse(summary.id(), summary.title(), summary.originalFilename(),
                summary.pageCount(), summary.createdAt());
    }
}
