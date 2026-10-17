package com.woozoo.quiz.quiz.application;

import com.woozoo.quiz.SyncTaskExecutorConfiguration;
import com.woozoo.quiz.TestcontainersConfiguration;
import com.woozoo.quiz.auth.domain.NewUser;
import com.woozoo.quiz.auth.persistence.UserRepository;
import com.woozoo.quiz.global.error.ErrorCode;
import com.woozoo.quiz.material.domain.FileType;
import com.woozoo.quiz.material.domain.MaterialChunk;
import com.woozoo.quiz.material.domain.NewMaterial;
import com.woozoo.quiz.material.persistence.MaterialRepository;
import com.woozoo.quiz.quiz.domain.CognitiveType;
import com.woozoo.quiz.quiz.domain.Difficulty;
import com.woozoo.quiz.quiz.domain.GenerationJob;
import com.woozoo.quiz.quiz.domain.GenerationStatus;
import com.woozoo.quiz.quiz.domain.QuizFormat;
import com.woozoo.quiz.quiz.port.GeneratedConcept;
import com.woozoo.quiz.quiz.port.GeneratedOption;
import com.woozoo.quiz.quiz.port.GeneratedQuestion;
import com.woozoo.quiz.quiz.port.GeneratedQuiz;
import com.woozoo.quiz.quiz.port.QuizGenerationException;
import com.woozoo.quiz.quiz.port.QuizGenerationPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

// 생성기가 실패하거나 쓸 수 없는 결과를 줄 때, 작업이 알맞은 코드로 FAILED 가 되고 퀴즈는 남지 않는지 본다
@Import({TestcontainersConfiguration.class, SyncTaskExecutorConfiguration.class})
@SpringBootTest
@Transactional
@RecordApplicationEvents
class QuizGenerationFailureTest {

    @MockitoBean
    QuizGenerationPort quizGenerationPort;

    @Autowired
    QuizGenerationService quizGenerationService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    MaterialRepository materialRepository;
    @Autowired
    JdbcClient jdbcClient;
    @Autowired
    ApplicationEvents applicationEvents;

    long userId;
    long materialId;

    @BeforeEach
    void setup() {
        userId = userRepository.save(new NewUser("failure@test.com", "1234", "홍길동"));
        materialId = materialRepository.save(new NewMaterial(userId, "네트워크 5강", "05.pdf", FileType.PDF));
        materialRepository.saveChunks(materialId, List.of(new MaterialChunk(1, 1, "TCP는 연결 지향 프로토콜이다.")));
    }

    private GenerationJob requestAndGetJob() {
        long jobId = quizGenerationService.requestGeneration(
                new GenerateQuizCommand(userId, materialId, QuizFormat.OX, Difficulty.NORMAL));
        return quizGenerationService.getJob(userId, jobId);
    }

    private long quizCount() {
        return jdbcClient.sql("SELECT count(*) FROM quiz WHERE user_id = :userId")
                .param("userId", userId)
                .query(Long.class)
                .single();
    }

    @Test
    void 생성기_호출이_실패하면_GENERATION_FAILED_가_된다() {
        given(quizGenerationPort.generateQuiz(any()))
                .willThrow(new QuizGenerationException("호출 실패", new RuntimeException()));

        GenerationJob job = requestAndGetJob();

        assertThat(job.status()).isEqualTo(GenerationStatus.FAILED);
        assertThat(job.errorCode()).isEqualTo(ErrorCode.GENERATION_FAILED.name());
        assertThat(quizCount()).isZero();
    }

    @Test
    void 검증을_통과한_문제가_없으면_NO_VALID_QUESTIONS_가_된다() {
        // 인용문이 원문에 없어 검증에서 모두 버려진다
        GeneratedQuestion madeUp = new GeneratedQuestion("문제", "강의에 없는 문장", CognitiveType.REMEMBER, "해설",
                List.of(new GeneratedOption("O", true), new GeneratedOption("X", false)), 1);
        given(quizGenerationPort.generateQuiz(any())).willReturn(new GeneratedQuiz(List.of(
                new GeneratedConcept("TCP", "설명", List.of(1), List.of(madeUp)))));

        GenerationJob job = requestAndGetJob();

        assertThat(job.status()).isEqualTo(GenerationStatus.FAILED);
        assertThat(job.errorCode()).isEqualTo(ErrorCode.NO_VALID_QUESTIONS.name());
        assertThat(quizCount()).isZero();
    }

    @Test
    void 실패하면_PROCESSING_과_FAILED_를_한_번씩_알린다() {
        given(quizGenerationPort.generateQuiz(any()))
                .willThrow(new QuizGenerationException("호출 실패", new RuntimeException()));

        GenerationJob job = requestAndGetJob();

        assertThat(applicationEvents.stream(JobStatusChanged.class)
                .filter(event -> event.jobId() == job.id()))
                .hasSize(2);
    }

    @Test
    void 예상하지_못한_오류는_INTERNAL_ERROR_가_된다() {
        given(quizGenerationPort.generateQuiz(any())).willThrow(new IllegalStateException("버그"));

        GenerationJob job = requestAndGetJob();

        assertThat(job.status()).isEqualTo(GenerationStatus.FAILED);
        assertThat(job.errorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR.name());
    }
}
