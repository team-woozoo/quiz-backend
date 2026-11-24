package com.woozoo.quiz.material.application;

import com.woozoo.quiz.concept.domain.ConceptTag;
import com.woozoo.quiz.material.domain.MaterialChunk;
import com.woozoo.quiz.material.port.ExtractedConcept;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractedConceptValidatorTest {

    private final ExtractedConceptValidator validator = new ExtractedConceptValidator();

    // 분석에 보낸 청크는 1~3번이다
    private static final List<MaterialChunk> CHUNKS = List.of(
            new MaterialChunk(1, 1, "첫 페이지"),
            new MaterialChunk(2, 2, "둘째 페이지"),
            new MaterialChunk(3, 4, "넷째 페이지"));

    private static ExtractedConcept concept(String name, List<Integer> chunkSeqs) {
        return new ExtractedConcept(name, name + " 설명", List.of(ConceptTag.RULE), chunkSeqs);
    }

    private List<ExtractedConcept> validate(ExtractedConcept... concepts) {
        // List.of 는 null 원소를 받지 않아, null 개념을 넣을 수 있게 ArrayList 로 담는다
        return validator.validate(CHUNKS, new ArrayList<>(Arrays.asList(concepts)));
    }

    @Test
    void 올바른_개념은_그대로_남긴다() {
        ExtractedConcept tcp = new ExtractedConcept("TCP", "연결 지향 전송",
                List.of(ConceptTag.CONTRAST, ConceptTag.RULE), List.of(1, 2));

        assertThat(validate(tcp)).containsExactly(tcp);
    }

    @Test
    void 개념이_없으면_빈_목록을_돌려준다() {
        assertThat(validator.validate(CHUNKS, List.of())).isEmpty();
    }

    @Test
    void null_개념과_이름이_빈_개념은_버린다() {
        List<ExtractedConcept> result = validate(
                null,
                concept(null, List.of(1)),
                concept("   ", List.of(1)),
                concept("TCP", List.of(1)));

        assertThat(result).extracting(ExtractedConcept::name).containsExactly("TCP");
    }

    @Test
    void 이름의_앞뒤_공백을_지운다() {
        assertThat(validate(concept("  TCP  ", List.of(1))))
                .extracting(ExtractedConcept::name).containsExactly("TCP");
    }

    // 강의 앞뒤에 같은 주제가 나오면 모델이 같은 이름을 둘로 나눠 준다. 버리면 한쪽 페이지 연결과 태그가 사라진다
    @Test
    void 이름이_같은_개념은_하나로_합치고_설명은_먼저_것을_쓴다() {
        List<ExtractedConcept> result = validate(
                new ExtractedConcept("TCP", "앞쪽 설명", List.of(ConceptTag.RULE), List.of(1, 2)),
                new ExtractedConcept("UDP", "비연결형", List.of(), List.of(3)),
                new ExtractedConcept("TCP", "뒤쪽 설명", List.of(ConceptTag.CONTRAST, ConceptTag.RULE), List.of(2, 3)));

        assertThat(result).containsExactly(
                new ExtractedConcept("TCP", "앞쪽 설명", List.of(ConceptTag.RULE, ConceptTag.CONTRAST), List.of(1, 2, 3)),
                new ExtractedConcept("UDP", "비연결형", List.of(), List.of(3)));
    }

    // 공백을 먼저 지워야 같은 이름으로 묶인다
    @Test
    void 공백만_다른_이름도_같은_개념으로_합친다() {
        List<ExtractedConcept> result = validate(concept("TCP", List.of(1)), concept("TCP ", List.of(2)));

        assertThat(result).singleElement()
                .satisfies(concept -> assertThat(concept.chunkSeqs()).containsExactly(1, 2));
    }

    // 이름이 겹치지 않는 개념은 합치기를 거치지 않아, 한 개념 안의 중복도 따로 빼야 한다
    @Test
    void 한_개념_안의_같은_청크와_태그는_한_번만_남긴다() {
        List<ExtractedConcept> result = validate(new ExtractedConcept("TCP", "설명",
                List.of(ConceptTag.RULE, ConceptTag.RULE), List.of(1, 1, 2)));

        assertThat(result).singleElement().satisfies(concept -> {
            assertThat(concept.tags()).containsExactly(ConceptTag.RULE);
            assertThat(concept.chunkSeqs()).containsExactly(1, 2);
        });
    }

    @Test
    void 태그와_청크_목록이_null_이면_빈_목록으로_본다() {
        List<ExtractedConcept> result = validate(
                new ExtractedConcept("TCP", "설명", null, List.of(1)),
                new ExtractedConcept("UDP", "설명", List.of(), null));

        // 태그가 없는 개념은 남고, 청크가 없는 개념은 연결할 페이지가 없어 빠진다
        assertThat(result).singleElement().satisfies(concept -> {
            assertThat(concept.name()).isEqualTo("TCP");
            assertThat(concept.tags()).isEmpty();
        });
    }

    @Test
    void 보내지_않은_청크_번호는_빼고_개념은_남긴다() {
        assertThat(validate(concept("TCP", List.of(1, 99, 3))))
                .singleElement()
                .satisfies(concept -> assertThat(concept.chunkSeqs()).containsExactly(1, 3));
    }

    @Test
    void 보낸_청크를_하나도_가리키지_않으면_버린다() {
        List<ExtractedConcept> result = validate(
                concept("모르는 페이지만", List.of(98, 99)),
                concept("페이지 없음", List.of()),
                concept("TCP", List.of(1)));

        assertThat(result).extracting(ExtractedConcept::name).containsExactly("TCP");
    }

    // 이름은 VARCHAR(255) 라 넘으면 저장 전체가 실패한다
    @Test
    void 이름이_255자를_넘으면_버린다() {
        List<ExtractedConcept> result = validate(
                concept("가".repeat(256), List.of(1)),
                concept("나".repeat(255), List.of(1)));

        assertThat(result).extracting(ExtractedConcept::name).containsExactly("나".repeat(255));
    }

    @Test
    void 설명이_비어_있으면_버린다() {
        List<ExtractedConcept> result = validate(
                new ExtractedConcept("TCP", null, List.of(), List.of(1)),
                new ExtractedConcept("UDP", "  ", List.of(), List.of(1)),
                new ExtractedConcept("IP", "패킷 전달", List.of(), List.of(1)));

        assertThat(result).extracting(ExtractedConcept::name).containsExactly("IP");
    }

    @Test
    void 모두_버려지면_빈_목록을_돌려준다() {
        assertThat(validate(concept("모르는 페이지만", List.of(99)), null)).isEmpty();
    }
}
