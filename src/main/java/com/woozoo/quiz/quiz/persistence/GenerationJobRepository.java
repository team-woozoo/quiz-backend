package com.woozoo.quiz.quiz.persistence;

import com.woozoo.quiz.global.error.ErrorCode;
import com.woozoo.quiz.quiz.domain.GenerationJob;
import com.woozoo.quiz.quiz.domain.GenerationStatus;
import com.woozoo.quiz.quiz.domain.NewGenerationJob;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

// 상태를 바꾸는 메서드는 이전 상태를 WHERE 에 넣는다. 같은 작업에 두 번 와도 DB 가 하나만 통과시킨다
@Repository
public class GenerationJobRepository {

    private final JdbcClient jdbcClient;

    public GenerationJobRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public long create(NewGenerationJob newGenerationJob) {
        return jdbcClient.sql("""
                INSERT INTO generation_job (user_id, material_id, kind, format, difficulty, status)
                VALUES (:userId, :materialId, :kind, :format, :difficulty, :status)
                RETURNING id
                """)
                .param("userId", newGenerationJob.userId())
                .param("materialId", newGenerationJob.materialId())
                .param("kind", newGenerationJob.kind().name())
                .param("format", newGenerationJob.format().name())
                .param("difficulty", newGenerationJob.difficulty().name())
                .param("status", GenerationStatus.PENDING.name())
                .query(Long.class)
                .single();
    }

    // false 면 이미 다른 곳에서 집었거나 끝난 작업이라 실행하지 않는다
    public boolean markProcessing(long jobId) {
        return jdbcClient.sql("""
                UPDATE generation_job
                SET status = 'PROCESSING', started_at = now()
                WHERE id = :jobId
                AND status = 'PENDING'
                """)
                .param("jobId", jobId)
                .update() == 1;
    }

    public boolean markDone(long jobId, long quizId) {
        return jdbcClient.sql("""
                UPDATE generation_job
                SET status = 'DONE', quiz_id = :quizId, finished_at = now()
                WHERE id = :jobId
                AND status = 'PROCESSING'
                """)
                .param("jobId", jobId)
                .param("quizId", quizId)
                .update() == 1;
    }

    // 시작도 못 하고 실패할 수 있어 PENDING 도 받는다. 끝난 작업은 덮어쓰지 않는다.
    // 메시지는 앱에 그대로 나갈 수 있어 AI 응답이나 강의 내용 대신 에러 코드의 고정 문구를 남긴다
    public boolean markFailed(long jobId, ErrorCode errorCode) {
        return jdbcClient.sql("""
                UPDATE generation_job
                SET status = 'FAILED', error_code = :errorCode, error_message = :errorMessage, finished_at = now()
                WHERE id = :jobId
                AND status IN ('PENDING', 'PROCESSING')
                """)
                .param("jobId", jobId)
                .param("errorCode", errorCode.name())
                .param("errorMessage", errorCode.getMessage())
                .update() == 1;
    }

    // 끝나지 않은 작업을 한꺼번에 실패로 바꾸고, 바꾼 개수를 돌려준다
    public int failAllUnfinished(ErrorCode errorCode) {
        return jdbcClient.sql("""
                UPDATE generation_job
                SET status = 'FAILED', error_code = :errorCode, error_message = :errorMessage, finished_at = now()
                WHERE status IN ('PENDING', 'PROCESSING')
                """)
                .param("errorCode", errorCode.name())
                .param("errorMessage", errorCode.getMessage())
                .update();
    }

    public Optional<GenerationJob> findById(long jobId) {
        return jdbcClient.sql("""
                SELECT id, user_id, material_id, kind, format, difficulty, status, quiz_id, error_code
                FROM generation_job
                WHERE id = :jobId
                """)
                .param("jobId", jobId)
                .query(GenerationJob.class)
                .optional();
    }
}
