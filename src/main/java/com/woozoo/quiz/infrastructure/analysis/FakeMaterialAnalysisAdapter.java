package com.woozoo.quiz.infrastructure.analysis;

import com.woozoo.quiz.concept.domain.ConceptTag;
import com.woozoo.quiz.material.domain.MaterialChunk;
import com.woozoo.quiz.material.port.ExtractedConcept;
import com.woozoo.quiz.material.port.MaterialAnalysis;
import com.woozoo.quiz.material.port.MaterialAnalysisPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

// 로컬과 테스트의 기본 분석기다. AI 를 부르지 않고, 청크마다 개념을 하나씩 만들어 항상 학습 자료로 받는다.
// 실패와 부적합 흐름은 서비스 테스트에서 포트를 목으로 두고 확인한다
@Component
@ConditionalOnProperty(name = "quiz.material.analyzer", havingValue = "fake")
public class FakeMaterialAnalysisAdapter implements MaterialAnalysisPort {

    @Override
    public MaterialAnalysis analyze(List<MaterialChunk> materialChunks) {
        List<ExtractedConcept> extractedConcepts = materialChunks.stream()
                .map(chunk ->
                    new ExtractedConcept(
                            "개념 " + chunk.seq(),
                            chunk.seq() + "번 청크의 내용을 다루는 가짜 개념이다.",
                            List.of(ConceptTag.RULE, ConceptTag.CONDITION),
                            List.of(chunk.seq())
                    )
                )
                .toList();

        return new MaterialAnalysis(true, "가짜 분석: 학습 내용이 있다고 본다", extractedConcepts);
    }
}
