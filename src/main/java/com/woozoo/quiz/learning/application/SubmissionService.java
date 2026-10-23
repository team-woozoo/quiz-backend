package com.woozoo.quiz.learning.application;

import com.woozoo.quiz.global.error.BusinessException;
import com.woozoo.quiz.global.error.ErrorCode;
import com.woozoo.quiz.learning.domain.*;
import com.woozoo.quiz.learning.persistence.SubmissionRepository;
import com.woozoo.quiz.learning.persistence.WrongAnswerRepository;
import com.woozoo.quiz.quiz.domain.StoredOption;
import com.woozoo.quiz.quiz.domain.StoredQuiz;
import com.woozoo.quiz.quiz.persistence.QuizRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final WrongAnswerRepository wrongAnswerRepository;
    private final QuizRepository quizRepository;

    public SubmissionService(SubmissionRepository submissionRepository,
                             WrongAnswerRepository wrongAnswerRepository,
                             QuizRepository quizRepository) {
        this.submissionRepository = submissionRepository;
        this.wrongAnswerRepository = wrongAnswerRepository;
        this.quizRepository = quizRepository;
    }

    @Transactional
    public SubmissionResult submit(SubmitQuizCommand command) {
        Optional<StoredQuiz> quiz = quizRepository.findById(command.quizId());
        if(quiz.isEmpty() || quiz.get().userId() != command.userId()) {
            throw new BusinessException(ErrorCode.QUIZ_NOT_FOUND);
        }
        List<StoredOption> options = quizRepository.findOptions(command.quizId());

        // 잘못된 제출이면 아무것도 저장하기 전에 거절하려고 채점을 저장보다 먼저 한다
        List<QuestionResult> results;

        try {
            results = QuizGrader.grade(options, command.answers());
        } catch (InvalidSubmissionException e) {
            throw new BusinessException(ErrorCode.INVALID_SUBMISSION, e);
        }

        NewSubmission newSubmission = new NewSubmission(command.userId(), command.quizId());

        long submissionId = submissionRepository.save(newSubmission);
        submissionRepository.saveAnswers(submissionId, results);

        wrongAnswerRepository.apply(command.userId(), results);
        return new SubmissionResult(submissionId, results);
    }
}
