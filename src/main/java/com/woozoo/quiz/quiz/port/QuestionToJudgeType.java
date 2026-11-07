package com.woozoo.quiz.quiz.port;

import java.util.List;

// 판정 모델이 생성기의 라벨에 끌려가지 않게 cognitiveType 을 뺀다.
// index 는 이번 판정 안에서만 문제를 가리키는 임시 번호이고, sourceChunk 는 근거 청크의 본문이다
public record QuestionToJudgeType(int index, String content, String sourceQuote,
                                  String explanation, List<GeneratedOption> options, String sourceChunk) {
}
