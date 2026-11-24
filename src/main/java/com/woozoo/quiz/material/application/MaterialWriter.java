package com.woozoo.quiz.material.application;

import com.woozoo.quiz.concept.persistence.MaterialConceptRepository;
import com.woozoo.quiz.material.domain.MaterialChunk;
import com.woozoo.quiz.material.domain.NewMaterial;
import com.woozoo.quiz.material.domain.StoredChunk;
import com.woozoo.quiz.material.persistence.MaterialRepository;
import com.woozoo.quiz.material.port.ExtractedConcept;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 추출과 분석은 트랜잭션 밖에서 하고, 자료와 청크와 개념 저장만 한 트랜잭션으로 묶으려고 서비스와 나눈다.
// 개념 저장이 실패하면 자료와 청크도 남지 않아, 개념 없는 자료가 해시 때문에 다시 올릴 수도 없게 되는 일을 막는다
@Component
public class MaterialWriter {

    private final MaterialRepository materialRepository;
    private final MaterialConceptRepository materialConceptRepository;

    public MaterialWriter(MaterialRepository materialRepository,
                          MaterialConceptRepository materialConceptRepository) {
        this.materialRepository = materialRepository;
        this.materialConceptRepository = materialConceptRepository;
    }

    @Transactional
    public long save(NewMaterial material, List<MaterialChunk> materialChunks,
                     List<ExtractedConcept> extractedConcepts) {
        long materialId = materialRepository.save(material);
        materialRepository.saveChunks(materialId, materialChunks);

        // 분석은 청크를 seq 로 가리키고 DB 는 id 로 잇는다
        List<StoredChunk> storedChunks = materialRepository.findChunks(materialId);
        Map<Integer, Long> chunkIdBySeq = storedChunks.stream()
                .collect(Collectors.toMap(StoredChunk::seq, StoredChunk::id));

        saveConcepts(materialId, extractedConcepts, chunkIdBySeq);

        return materialId;
    }

    // ExtractedConceptValidator 를 거친 개념만 받는다. 이름이 겹치거나 모르는 seq 가 오면 저장 전체가 실패한다
    private void saveConcepts(long materialId, List<ExtractedConcept> extractedConcepts,
                              Map<Integer, Long> chunkIdBySeq) {
        for (ExtractedConcept extractedConcept : extractedConcepts) {
            long conceptId = materialConceptRepository.save(materialId, extractedConcept.name(),
                    extractedConcept.description(), extractedConcept.tags());

            for (int seq : extractedConcept.chunkSeqs()) {
                materialConceptRepository.linkChunk(conceptId, chunkIdBySeq.get(seq));
            }
        }
    }
}
