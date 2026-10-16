package com.woozoo.quiz.quiz.domain;

// 화면에 근거를 보여 줄 수 있게 근거 청크의 페이지 번호와 개념 이름을 같이 담는다
public record StoredQuestion(
        long id,
        int orderNo,
        String content,
        CognitiveType cognitiveType,
        String explanation,
        String sourceQuote,
        Integer sourcePageNo,
        String conceptName) {
}
