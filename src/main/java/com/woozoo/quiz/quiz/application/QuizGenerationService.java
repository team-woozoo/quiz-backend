package com.woozoo.quiz.quiz.application;

import com.woozoo.quiz.global.error.BusinessException;
import com.woozoo.quiz.global.error.ErrorCode;
import com.woozoo.quiz.material.persistence.MaterialRepository;
import com.woozoo.quiz.quiz.domain.GenerationJob;
import com.woozoo.quiz.quiz.domain.NewGenerationJob;
import com.woozoo.quiz.quiz.domain.QuizKind;
import com.woozoo.quiz.quiz.persistence.GenerationJobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

@Service
public class QuizGenerationService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final MaterialRepository materialRepository;
    private final GenerationJobRepository generationJobRepository;
    private final QuizGenerationWorker quizGenerationWorker;
    private final Clock clock;
    private final long dailyLimit;

    public QuizGenerationService(MaterialRepository materialRepository,
                                 GenerationJobRepository generationJobRepository,
                                 QuizGenerationWorker quizGenerationWorker,
                                 Clock clock,
                                 @Value("${quiz.generation.daily-limit}") long dailyLimit) {
        this.materialRepository = materialRepository;
        this.generationJobRepository = generationJobRepository;
        this.quizGenerationWorker = quizGenerationWorker;
        this.clock = clock;
        this.dailyLimit = dailyLimit;
    }

    // 세고 넣는 사이에 트랜잭션을 두지 않는다.
    // 같은 사용자의 동시 요청은 진행 중 작업 인덱스(V2)가 하나만 통과시켜서, 한도를 넘지 않는다.
    // 트랜잭션 안에서 작업을 넘기면 커밋 전에 백그라운드가 먼저 돌아 작업 행을 못 찾을 수 있다
    public long requestGeneration(GenerateQuizCommand command) {
        // 남의 자료도 없는 자료와 같은 응답을 줘서, 다른 사용자의 자료 id 가 있는지 알 수 없게 한다
        if (!materialRepository.existsByIdAndOwnerId(command.materialId(), command.userId())) {
            throw new BusinessException(ErrorCode.MATERIAL_NOT_FOUND);
        }
        if (!hasRemainingDailyQuota(command.userId())) {
            throw new BusinessException(ErrorCode.DAILY_GENERATION_LIMIT_EXCEEDED);
        }
        long jobId;
        try {
            jobId = generationJobRepository.create(new NewGenerationJob(
                    command.userId(), command.materialId(), QuizKind.NORMAL, command.format(), command.difficulty()));
        } catch (DuplicateKeyException e) {
            // generation_job 의 유니크 제약은 진행 중 작업 인덱스(V2) 하나뿐이라, 중복이면 진행 중인 작업이 있다는 뜻이다
            throw new BusinessException(ErrorCode.GENERATION_IN_PROGRESS, e);
        }
        quizGenerationWorker.generate(jobId);
        return jobId;
    }

    public GenerationJob getJob(long userId, long jobId) {
        return generationJobRepository.findById(jobId)
                .filter(job -> job.userId() == userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
    }

    // 주인을 확인하지 않는다. 이미 주인 확인을 거친 연결로 보낼 때만 쓰고, 요청을 받는 곳에서는 getJob 을 쓴다
    public Optional<GenerationJob> findJob(long jobId) {
        return generationJobRepository.findById(jobId);
    }

    private boolean hasRemainingDailyQuota(long userId) {
        Instant startOfDay = LocalDate.now(clock.withZone(SEOUL)).atStartOfDay(SEOUL).toInstant();

        return generationJobRepository.countByUserIdSince(userId, startOfDay) < dailyLimit;
    }
}
