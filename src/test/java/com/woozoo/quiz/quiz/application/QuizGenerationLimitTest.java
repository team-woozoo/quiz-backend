package com.woozoo.quiz.quiz.application;

import com.woozoo.quiz.SyncTaskExecutorConfiguration;
import com.woozoo.quiz.TestcontainersConfiguration;
import com.woozoo.quiz.auth.domain.NewUser;
import com.woozoo.quiz.auth.persistence.UserRepository;
import com.woozoo.quiz.global.error.BusinessException;
import com.woozoo.quiz.global.error.ErrorCode;
import com.woozoo.quiz.material.domain.FileType;
import com.woozoo.quiz.material.domain.MaterialChunk;
import com.woozoo.quiz.material.domain.NewMaterial;
import com.woozoo.quiz.material.persistence.MaterialRepository;
import com.woozoo.quiz.quiz.domain.Difficulty;
import com.woozoo.quiz.quiz.domain.GenerationStatus;
import com.woozoo.quiz.quiz.domain.QuizFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 지금을 한국 시간 10월 20일 00:30 으로 고정하고, 하루 한도가 한국 자정부터 세는지와 동시 실행 제한을 본다
@Import({TestcontainersConfiguration.class, SyncTaskExecutorConfiguration.class})
@SpringBootTest
@Transactional
class QuizGenerationLimitTest {

    // 한국 2026-10-20 00:30. UTC 로는 아직 10월 19일이라, UTC 자정으로 세면 아래 경계 테스트가 실패한다
    static final Instant NOW = Instant.parse("2026-10-19T15:30:00Z");
    // 한국 2026-10-20 00:00
    static final Instant KST_MIDNIGHT = Instant.parse("2026-10-19T15:00:00Z");

    @TestBean
    Clock clock;

    static Clock clock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    @Autowired
    QuizGenerationService quizGenerationService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    MaterialRepository materialRepository;
    @Autowired
    JdbcClient jdbcClient;
    @Value("${quiz.generation.daily-limit}")
    int dailyLimit;

    long userId;
    long materialId;

    @BeforeEach
    void setup() {
        userId = userRepository.save(new NewUser("limit@test.com", "1234", "홍길동"));
        materialId = materialRepository.save(new NewMaterial(userId, "네트워크 5강", "05.pdf", FileType.PDF));
        materialRepository.saveChunks(materialId, List.of(
                new MaterialChunk(1, 1, "TCP는 연결 지향 프로토콜이다."),
                new MaterialChunk(2, 2, "UDP는 비연결형 프로토콜이다.")));
    }

    private long request() {
        return quizGenerationService.requestGeneration(
                new GenerateQuizCommand(userId, materialId, QuizFormat.OX, Difficulty.NORMAL));
    }

    // 한도를 채우려고 요청을 반복하지 않고, 정해진 시각에 만든 작업 행을 바로 넣는다
    private void insertJobs(int count, String status, Instant createdAt) {
        for (int i = 0; i < count; i++) {
            jdbcClient.sql("""
                            INSERT INTO generation_job (user_id, material_id, kind, format, difficulty, status, created_at)
                            VALUES (:userId, :materialId, 'NORMAL', 'OX', 'NORMAL', :status, :createdAt)
                            """)
                    .param("userId", userId)
                    .param("materialId", materialId)
                    .param("status", status)
                    .param("createdAt", createdAt.atOffset(ZoneOffset.UTC))
                    .update();
        }
    }

    private long countJobs() {
        return jdbcClient.sql("SELECT count(*) FROM generation_job WHERE user_id = :userId")
                .param("userId", userId)
                .query(Long.class)
                .single();
    }

    private void assertRejectedWith(ErrorCode errorCode) {
        assertThatThrownBy(this::request)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(errorCode);
    }

    @Test
    void 한도보다_하나_적게_만들었으면_요청할_수_있다() {
        insertJobs(dailyLimit - 1, "DONE", KST_MIDNIGHT);

        long jobId = request();

        assertThat(quizGenerationService.getJob(userId, jobId).status()).isEqualTo(GenerationStatus.DONE);
    }

    @Test
    void 오늘_한도만큼_만들었으면_DAILY_GENERATION_LIMIT_EXCEEDED_이고_작업을_만들지_않는다() {
        insertJobs(dailyLimit, "DONE", KST_MIDNIGHT);

        assertRejectedWith(ErrorCode.DAILY_GENERATION_LIMIT_EXCEEDED);
        assertThat(countJobs()).isEqualTo(dailyLimit);
    }

    // 실패한 작업도 AI 를 이미 불렀을 수 있어 한도에 센다
    @Test
    void 실패한_작업도_한도에_센다() {
        insertJobs(dailyLimit, "FAILED", KST_MIDNIGHT);

        assertRejectedWith(ErrorCode.DAILY_GENERATION_LIMIT_EXCEEDED);
    }

    @Test
    void 한국_자정_전에_만든_작업은_오늘_한도에_세지_않는다() {
        // 한국 10월 19일 23:59:59. UTC 날짜로는 10월 19일이라 UTC 자정 기준이면 오늘로 셌을 작업이다
        insertJobs(dailyLimit, "DONE", KST_MIDNIGHT.minusSeconds(1));

        long jobId = request();

        assertThat(quizGenerationService.getJob(userId, jobId).status()).isEqualTo(GenerationStatus.DONE);
    }

    // INSERT 가 막히면 트랜잭션이 깨져 그 뒤로 쿼리를 더 보내지 않는다
    @Test
    void 진행_중인_작업이_있으면_GENERATION_IN_PROGRESS_다() {
        insertJobs(1, "PROCESSING", KST_MIDNIGHT);

        assertRejectedWith(ErrorCode.GENERATION_IN_PROGRESS);
    }

    // 둘 다 걸리면 한도를 먼저 본다. 한도가 찬 사용자는 앞 작업이 끝나도 어차피 못 만든다
    @Test
    void 한도가_찼고_진행_중인_작업도_있으면_한도_초과로_알린다() {
        insertJobs(dailyLimit - 1, "DONE", KST_MIDNIGHT);
        insertJobs(1, "PENDING", KST_MIDNIGHT);

        assertRejectedWith(ErrorCode.DAILY_GENERATION_LIMIT_EXCEEDED);
    }
}
