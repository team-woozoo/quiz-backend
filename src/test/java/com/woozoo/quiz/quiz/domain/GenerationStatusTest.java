package com.woozoo.quiz.quiz.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenerationStatusTest {

    @Test
    void DONE_과_FAILED_만_끝난_상태다() {
        assertThat(GenerationStatus.DONE.isFinished()).isTrue();
        assertThat(GenerationStatus.FAILED.isFinished()).isTrue();
        assertThat(GenerationStatus.PENDING.isFinished()).isFalse();
        assertThat(GenerationStatus.PROCESSING.isFinished()).isFalse();
    }
}
