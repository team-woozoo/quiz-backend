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
import com.woozoo.quiz.material.domain.StoredChunk;
import com.woozoo.quiz.material.persistence.MaterialRepository;
import com.woozoo.quiz.quiz.domain.Difficulty;
import com.woozoo.quiz.quiz.domain.GenerationJob;
import com.woozoo.quiz.quiz.domain.GenerationStatus;
import com.woozoo.quiz.quiz.domain.QuizFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 가짜 생성기로 요청부터 저장, DONE 까지 한 흐름을 본다. 백그라운드 작업은 같은 스레드에서 바로 돈다
@Import({TestcontainersConfiguration.class, SyncTaskExecutorConfiguration.class})
@SpringBootTest
@Transactional
@RecordApplicationEvents
class QuizGenerationServiceTest {

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
    long otherUserId;
    long materialId;

    @BeforeEach
    void setup() {
        userId = userRepository.save(new NewUser("owner@test.com", "1234", "주인"));
        otherUserId = userRepository.save(new NewUser("other@test.com", "1234", "남"));
        materialId = materialRepository.save(new NewMaterial(userId, "네트워크 5강", "05.pdf", FileType.PDF, 30));
        materialRepository.saveChunks(materialId, List.of(
                new MaterialChunk(1, 1, "TCP는 연결 지향 프로토콜이다.\n신뢰성 있는 전송을 보장한다."),
                new MaterialChunk(2, 2, "UDP는 비연결형 프로토콜이다.\n순서를 보장하지 않는다."),
                new MaterialChunk(3, 3, "IP는 패킷을 목적지까지 전달한다.\n경로를 고른다.")));
    }

    private long request(QuizFormat format) {
        return quizGenerationService.requestGeneration(
                new GenerateQuizCommand(userId, materialId, format, Difficulty.NORMAL));
    }

    private long count(String sql, long id) {
        return jdbcClient.sql(sql).param("id", id).query(Long.class).single();
    }

    @Test
    void 생성을_요청하면_퀴즈를_저장하고_작업이_DONE_이_된다() {
        long jobId = request(QuizFormat.OX);

        GenerationJob job = quizGenerationService.getJob(userId, jobId);
        assertThat(job.status()).isEqualTo(GenerationStatus.DONE);
        assertThat(job.quizId()).isNotNull();

        // 가짜 생성기는 청크마다 개념 하나, 문제 하나를 만든다
        long quizId = job.quizId();
        assertThat(count("SELECT count(*) FROM question WHERE quiz_id = :id", quizId)).isEqualTo(3);
        assertThat(count("""
                SELECT count(*) FROM question_option o JOIN question q ON q.id = o.question_id
                WHERE q.quiz_id = :id
                """, quizId)).isEqualTo(6);
        assertThat(count("SELECT count(*) FROM material_concept WHERE material_id = :id", materialId)).isEqualTo(3);
        assertThat(count("""
                SELECT count(*) FROM concept_chunk cc JOIN material_concept mc ON mc.id = cc.material_concept_id
                WHERE mc.material_id = :id
                """, materialId)).isEqualTo(3);
    }

    // 구독 중인 앱이 생성 중과 완료를 한 번씩 받으려면 상태가 바뀔 때마다 알려야 한다
    @Test
    void 생성이_끝나면_PROCESSING_과_DONE_을_한_번씩_알린다() {
        long jobId = request(QuizFormat.OX);

        assertThat(applicationEvents.stream(JobStatusChanged.class)
                .filter(event -> event.jobId() == jobId))
                .hasSize(2);
    }

    @Test
    void 문제는_1번부터_차례로_저장되고_근거_청크가_seq_에_맞는_청크로_이어진다() {
        long quizId = quizGenerationService.getJob(userId, request(QuizFormat.OX)).quizId();

        List<Long> orderNos = jdbcClient.sql("SELECT order_no FROM question WHERE quiz_id = :id ORDER BY order_no")
                .param("id", quizId).query(Long.class).list();
        List<Long> sourceChunkIds = jdbcClient.sql("SELECT source_chunk_id FROM question WHERE quiz_id = :id ORDER BY order_no")
                .param("id", quizId).query(Long.class).list();

        assertThat(orderNos).containsExactly(1L, 2L, 3L);
        assertThat(sourceChunkIds).containsExactlyElementsOf(
                materialRepository.findChunks(materialId).stream().map(StoredChunk::id).toList());
    }

    @Test
    void 같은_자료로_다시_만들면_기존_개념을_다시_쓴다() {
        long firstQuizId = quizGenerationService.getJob(userId, request(QuizFormat.OX)).quizId();
        long secondQuizId = quizGenerationService.getJob(userId, request(QuizFormat.MULTIPLE_CHOICE)).quizId();

        assertThat(secondQuizId).isNotEqualTo(firstQuizId);
        assertThat(count("SELECT count(*) FROM material_concept WHERE material_id = :id", materialId)).isEqualTo(3);
        assertThat(count("""
                SELECT count(*) FROM concept_chunk cc JOIN material_concept mc ON mc.id = cc.material_concept_id
                WHERE mc.material_id = :id
                """, materialId)).isEqualTo(3);
    }

    @Test
    void 객관식은_문제마다_보기_4개를_저장한다() {
        long quizId = quizGenerationService.getJob(userId, request(QuizFormat.MULTIPLE_CHOICE)).quizId();

        assertThat(count("""
                SELECT count(*) FROM question_option o JOIN question q ON q.id = o.question_id
                WHERE q.quiz_id = :id
                """, quizId)).isEqualTo(12);
    }

    @Test
    void 남의_자료로는_생성을_요청할_수_없고_작업도_만들지_않는다() {
        GenerateQuizCommand command = new GenerateQuizCommand(otherUserId, materialId, QuizFormat.OX, Difficulty.NORMAL);

        assertThatThrownBy(() -> quizGenerationService.requestGeneration(command))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MATERIAL_NOT_FOUND);
        assertThat(count("SELECT count(*) FROM generation_job WHERE user_id = :id", otherUserId)).isZero();
    }

    @Test
    void 없는_자료로는_생성을_요청할_수_없다() {
        GenerateQuizCommand command = new GenerateQuizCommand(userId, -1L, QuizFormat.OX, Difficulty.NORMAL);

        assertThatThrownBy(() -> quizGenerationService.requestGeneration(command))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MATERIAL_NOT_FOUND);
    }

    @Test
    void 남의_작업은_없는_작업처럼_조회되지_않는다() {
        long jobId = request(QuizFormat.OX);

        assertThatThrownBy(() -> quizGenerationService.getJob(otherUserId, jobId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.JOB_NOT_FOUND);
    }
}
