package com.woozoo.quiz.quiz.port;

public interface QuizGenerationPort {
    /**
     * 청크로 개념과 개념별 문제를 만든다.
     * 근거는 받은 청크의 seq 와 원문 인용으로 가리킨다.
     * 내용 규칙(보기 수, 정답 수, 근거가 원문에 있는지)은 확인하지 않는다. 그건 서비스가 한다.
     *
     * @throws QuizGenerationException AI 호출이 실패했거나 응답을 읽을 수 없을 때
     */
    GeneratedQuiz generateQuiz(QuizGenerationRequest request);
}
