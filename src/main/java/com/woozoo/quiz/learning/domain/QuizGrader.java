package com.woozoo.quiz.learning.domain;

import com.woozoo.quiz.quiz.domain.StoredOption;

import java.util.*;
import java.util.stream.Collectors;

public class QuizGrader {

    private QuizGrader() {}

    public static List<QuestionResult> grade(List<StoredOption> options, List<SubmittedAnswer> answers) {
        Set<Long> seen = new HashSet<>();

        for(SubmittedAnswer answer : answers) {
            if(!seen.add(answer.questionId())) {
                throw new InvalidSubmissionException("같은 문제를 두 번 제출했다: questionId=" + answer.questionId());
            }
        }

        Map<Long, StoredOption> optionById = options.stream()
                .collect(Collectors.toMap(StoredOption::id, option -> option));

        for(SubmittedAnswer answer : answers) {
            StoredOption option = optionById.get(answer.optionId());
            if(option == null) {
                throw new InvalidSubmissionException("이 퀴즈의 보기가 아니다: optionId=" + answer.optionId());
            }

            // 다른 문제의 정답 보기를 보내 맞은 것으로 만들지 못하게, 고른 보기가 그 문제의 것인지 본다
            if(answer.questionId() != option.questionId()) {
                throw new InvalidSubmissionException("다른 문제의 보기이다: questionId=" +
                        answer.questionId() + ", optionId=" + answer.optionId());
            }
        }

        Map<Long, Long> selectedByQuestion = answers.stream()
                .collect(Collectors.toMap(SubmittedAnswer::questionId, SubmittedAnswer::optionId));

        // 모든 문제에는 보기가 있고 보기는 문제 순서로 온다. 그래서 보기에서 뽑은 문제 id 가 곧 퀴즈의 문제 목록이다
        List<Long> questionIds = options.stream()
                .map(StoredOption::questionId)
                .distinct()
                .toList();

        List<QuestionResult> questionResults = new ArrayList<>();

        // 보내지 않은 문제는 안 푼 것으로 보고 틀림으로 채점한다
        for(Long questionId : questionIds) {
            Long selected = selectedByQuestion.get(questionId);
            boolean correct = selected != null && optionById.get(selected).isCorrect();
            questionResults.add(new QuestionResult(questionId, selected, correct));
        }
        return questionResults;
    }
}
