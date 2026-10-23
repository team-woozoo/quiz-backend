package com.woozoo.quiz.quiz.web;

import com.woozoo.quiz.quiz.application.JobStatusChanged;
import com.woozoo.quiz.quiz.application.QuizGenerationService;
import com.woozoo.quiz.quiz.domain.Difficulty;
import com.woozoo.quiz.quiz.domain.GenerationJob;
import com.woozoo.quiz.quiz.domain.GenerationStatus;
import com.woozoo.quiz.quiz.domain.QuizFormat;
import com.woozoo.quiz.quiz.domain.QuizKind;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 리스너는 생성 스레드에서 돈다. 알림이 실패해도 예외가 생성 쪽으로 나가지 않는지 본다
class JobEventListenerTest {

    private final JobEmitterRegistry registry = new JobEmitterRegistry(Duration.ofMinutes(1));
    private final QuizGenerationService quizGenerationService = mock(QuizGenerationService.class);
    private final JobEventListener listener = new JobEventListener(registry, quizGenerationService);

    @Test
    void 구독자가_없으면_작업을_조회하지_않는다() {
        listener.sendJobStatus(new JobStatusChanged(1L));

        verify(quizGenerationService, never()).findJob(1L);
    }

    @Test
    void 작업_조회가_실패해도_예외를_밖으로_던지지_않는다() {
        registry.connect(1L);
        given(quizGenerationService.findJob(1L)).willThrow(new IllegalStateException("DB 연결 실패"));

        assertThatNoException().isThrownBy(() -> listener.sendJobStatus(new JobStatusChanged(1L)));
    }

    @Test
    void 작업이_없으면_보내지_않고_조용히_끝난다() {
        registry.connect(1L);
        given(quizGenerationService.findJob(1L)).willReturn(Optional.empty());

        assertThatNoException().isThrownBy(() -> listener.sendJobStatus(new JobStatusChanged(1L)));
    }

    @Test
    void 구독자가_있으면_작업을_조회해_보낸다() {
        registry.connect(1L);
        given(quizGenerationService.findJob(1L)).willReturn(Optional.of(new GenerationJob(
                1L, 10L, 20L, QuizKind.NORMAL, QuizFormat.OX, Difficulty.NORMAL,
                GenerationStatus.PROCESSING, null, null, null)));

        assertThatNoException().isThrownBy(() -> listener.sendJobStatus(new JobStatusChanged(1L)));
        verify(quizGenerationService).findJob(1L);
    }
}
