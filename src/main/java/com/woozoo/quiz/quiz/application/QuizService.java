package com.woozoo.quiz.quiz.application;

import com.woozoo.quiz.global.error.BusinessException;
import com.woozoo.quiz.global.error.ErrorCode;
import com.woozoo.quiz.quiz.domain.StoredOption;
import com.woozoo.quiz.quiz.domain.StoredQuiz;
import com.woozoo.quiz.quiz.persistence.QuizRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class QuizService {

    private final QuizRepository quizRepository;

    public QuizService(QuizRepository quizRepository) {
        this.quizRepository = quizRepository;
    }

    // 정답도 같이 내려줘 앱이 고르는 즉시 맞았는지 보여 준다. 기록에 남는 채점은 제출할 때 서버가 다시 한다.
    // 남의 퀴즈도 없는 퀴즈와 같은 404 로 답한다
    public QuizDetail getQuiz(long userId, long quizId) {
        StoredQuiz quiz = quizRepository.findById(quizId)
                .filter(found -> found.userId() == userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_NOT_FOUND));

        Map<Long, List<StoredOption>> optionsByQuestionId = quizRepository.findOptions(quizId).stream()
                .collect(Collectors.groupingBy(StoredOption::questionId));

        List<QuizDetail.QuestionDetail> questions = quizRepository.findQuestions(quizId).stream()
                .map(question -> new QuizDetail.QuestionDetail(
                        question, optionsByQuestionId.getOrDefault(question.id(), List.of())))
                .toList();

        return new QuizDetail(quiz, questions);
    }
}
