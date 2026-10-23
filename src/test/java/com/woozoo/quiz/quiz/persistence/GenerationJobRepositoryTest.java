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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    // 사용자마다 진행 중인 작업은 하나뿐이라, 진행 중인 작업이 여럿 필요하면 사용자를 나눠 만든다
    private long createJobOfNewUser(String email) {
        long otherUserId = userRepository.save(new NewUser(email, "1234", "다른 사용자"));
        long otherMaterialId = materialRepository.save(
                new NewMaterial(otherUserId, "운영체제 3강", "os.pdf", FileType.PDF));
        return generationJobRepository.create(
                new NewGenerationJob(otherUserId, otherMaterialId, QuizKind.NORMAL, QuizFormat.OX, Difficulty.NORMAL));
    }

    private void moveCreatedAt(long jobId, Duration ago) {
        jdbcClient.sql("UPDATE generation_job SET created_at = now() - :seconds * interval '1 second' WHERE id = :id")
                .param("seconds", ago.toSeconds())
                .param("id", jobId)
                .update();
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
        assertThat(job.userConceptId()).isNull();
    }

    // 약점 작업은 자료 없이 겨냥한 대표 개념을 들고 있다
    @Test
    void 약점_작업은_자료_없이_대표_개념과_함께_조회된다() {
        long userConceptId = jdbcClient.sql("""
                        INSERT INTO user_concept (user_id, name, description) VALUES (:userId, 'TCP', '설명') RETURNING id
                        """)
                .param("userId", userId).query(Long.class).single();

        long jobId = generationJobRepository.create(new NewGenerationJob(
                userId, null, QuizKind.WEAKNESS, QuizFormat.OX, Difficulty.NORMAL, userConceptId));

        GenerationJob job = find(jobId);
        assertThat(job.kind()).isEqualTo(QuizKind.WEAKNESS);
        assertThat(job.materialId()).isNull();
        assertThat(job.userConceptId()).isEqualTo(userConceptId);
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
        long processingJobId = createJobOfNewUser("processing@test.com");
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
        long processingJobId = createJobOfNewUser("processing@test.com");
        long doneJobId = createJobOfNewUser("done@test.com");
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

    // 아래 두 테스트는 INSERT 가 실패하면 트랜잭션이 깨져 그 뒤로 쿼리를 더 보내지 않는다
    @Test
    void PENDING_작업이_있으면_같은_사용자는_작업을_더_만들_수_없다() {
        createJob();

        assertThatThrownBy(this::createJob).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void PROCESSING_작업이_있으면_같은_사용자는_작업을_더_만들_수_없다() {
        generationJobRepository.markProcessing(createJob());

        assertThatThrownBy(this::createJob).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void 앞_작업이_끝나면_같은_사용자가_다시_만들_수_있다() {
        long failedJobId = createJob();
        generationJobRepository.markFailed(failedJobId, ErrorCode.GENERATION_FAILED);

        long doneJobId = createJob();
        generationJobRepository.markProcessing(doneJobId);
        generationJobRepository.markDone(doneJobId, insertQuiz());

        long nextJobId = createJob();

        assertThat(find(nextJobId).status()).isEqualTo(GenerationStatus.PENDING);
    }

    @Test
    void 다른_사용자의_진행_중인_작업은_막지_않는다() {
        long otherJobId = createJobOfNewUser("other@test.com");
        long jobId = createJob();

        assertThat(find(otherJobId).status()).isEqualTo(GenerationStatus.PENDING);
        assertThat(find(jobId).status()).isEqualTo(GenerationStatus.PENDING);
    }

    @Test
    void 기준_시각_이후에_만든_내_작업만_센다() {
        long oldJobId = createJob();
        generationJobRepository.markFailed(oldJobId, ErrorCode.GENERATION_FAILED);
        moveCreatedAt(oldJobId, Duration.ofDays(2));
        long recentJobId = createJob();
        generationJobRepository.markFailed(recentJobId, ErrorCode.GENERATION_FAILED);
        createJob();
        createJobOfNewUser("other@test.com");

        long count = generationJobRepository.countByUserIdSince(userId, Instant.now().minus(Duration.ofDays(1)));

        // 이틀 전 작업과 남의 작업은 빼고, 실패한 작업과 진행 중인 작업은 센다
        assertThat(count).isEqualTo(2);
    }
}
