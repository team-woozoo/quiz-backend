package com.woozoo.quiz.quiz.persistence;

import com.woozoo.quiz.TestcontainersConfiguration;
import com.woozoo.quiz.auth.domain.NewUser;
import com.woozoo.quiz.auth.persistence.UserRepository;
import com.woozoo.quiz.global.error.ErrorCode;
import com.woozoo.quiz.material.domain.FileType;
import com.woozoo.quiz.material.domain.NewMaterial;
import com.woozoo.quiz.material.persistence.MaterialRepository;
import com.woozoo.quiz.quiz.domain.Difficulty;
import com.woozoo.quiz.quiz.domain.GenerationJob;
import com.woozoo.quiz.quiz.domain.GenerationStatus;
import com.woozoo.quiz.quiz.domain.NewGenerationJob;
import com.woozoo.quiz.quiz.domain.QuizFormat;
import com.woozoo.quiz.quiz.domain.QuizKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class GenerationJobRepositoryTest {

    @Autowired
    GenerationJobRepository generationJobRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    MaterialRepository materialRepository;
    @Autowired
    JdbcClient jdbcClient;

    long userId;
    long materialId;

    @BeforeEach
    void setup() {
        userId = userRepository.save(new NewUser("job@test.com", "1234", "홍길동"));
        materialId = materialRepository.save(new NewMaterial(userId, "네트워크 5강", "network.pdf", FileType.PDF));
    }

    private long createJob() {
        return generationJobRepository.create(
                new NewGenerationJob(userId, materialId, QuizKind.NORMAL, QuizFormat.OX, Difficulty.NORMAL));
    }

    // 퀴즈 저장소가 아직 없어 markDone 의 quiz_id 외래 키를 맞추려고 직접 넣는다
    private long insertQuiz() {
        return jdbcClient.sql("""
                        INSERT INTO quiz (user_id, material_id, kind, format, difficulty)
                        VALUES (:userId, :materialId, 'NORMAL', 'OX', 'NORMAL')
                        RETURNING id
                        """)
                .param("userId", userId)
                .param("materialId", materialId)
                .query(Long.class)
                .single();
    }

    private GenerationJob find(long jobId) {
        return generationJobRepository.findById(jobId).orElseThrow();
    }

    @Test
    void 만든_작업은_요청한_값과_PENDING_상태로_조회된다() {
        long jobId = createJob();

        GenerationJob job = find(jobId);

        assertThat(job.id()).isEqualTo(jobId);
        assertThat(job.userId()).isEqualTo(userId);
        assertThat(job.materialId()).isEqualTo(materialId);
        assertThat(job.kind()).isEqualTo(QuizKind.NORMAL);
        assertThat(job.format()).isEqualTo(QuizFormat.OX);
        assertThat(job.difficulty()).isEqualTo(Difficulty.NORMAL);
        assertThat(job.status()).isEqualTo(GenerationStatus.PENDING);
        assertThat(job.quizId()).isNull();
        assertThat(job.errorCode()).isNull();
    }

    @Test
    void 없는_작업은_빈값을_반환한다() {
        assertThat(generationJobRepository.findById(-1L)).isEmpty();
    }

    @Test
    void PENDING_작업은_한_번만_PROCESSING_으로_바뀐다() {
        long jobId = createJob();

        assertThat(generationJobRepository.markProcessing(jobId)).isTrue();
        assertThat(generationJobRepository.markProcessing(jobId)).isFalse();
        assertThat(find(jobId).status()).isEqualTo(GenerationStatus.PROCESSING);
    }

    @Test
    void PROCESSING_작업을_완료하면_퀴즈_아이디가_남는다() {
        long jobId = createJob();
        long quizId = insertQuiz();
        generationJobRepository.markProcessing(jobId);

        assertThat(generationJobRepository.markDone(jobId, quizId)).isTrue();

        GenerationJob job = find(jobId);
        assertThat(job.status()).isEqualTo(GenerationStatus.DONE);
        assertThat(job.quizId()).isEqualTo(quizId);
    }

    @Test
    void 시작하지_않은_작업은_완료할_수_없다() {
        long jobId = createJob();
        long quizId = insertQuiz();

        assertThat(generationJobRepository.markDone(jobId, quizId)).isFalse();
        assertThat(find(jobId).status()).isEqualTo(GenerationStatus.PENDING);
    }

    @Test
    void PENDING_이나_PROCESSING_작업은_실패로_바꾸고_에러_코드를_남긴다() {
        long pendingJobId = createJob();
        long processingJobId = createJob();
        generationJobRepository.markProcessing(processingJobId);

        assertThat(generationJobRepository.markFailed(pendingJobId, ErrorCode.INTERNAL_ERROR)).isTrue();
        assertThat(generationJobRepository.markFailed(processingJobId, ErrorCode.INTERNAL_ERROR)).isTrue();

        assertThat(find(pendingJobId).status()).isEqualTo(GenerationStatus.FAILED);
        assertThat(find(processingJobId).errorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR.name());
    }

    @Test
    void 끝난_작업은_실패로_덮어쓰지_않는다() {
        long jobId = createJob();
        long quizId = insertQuiz();
        generationJobRepository.markProcessing(jobId);
        generationJobRepository.markDone(jobId, quizId);

        assertThat(generationJobRepository.markFailed(jobId, ErrorCode.INTERNAL_ERROR)).isFalse();

        GenerationJob job = find(jobId);
        assertThat(job.status()).isEqualTo(GenerationStatus.DONE);
        assertThat(job.errorCode()).isNull();
    }

    @Test
    void 끝나지_않은_작업만_한꺼번에_실패로_바꾼다() {
        long pendingJobId = createJob();
        long processingJobId = createJob();
        long doneJobId = createJob();
        generationJobRepository.markProcessing(processingJobId);
        generationJobRepository.markProcessing(doneJobId);
        generationJobRepository.markDone(doneJobId, insertQuiz());

        // 다른 테스트가 남긴 행이 있을 수 있어 개수는 최소만 본다
        assertThat(generationJobRepository.failAllUnfinished(ErrorCode.GENERATION_FAILED)).isGreaterThanOrEqualTo(2);

        assertThat(find(pendingJobId).status()).isEqualTo(GenerationStatus.FAILED);
        assertThat(find(processingJobId).errorCode()).isEqualTo(ErrorCode.GENERATION_FAILED.name());
        assertThat(find(doneJobId).status()).isEqualTo(GenerationStatus.DONE);
    }

    @Test
    void 실패한_작업은_다시_시작하지_않는다() {
        long jobId = createJob();
        generationJobRepository.markFailed(jobId, ErrorCode.INTERNAL_ERROR);

        assertThat(generationJobRepository.markProcessing(jobId)).isFalse();
        assertThat(find(jobId).status()).isEqualTo(GenerationStatus.FAILED);
    }
}
