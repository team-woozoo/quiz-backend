package com.woozoo.quiz.quiz.port;

import com.woozoo.quiz.quiz.domain.CognitiveType;

// index 는 판정을 요청한 QuestionToJudgeType 의 index 다.
// reason 은 판정 근거를 보려고 받는다. 판정에는 쓰지 않고, 모델이 이유를 먼저 쓰게 유형 앞에 둔다
public record JudgedQuestionType(int index, String reason, CognitiveType cognitiveType) {
}
