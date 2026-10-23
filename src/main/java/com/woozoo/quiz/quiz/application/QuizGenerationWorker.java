package com.woozoo.quiz.quiz.application;

import com.woozoo.quiz.concept.domain.ConceptPage;
import com.woozoo.quiz.concept.domain.UserConcept;
import com.woozoo.quiz.concept.persistence.MaterialConceptRepository;
import com.woozoo.quiz.concept.persistence.UserConceptRepository;
import com.woozoo.quiz.global.error.BusinessException;
import com.woozoo.quiz.global.error.ErrorCode;
import com.woozoo.quiz.material.domain.StoredChunk;
import com.woozoo.quiz.material.persistence.MaterialRepository;
import com.woozoo.quiz.quiz.domain.CognitiveType;
import com.woozoo.quiz.quiz.domain.GenerationJob;
import com.woozoo.quiz.quiz.domain.QuizKind;
import com.woozoo.quiz.quiz.persistence.GenerationJobRepository;
import com.woozoo.quiz.quiz.persistence.QuizRepository;
import com.woozoo.quiz.quiz.port.ConceptFocus;
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

import java.util.ArrayList;
import java.util.HashMap;
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
    private final UserConceptRepository userConceptRepository;
    private final QuizRepository quizRepository;
    private final QuizGenerationPort quizGenerationPort;
    private final GeneratedQuizValidator generatedQuizValidator;
    private final QuizWriter quizWriter;
    private final int questionCount;
    private final Map<CognitiveType, Integer> cognitiveTypeCounts;
    private final int weaknessQuestionCount;
    private final Map<CognitiveType, Integer> weaknessCognitiveTypeCounts;
    private final int askedQuestionLimit;
    private final int weaknessQuestionsPerPage;
    private final ApplicationEventPublisher eventPublisher;

    public QuizGenerationWorker(GenerationJobRepository generationJobRepository,
                                MaterialRepository materialRepository,
                                MaterialConceptRepository materialConceptRepository,
                                UserConceptRepository userConceptRepository,
                                QuizRepository quizRepository,
                                QuizGenerationPort quizGenerationPort,
                                GeneratedQuizValidator generatedQuizValidator,
                                QuizWriter quizWriter,
                                @Value("${quiz.generation.question-count}") int questionCount,
                                @Value("${quiz.generation.cognitive-mix}") String cognitiveMix,
                                @Value("${quiz.generation.weakness.question-count}") int weaknessQuestionCount,
                                @Value("${quiz.generation.weakness.cognitive-mix}") String weaknessCognitiveMix,
                                @Value("${quiz.generation.weakness.asked-question-limit}") int askedQuestionLimit,
                                @Value("${quiz.generation.weakness.questions-per-page}") int weaknessQuestionsPerPage,
                                ApplicationEventPublisher eventPublisher) {
        this.generationJobRepository = generationJobRepository;
        this.materialRepository = materialRepository;
        this.materialConceptRepository = materialConceptRepository;
        this.userConceptRepository = userConceptRepository;
        this.quizRepository = quizRepository;
        this.quizGenerationPort = quizGenerationPort;
        this.generatedQuizValidator = generatedQuizValidator;
        this.quizWriter = quizWriter;
        this.questionCount = questionCount;
        this.cognitiveTypeCounts = CognitiveMix.parse(cognitiveMix, questionCount);
        this.weaknessQuestionCount = weaknessQuestionCount;
        this.weaknessCognitiveTypeCounts = CognitiveMix.parse(weaknessCognitiveMix, weaknessQuestionCount);
        this.askedQuestionLimit = askedQuestionLimit;
        this.weaknessQuestionsPerPage = weaknessQuestionsPerPage;
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
        if (job.kind() == QuizKind.WEAKNESS) {
            runWeakness(job);
            return;
        }
        List<StoredChunk> chunks = materialRepository.findChunks(job.materialId());

        QuizGenerationRequest request = new QuizGenerationRequest(
                chunks.stream().map(chunk -> new SourceChunk(chunk.seq(), chunk.content())).toList(),
                job.format(),
                job.difficulty(),
                questionCount,
                cognitiveTypeCounts,
                findExistingConcepts(job.materialId()));

        GeneratedQuiz validated = generateAndValidate(request);

        // AI 는 청크를 seq 로 가리키고 DB 는 id 로 잇는다
        Map<Integer, Long> chunkIdBySeq = chunks.stream()
                .collect(Collectors.toMap(StoredChunk::seq, StoredChunk::id));
        quizWriter.save(job, validated, chunkIdBySeq);
    }

    // 대표 개념이 나온 페이지만 보내 그 개념으로 문제를 낸다.
    // 대표 개념은 여러 자료의 개념을 묶을 수 있어, 자료마다 따로인 seq 대신 보내는 순서대로 번호를 새로 매긴다.
    // 문제는 근거 페이지를 가진 원래 자료 개념에 붙여, 약점 퀴즈의 결과도 그 개념의 정답률에 들어가게 한다
    private void runWeakness(GenerationJob job) {
        UserConcept concept = userConceptRepository.findById(job.userConceptId()).orElseThrow();
        List<ConceptPage> pages = userConceptRepository.findPages(concept.id());
        if (pages.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_VALID_QUESTIONS);
        }

        List<SourceChunk> chunks = new ArrayList<>();
        Map<Integer, Long> chunkIdBySeq = new HashMap<>();
        // 한 페이지에 같은 대표 개념의 자료 개념이 둘 이어져 있으면 먼저 나온 개념에 붙인다
        Map<Long, Long> materialConceptIdByChunkId = new HashMap<>();
        for (ConceptPage page : pages) {
            if (materialConceptIdByChunkId.putIfAbsent(page.chunkId(), page.materialConceptId()) != null) {
                continue;
            }
            int seq = chunks.size() + 1;
            chunks.add(new SourceChunk(seq, page.content()));
            chunkIdBySeq.put(seq, page.chunkId());
        }

        List<Long> materialConceptIds = pages.stream().map(ConceptPage::materialConceptId).distinct().toList();
        List<String> askedQuestions = quizRepository.findRecentQuestionContents(
                job.userId(), materialConceptIds, askedQuestionLimit);

        // 개념의 페이지가 적으면 같은 사실을 돌려 쓰거나 이름표를 부풀려 개수를 채운다. 페이지 수에 맞춰 덜 낸다
        int questionCount = Math.min(weaknessQuestionCount, chunks.size() * weaknessQuestionsPerPage);
        QuizGenerationRequest request = new QuizGenerationRequest(
                chunks,
                job.format(),
                job.difficulty(),
                questionCount,
                CognitiveMix.limitTo(weaknessCognitiveTypeCounts, questionCount),
                List.of(),
                new ConceptFocus(concept.name(), concept.description(), askedQuestions));

        GeneratedQuiz validated = generateAndValidate(request);
        quizWriter.saveWeakness(job, validated, chunkIdBySeq, materialConceptIdByChunkId);
    }

    private GeneratedQuiz generateAndValidate(QuizGenerationRequest request) {
        GeneratedQuiz generated = quizGenerationPort.generateQuiz(request);
        GeneratedQuiz validated = generatedQuizValidator.validate(request, generated);

        if (validated.concepts().isEmpty()) {
            throw new BusinessException(ErrorCode.NO_VALID_QUESTIONS);
        }
        return validated;
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
