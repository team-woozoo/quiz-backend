package com.woozoo.quiz.learning.application;

import com.woozoo.quiz.learning.domain.QuizSummary;
import com.woozoo.quiz.learning.persistence.SubmissionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

// 내 퀴즈에 내 풀이 기록을 붙여 보여 준다. 제출 기록을 읽어야 해서 quiz 가 아니라 learning 에 둔다.
// quiz 에 두면 quiz 가 learning 을 알게 되어, 이미 있는 learning → quiz 의존과 순환이 생긴다
@Service
public class QuizHistoryService {

    private final SubmissionRepository submissionRepository;

    public QuizHistoryService(SubmissionRepository submissionRepository) {
        this.submissionRepository = submissionRepository;
    }

    public List<QuizSummary> getMyQuizzes(long userId) {
        return submissionRepository.findQuizSummaries(userId);
    }
}
