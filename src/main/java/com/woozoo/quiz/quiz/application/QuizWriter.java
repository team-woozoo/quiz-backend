package com.woozoo.quiz.quiz.application;

import com.woozoo.quiz.quiz.domain.GenerationJob;
import com.woozoo.quiz.quiz.domain.NewQuestion;
import com.woozoo.quiz.quiz.domain.NewQuiz;
import com.woozoo.quiz.quiz.domain.QuizFormat;
import com.woozoo.quiz.quiz.persistence.GenerationJobRepository;
import com.woozoo.quiz.quiz.persistence.QuizRepository;
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
    private final GenerationJobRepository generationJobRepository;
    private final OptionShuffler optionShuffler;

    public QuizWriter(QuizRepository quizRepository,
                      GenerationJobRepository generationJobRepository,
                      OptionShuffler optionShuffler) {
        this.quizRepository = quizRepository;
        this.generationJobRepository = generationJobRepository;
        this.optionShuffler = optionShuffler;
    }

    // GeneratedQuizValidator 를 통과한 결과만 받는다. 검증 전 결과가 오면 INSERT 가 실패할 수 있다.
    // 개념은 업로드할 때 이미 저장해 두어 여기서 만들지 않는다. 생성기는 개념을 요청 안의 번호로 가리키고,
    // 검증기가 보낸 번호의 문제만 남기므로 conceptIdByNo 에서 반드시 찾는다.
    // 퀴즈는 저장됐는데 DONE 으로 못 바뀌면 앱은 끝나지 않는 작업을 보게 되어, 저장과 DONE 을 한 트랜잭션에 둔다
    @Transactional
    public long save(GenerationJob job, GeneratedQuiz validated,
                     Map<Integer, Long> chunkIdBySeq, Map<Integer, Long> conceptIdByNo) {
        long quizId = quizRepository.save(
                new NewQuiz(job.userId(), job.materialId(), job.kind(), job.format(), job.difficulty()));

        int questionOrderNo = 1;
        for (GeneratedQuestion question : validated.questions()) {
            long conceptId = conceptIdByNo.get(question.conceptNo());
            saveQuestion(quizId, questionOrderNo++, conceptId, chunkIdBySeq, question, job.format());
        }

        markDone(job, quizId);
        return quizId;
    }

    // 약점 퀴즈는 자료 없이 저장하고 겨냥한 대표 개념을 남긴다. 검증기가 겨냥한 개념(번호 1)의 문제만 남긴다.
    // 대표 개념은 여러 자료의 개념을 묶을 수 있어 번호 하나가 자료 개념 하나로 정해지지 않는다.
    // 그래서 문제마다 근거 페이지로 원래 자료 개념을 찾아 붙인다
    @Transactional
    public long saveWeakness(GenerationJob job, GeneratedQuiz validated, Map<Integer, Long> chunkIdBySeq,
                             Map<Long, Long> materialConceptIdByChunkId) {
        long quizId = quizRepository.save(
                new NewQuiz(job.userId(), null, job.kind(), job.format(), job.difficulty()));
        quizRepository.saveWeaknessTarget(quizId, job.userConceptId());

        int questionOrderNo = 1;
        for (GeneratedQuestion question : validated.questions()) {
            long chunkId = chunkIdBySeq.get(question.sourceChunkSeq());
            saveQuestion(quizId, questionOrderNo++, materialConceptIdByChunkId.get(chunkId),
                    chunkIdBySeq, question, job.format());
        }

        markDone(job, quizId);
        return quizId;
    }

    private void saveQuestion(long quizId, int orderNo, long conceptId, Map<Integer, Long> chunkIdBySeq,
                              GeneratedQuestion question, QuizFormat format) {
        long questionId = quizRepository.saveQuestion(new NewQuestion(
                quizId,
                orderNo,
                conceptId,
                chunkIdBySeq.get(question.sourceChunkSeq()),
                question.sourceQuote(),
                question.cognitiveType(),
                question.content(),
                question.explanation()));
        saveOptions(questionId, question.options(), format);
    }

    // 생성 중에 서버가 재시작돼 실패로 정리된 작업이면 여기서 막혀, 롤백으로 주인 없는 퀴즈를 남기지 않는다
    private void markDone(GenerationJob job, long quizId) {
        if (!generationJobRepository.markDone(job.id(), quizId)) {
            throw new IllegalStateException("PROCESSING 이 아닌 작업은 완료할 수 없다: jobId=" + job.id());
        }
    }

    private void saveOptions(long questionId, List<GeneratedOption> options, QuizFormat format) {
        // AI 는 정답을 첫 보기로 쓰는 습관이 있어, 저장 전에 객관식 보기 순서를 섞는다
        List<GeneratedOption> shuffledOptions = optionShuffler.shuffle(options, format);

        for (int i = 0; i < shuffledOptions.size(); i++) {
            GeneratedOption option = shuffledOptions.get(i);
            quizRepository.saveOption(questionId, i + 1, option.content(), option.isCorrect());
        }
    }
}
