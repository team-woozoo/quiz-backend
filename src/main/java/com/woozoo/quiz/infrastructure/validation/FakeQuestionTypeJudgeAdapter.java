package com.woozoo.quiz.infrastructure.validation;

import com.woozoo.quiz.quiz.port.JudgedQuestionType;
import com.woozoo.quiz.quiz.port.QuestionToJudgeType;
import com.woozoo.quiz.quiz.domain.CognitiveType;
import com.woozoo.quiz.quiz.port.QuestionTypeJudgePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

// 로컬과 테스트의 기본 검증기다. AI 를 부르지 않아 비용이 들지 않는다
@Component
@ConditionalOnProperty(name = "quiz.generation.judge", havingValue = "fake")
public class FakeQuestionTypeJudgeAdapter implements QuestionTypeJudgePort {

    private final int rejectEvery;

    public FakeQuestionTypeJudgeAdapter(@Value("${quiz.generation.fake-judge-reject-every}")
                                        int rejectEvery) {
        this.rejectEvery = rejectEvery;
    }

    @Override
    public List<JudgedQuestionType> judge(List<QuestionToJudgeType> questions) {
        return questions.stream()
                .map(question -> new JudgedQuestionType(question.index(),
                        typeOf(question.index())))
                .toList();
    }

    // 가짜 생성기가 모든 문제를 REMEMBER 로 내므로 REMEMBER 는 일치, 그 밖의 유형은 불일치가 된다
    private CognitiveType typeOf(int index) {
        boolean rejected = rejectEvery > 0 && (index + 1) % rejectEvery == 0;
        return rejected ? CognitiveType.UNDERSTAND : CognitiveType.REMEMBER;
    }
}
