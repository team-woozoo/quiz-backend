package com.woozoo.quiz.quiz.domain;

public enum QuizFormat {
    MULTIPLE_CHOICE(4),
    OX(2);

    private final int optionCount;

    QuizFormat(int optionCount) {
        this.optionCount = optionCount;
    }

    public int optionCount() {
        return optionCount;
    }
}
