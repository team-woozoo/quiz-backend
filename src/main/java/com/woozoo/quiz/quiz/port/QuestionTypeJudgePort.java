package com.woozoo.quiz.quiz.port;

import java.util.List;

public interface QuestionTypeJudgePort {
    /**
     * 문제마다 정답을 도출하는 데 필요한 사고 과정으로 인지 유형을 판정한다.
     * 판정은 받은 index 로 가리킨다. 생성기의 유형과 견주는 일은 서비스가 한다.
     *
     * @throws QuestionTypeJudgeException AI 호출이 실패했거나 응답을 읽을 수 없을 때
     */
    List<JudgedQuestionType> judge(List<QuestionToJudgeType> questions);
}
