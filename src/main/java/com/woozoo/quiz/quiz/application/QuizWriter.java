package com.woozoo.quiz.quiz.application;

import com.woozoo.quiz.concept.persistence.MaterialConceptRepository;
import com.woozoo.quiz.quiz.domain.GenerationJob;
import com.woozoo.quiz.quiz.domain.NewQuestion;
import com.woozoo.quiz.quiz.domain.NewQuiz;
import com.woozoo.quiz.quiz.persistence.GenerationJobRepository;
import com.woozoo.quiz.quiz.persistence.QuizRepository;
import com.woozoo.quiz.quiz.port.GeneratedConcept;
import com.woozoo.quiz.quiz.port.GeneratedOption;
import com.woozoo.quiz.quiz.port.GeneratedQuestion;
import com.woozoo.quiz.quiz.port.GeneratedQuiz;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Component
public class QuizWriter {

    private final QuizRepository quizRepository;
    private final MaterialConceptRepository materialConceptRepository;
    private final GenerationJobRepository generationJobRepository;

    public QuizWriter(QuizRepository quizRepository,
                      MaterialConceptRepository materialConceptRepository,
                      GenerationJobRepository generationJobRepository) {
        this.quizRepository = quizRepository;
        this.materialConceptRepository = materialConceptRepository;
        this.generationJobRepository = generationJobRepository;
    }

    // GeneratedQuizValidator 를 통과한 결과만 받는다. 검증 전 결과가 오면 INSERT 가 실패할 수 있다.
    // 퀴즈는 저장됐는데 DONE 으로 못 바뀌면 앱은 끝나지 않는 작업을 보게 되어, 저장과 DONE 을 한 트랜잭션에 둔다
    @Transactional
    public long save(GenerationJob job, GeneratedQuiz validated, Map<Integer, Long> chunkIdBySeq) {
        long quizId = quizRepository.save(
                new NewQuiz(job.userId(), job.materialId(), job.kind(), job.format(), job.difficulty()));

        int questionOrderNo = 1;
        for (GeneratedConcept concept : validated.concepts()) {
            long conceptId = materialConceptRepository.findOrCreate(
                    job.materialId(), concept.name(), concept.description());

            for (int chunkSeq : concept.chunkSeqs()) {
                materialConceptRepository.linkChunk(conceptId, chunkIdBySeq.get(chunkSeq));
            }

            for (GeneratedQuestion question : concept.questions()) {
                long questionId = quizRepository.saveQuestion(new NewQuestion(
                        quizId,
                        questionOrderNo++,
                        conceptId,
                        chunkIdBySeq.get(question.sourceChunkSeq()),
                        question.sourceQuote(),
                        question.cognitiveType(),
                        question.content(),
                        question.explanation()));
                saveOptions(questionId, question.options());
            }
        }

        // 생성 중에 서버가 재시작돼 실패로 정리된 작업이면 여기서 막혀, 롤백으로 주인 없는 퀴즈를 남기지 않는다
        if (!generationJobRepository.markDone(job.id(), quizId)) {
            throw new IllegalStateException("PROCESSING 이 아닌 작업은 완료할 수 없다: jobId=" + job.id());
        }
        return quizId;
    }

    private void saveOptions(long questionId, List<GeneratedOption> options) {
        for (int i = 0; i < options.size(); i++) {
            GeneratedOption option = options.get(i);
            quizRepository.saveOption(questionId, i + 1, option.content(), option.isCorrect());
        }
    }
}
