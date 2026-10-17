package com.woozoo.quiz.quiz.application;

import com.woozoo.quiz.global.error.BusinessException;
import com.woozoo.quiz.global.error.ErrorCode;
import com.woozoo.quiz.material.persistence.MaterialRepository;
import com.woozoo.quiz.quiz.domain.GenerationJob;
import com.woozoo.quiz.quiz.domain.NewGenerationJob;
import com.woozoo.quiz.quiz.domain.QuizKind;
import com.woozoo.quiz.quiz.persistence.GenerationJobRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class QuizGenerationService {

    private final MaterialRepository materialRepository;
    private final GenerationJobRepository generationJobRepository;
    private final QuizGenerationWorker quizGenerationWorker;

    public QuizGenerationService(MaterialRepository materialRepository,
                                 GenerationJobRepository generationJobRepository,
                                 QuizGenerationWorker quizGenerationWorker) {
        this.materialRepository = materialRepository;
        this.generationJobRepository = generationJobRepository;
        this.quizGenerationWorker = quizGenerationWorker;
    }

    // 작업 생성은 INSERT 하나라 트랜잭션을 두지 않는다.
    // 트랜잭션 안에서 작업을 넘기면 커밋 전에 백그라운드가 먼저 돌아 작업 행을 못 찾을 수 있다
    public long requestGeneration(GenerateQuizCommand command) {
        // 남의 자료도 없는 자료와 같은 응답을 줘서, 다른 사용자의 자료 id 가 있는지 알 수 없게 한다
        if (!materialRepository.existsByIdAndOwnerId(command.materialId(), command.userId())) {
            throw new BusinessException(ErrorCode.MATERIAL_NOT_FOUND);
        }

        long jobId = generationJobRepository.create(new NewGenerationJob(
                command.userId(), command.materialId(), QuizKind.NORMAL, command.format(), command.difficulty()));
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
}
