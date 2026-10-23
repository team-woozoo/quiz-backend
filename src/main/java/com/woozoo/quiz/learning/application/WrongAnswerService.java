package com.woozoo.quiz.learning.application;

import com.woozoo.quiz.global.error.BusinessException;
import com.woozoo.quiz.global.error.ErrorCode;
import com.woozoo.quiz.learning.domain.InvalidSubmissionException;
import com.woozoo.quiz.learning.domain.QuestionResult;
import com.woozoo.quiz.learning.domain.QuizGrader;
import com.woozoo.quiz.learning.domain.SubmittedAnswer;
import com.woozoo.quiz.learning.domain.WrongAnswerQuestion;
import com.woozoo.quiz.learning.persistence.SubmissionRepository;
import com.woozoo.quiz.learning.persistence.WrongAnswerRepository;
import com.woozoo.quiz.quiz.domain.StoredOption;
import com.woozoo.quiz.quiz.persistence.QuizRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class WrongAnswerService {

    private final WrongAnswerRepository wrongAnswerRepository;
    private final SubmissionRepository submissionRepository;
    private final QuizRepository quizRepository;

    public WrongAnswerService(WrongAnswerRepository wrongAnswerRepository,
                              SubmissionRepository submissionRepository,
                              QuizRepository quizRepository) {
        this.wrongAnswerRepository = wrongAnswerRepository;
        this.submissionRepository = submissionRepository;
        this.quizRepository = quizRepository;
    }

    public List<WrongAnswerNote> getMyWrongAnswers(long userId) {
        List<WrongAnswerQuestion> questions = wrongAnswerRepository.findUnresolved(userId);
        Map<Long, List<StoredOption>> optionsByQuestion = quizRepository
                .findOptionsOfMyQuestions(userId, questions.stream().map(WrongAnswerQuestion::questionId).toList())
                .stream()
                .collect(Collectors.groupingBy(StoredOption::questionId));

        return questions.stream()
                .map(question -> new WrongAnswerNote(question, optionsByQuestion.get(question.questionId())))
                .toList();
    }

    // 앱이 먼저 채점해 보여 주지만, 기록은 서버가 다시 채점한 결과로 남긴다.
    // 복습 한 번을 퀴즈 없는 제출 하나로 남겨, 문제당 최근 답으로 세는 분석에 들어가고 퀴즈별 기록은 바뀌지 않는다.
    // 오답 노트에 없는 내 문제를 보내도 받는다. 맞히면 바뀌는 것이 없고, 틀리면 오답 노트에 들어간다
    @Transactional
    public SubmissionResult review(ReviewCommand command) {
        List<Long> questionIds = command.answers().stream().map(SubmittedAnswer::questionId).toList();
        List<StoredOption> options = quizRepository.findOptionsOfMyQuestions(command.userId(), questionIds);

        // 잘못된 제출이면 아무것도 저장하기 전에 거절하려고 채점을 저장보다 먼저 한다
        List<QuestionResult> results;
        try {
            results = QuizGrader.gradeAnswered(options, command.answers());
        } catch (InvalidSubmissionException e) {
            throw new BusinessException(ErrorCode.INVALID_SUBMISSION, e);
        }

        long submissionId = submissionRepository.saveReview(command.userId());
        submissionRepository.saveAnswers(submissionId, results);
        wrongAnswerRepository.apply(command.userId(), results);
        return new SubmissionResult(submissionId, results);
    }
}
