package com.woozoo.quiz.quiz.domain;

public enum GenerationStatus {
    PENDING,
    PROCESSING,
    DONE,
    FAILED;

    public boolean isFinished() { return this == DONE || this == FAILED; }
}
