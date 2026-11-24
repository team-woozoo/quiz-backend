package com.woozoo.quiz.infrastructure.analysis;

import com.woozoo.quiz.material.domain.MaterialChunk;

import java.util.List;

// 업로드한 자료를 분석할 때 LLM 에 보낼 user 메시지를 만든다. 규칙은 system 프롬프트(prompts/material-analysis-system.md)에 있고,
// 여기에는 강의 자료만 넣는다. 태그는 구획을 나눠 읽게 하는 표시일 뿐 XML 로 파싱하지 않아 원문을 이스케이프하지 않는다.
// 생성 요청과 같은 형식으로 보내, 분석이 매긴 청크 번호(seq)가 이후 생성 요청의 번호와 같은 청크를 가리키게 한다
final class MaterialAnalysisUserMessage {

    private MaterialAnalysisUserMessage() {
    }

    // 문자열을 직접 이어 붙여 만든다. 템플릿 변수로 넘기면 강의 자료 속 { } 를 변수 자리로 읽어 실패한다
    static String from(List<MaterialChunk> chunks) {
        StringBuilder message = new StringBuilder();
        message.append("<material>\n");
        for (MaterialChunk chunk : chunks) {
            message.append("<chunk seq=\"").append(chunk.seq()).append("\">\n")
                    .append(chunk.content()).append('\n')
                    .append("</chunk>\n");
        }
        message.append("</material>\n");
        return message.toString();
    }
}
