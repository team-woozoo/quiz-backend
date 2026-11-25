package com.woozoo.quiz;

import com.woozoo.quiz.concept.persistence.MaterialConceptRepository;
import com.woozoo.quiz.material.domain.StoredChunk;
import com.woozoo.quiz.material.persistence.MaterialRepository;

import java.util.List;

// 업로드 분석을 거친 자료처럼, 청크마다 개념을 하나씩 만들어 그 청크에 잇는다.
// 생성기는 이제 저장된 개념에만 문제를 붙여서, 저장소로 자료를 직접 만든 테스트도 개념이 있어야 생성까지 간다
public final class ConceptFixture {

    private ConceptFixture() {
    }

    public static void conceptPerChunk(MaterialRepository materialRepository,
                                       MaterialConceptRepository materialConceptRepository, long materialId) {
        for (StoredChunk chunk : materialRepository.findChunks(materialId)) {
            long conceptId = materialConceptRepository.save(materialId, "개념 " + chunk.seq(),
                    chunk.seq() + "번 청크의 개념", List.of());
            materialConceptRepository.linkChunk(conceptId, chunk.id());
        }
    }
}
