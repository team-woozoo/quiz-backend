package com.woozoo.quiz.quiz.application;

import com.woozoo.quiz.concept.persistence.MaterialConceptRepository;
import com.woozoo.quiz.global.error.BusinessException;
import com.woozoo.quiz.global.error.ErrorCode;
import com.woozoo.quiz.material.domain.StoredChunk;
import com.woozoo.quiz.material.persistence.MaterialRepository;
import com.woozoo.quiz.quiz.domain.GenerationJob;
import com.woozoo.quiz.quiz.persistence.GenerationJobRepository;
import com.woozoo.quiz.quiz.port.ExistingConcept;
import com.woozoo.quiz.quiz.port.GeneratedQuiz;
import com.woozoo.quiz.quiz.port.QuizGenerationException;
import com.woozoo.quiz.quiz.port.QuizGenerationPort;
import com.woozoo.quiz.quiz.port.QuizGenerationRequest;
import com.woozoo.quiz.quiz.port.SourceChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// @Async 는 같은 클래스 안에서 부르면 프록시를 거치지 않아 동기로 돈다. 그래서 서비스와 다른 컴포넌트로 둔다
@Slf4j
@Component
public class QuizGenerationWorker {

    private final GenerationJobRepository generationJobRepository;
    private final MaterialRepository materialRepository;
    private final MaterialConceptRepository materialConceptRepository;
    private final QuizGenerationPort quizGenerationPort;
    private final GeneratedQuizValidator generatedQuizValidator;
    private final QuizWriter quizWriter;
    private final int questionCount;
    private final ApplicationEventPublisher eventPublisher;

    public QuizGenerationWorker(GenerationJobRepository generationJobRepository,
                                MaterialRepository materialRepository,
                                MaterialConceptRepository materialConceptRepository,
                                QuizGenerationPort quizGenerationPort,
                                GeneratedQuizValidator generatedQuizValidator,
                                QuizWriter quizWriter,
                                @Value("${quiz.generation.question-count}") int questionCount,
                                ApplicationEventPublisher eventPublisher) {
        this.generationJobRepository = generationJobRepository;
        this.materialRepository = materialRepository;
        this.materialConceptRepository = materialConceptRepository;
        this.quizGenerationPort = quizGenerationPort;
        this.generatedQuizValidator = generatedQuizValidator;
        this.quizWriter = quizWriter;
        this.questionCount = questionCount;
        this.eventPublisher = eventPublisher;
    }

    // 트랜잭션은 QuizWriter 의 저장에만 있다. 수십 초 걸리는 AI 호출 동안 DB 연결을 잡아 두지 않으려는 것이다
    @Async
    public void generate(long jobId) {
        if (!generationJobRepository.markProcessing(jobId)) {
            return;
        }
        eventPublisher.publishEvent(new JobStatusChanged(jobId));
        try {
            run(jobId);
            eventPublisher.publishEvent(new JobStatusChanged(jobId));
        } catch (QuizGenerationException e) {
            log.warn("퀴즈 생성 호출 실패: jobId={}", jobId, e);
            fail(jobId, ErrorCode.GENERATION_FAILED);
        } catch (BusinessException e) {
            fail(jobId, e.getErrorCode());
        } catch (RuntimeException e) {
            log.error("퀴즈 생성 중 예상하지 못한 오류: jobId={}", jobId, e);
            fail(jobId, ErrorCode.INTERNAL_ERROR);
        }
    }

    private void run(long jobId) {
        GenerationJob job = generationJobRepository.findById(jobId).orElseThrow();
        List<StoredChunk> chunks = materialRepository.findChunks(job.materialId());

        QuizGenerationRequest request = new QuizGenerationRequest(
                chunks.stream().map(chunk -> new SourceChunk(chunk.seq(), chunk.content())).toList(),
                job.format(),
                job.difficulty(),
                questionCount,
                findExistingConcepts(job.materialId()));

        GeneratedQuiz generated = quizGenerationPort.generateQuiz(request);
        GeneratedQuiz validated = generatedQuizValidator.validate(request, generated);

        if (validated.concepts().isEmpty()) {
            throw new BusinessException(ErrorCode.NO_VALID_QUESTIONS);
        }

        // AI 는 청크를 seq 로 가리키고 DB 는 id 로 잇는다
        Map<Integer, Long> chunkIdBySeq = chunks.stream()
                .collect(Collectors.toMap(StoredChunk::seq, StoredChunk::id));
        quizWriter.save(job, validated, chunkIdBySeq);
    }

    private List<ExistingConcept> findExistingConcepts(long materialId) {
        return materialConceptRepository.findByMaterialId(materialId).stream()
                .map(concept -> new ExistingConcept(concept.name(), concept.description()))
                .toList();
    }

    private void fail(long jobId, ErrorCode errorCode) {
        generationJobRepository.markFailed(jobId, errorCode);
        eventPublisher.publishEvent(new JobStatusChanged(jobId));
    }
}
