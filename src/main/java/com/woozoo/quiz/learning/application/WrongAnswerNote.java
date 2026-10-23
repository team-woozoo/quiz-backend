package com.woozoo.quiz.learning.application;

import com.woozoo.quiz.learning.domain.WrongAnswerQuestion;
import com.woozoo.quiz.quiz.domain.StoredOption;

import java.util.List;

// 오답 노트의 문제와 그 보기. 앱이 바로 채점할 수 있게 정답 보기도 함께 준다
public record WrongAnswerNote(WrongAnswerQuestion question, List<StoredOption> options) {
}
