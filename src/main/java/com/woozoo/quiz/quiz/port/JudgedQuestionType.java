package com.woozoo.quiz.quiz.port;

import com.woozoo.quiz.quiz.domain.CognitiveType;

// index 는 판정을 요청한 QuestionToJudgeType 의 index 다
public record JudgedQuestionType(int index, CognitiveType cognitiveType) {
}
