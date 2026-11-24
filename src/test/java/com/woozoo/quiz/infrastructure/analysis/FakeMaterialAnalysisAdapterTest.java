package com.woozoo.quiz.infrastructure.analysis;

import com.woozoo.quiz.material.domain.MaterialChunk;
import com.woozoo.quiz.material.port.ExtractedConcept;
import com.woozoo.quiz.material.port.MaterialAnalysis;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FakeMaterialAnalysisAdapterTest {

    private final FakeMaterialAnalysisAdapter adapter = new FakeMaterialAnalysisAdapter();

    @Test
    void 모든_자료를_학습_자료로_받는다() {
        MaterialAnalysis analysis = adapter.analyze(List.of(new MaterialChunk(1, 1, "내용")));

        assertThat(analysis.suitable()).isTrue();
    }

    // 개념 이름이 겹치면 material_concept 의 (material_id, name) 유일 제약에 걸려 저장이 실패한다
    @Test
    void 청크마다_이름이_다른_개념을_하나씩_만들고_그_청크를_가리킨다() {
        List<MaterialChunk> chunks = List.of(
                new MaterialChunk(1, 3, "첫 페이지"),
                new MaterialChunk(2, 4, "둘째 페이지"));

        MaterialAnalysis analysis = adapter.analyze(chunks);

        assertThat(analysis.concepts()).extracting(ExtractedConcept::name).doesNotHaveDuplicates().hasSize(2);
        assertThat(analysis.concepts()).extracting(ExtractedConcept::chunkSeqs)
                .containsExactly(List.of(1), List.of(2));
    }

    // 배열 컬럼에 원소가 여럿 들어가는 경로까지 로컬에서 거치게 한다
    @Test
    void 개념마다_태그를_둘_이상_붙인다() {
        MaterialAnalysis analysis = adapter.analyze(List.of(new MaterialChunk(1, 1, "내용")));

        assertThat(analysis.concepts()).singleElement()
                .satisfies(concept -> assertThat(concept.tags()).hasSizeGreaterThanOrEqualTo(2));
    }
}
