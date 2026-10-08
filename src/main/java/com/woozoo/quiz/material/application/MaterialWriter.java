package com.woozoo.quiz.material.application;

import com.woozoo.quiz.material.domain.MaterialChunk;
import com.woozoo.quiz.material.domain.NewMaterial;
import com.woozoo.quiz.material.persistence.MaterialRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 추출은 트랜잭션 밖에서 하고, 자료와 청크 저장만 한 트랜잭션으로 묶으려고 서비스와 나눈다
@Component
public class MaterialWriter {

    private final MaterialRepository materialRepository;

    public MaterialWriter(MaterialRepository materialRepository) {
        this.materialRepository = materialRepository;
    }

    @Transactional
    public long save(NewMaterial material, List<MaterialChunk> materialChunks) {
        long materialId = materialRepository.save(material);
        materialRepository.saveChunks(materialId, materialChunks);
        return materialId;
    }
}
