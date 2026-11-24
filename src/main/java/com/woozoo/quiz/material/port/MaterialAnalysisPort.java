package com.woozoo.quiz.material.port;

import com.woozoo.quiz.material.domain.MaterialChunk;

import java.util.List;

public interface MaterialAnalysisPort {

    /**
     * 업로드한 자료의 청크를 보고, 학습 내용이 있는 자료인지 판단하고 개념을 나눈다.
     * 개념이 가리키는 청크는 받은 청크의 seq 다. 저장 전에 부르므로 자료나 청크의 id 를 모른다.
     * 받은 응답을 검증하거나 고치지는 않는다(없는 seq, 같은 이름의 개념 등). 그건 서비스가 한다.
     *
     * @throws MaterialAnalysisException AI 호출이 실패했거나, 응답이 비었거나 출력 상한에 닿아 끝까지 썼는지 알 수 없을 때
     */
    MaterialAnalysis analyze(List<MaterialChunk> materialChunks);
}
