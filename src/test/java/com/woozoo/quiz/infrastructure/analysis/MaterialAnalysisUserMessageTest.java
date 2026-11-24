package com.woozoo.quiz.infrastructure.analysis;

import com.woozoo.quiz.material.domain.MaterialChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialAnalysisUserMessageTest {

    @Test
    void 청크를_seq_와_함께_material_태그로_묶는다() {
        List<MaterialChunk> chunks = List.of(
                new MaterialChunk(1, 4, "TCP는 연결 지향 프로토콜이다.\n신뢰성 있는 전송을 보장한다."),
                new MaterialChunk(2, 5, "UDP는 비연결형이다."));

        assertThat(MaterialAnalysisUserMessage.from(chunks)).isEqualTo("""
                <material>
                <chunk seq="1">
                TCP는 연결 지향 프로토콜이다.
                신뢰성 있는 전송을 보장한다.
                </chunk>
                <chunk seq="2">
                UDP는 비연결형이다.
                </chunk>
                </material>
                """);
    }

    // 코드가 많은 자료는 중괄호와 꺾쇠가 흔하다. 바꾸면 모델이 본 글자와 저장한 원문이 달라진다
    @Test
    void 원문의_중괄호와_꺾쇠를_그대로_둔다() {
        List<MaterialChunk> chunks = List.of(
                new MaterialChunk(1, 1, "if (a < b) { list.add(\"<tag>\"); }"));

        assertThat(MaterialAnalysisUserMessage.from(chunks))
                .contains("if (a < b) { list.add(\"<tag>\"); }");
    }
}
