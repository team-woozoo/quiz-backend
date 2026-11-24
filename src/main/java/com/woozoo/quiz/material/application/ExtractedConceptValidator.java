package com.woozoo.quiz.material.application;

import com.woozoo.quiz.concept.domain.ConceptTag;
import com.woozoo.quiz.material.domain.MaterialChunk;
import com.woozoo.quiz.material.port.ExtractedConcept;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// 분석이 뽑은 개념을 저장할 수 있는 모양으로 고른다. 분석 어댑터는 응답이 끝까지 왔는지만 보고 내용은 고치지 않아,
// 개념 하나가 잘못돼도 나머지는 쓸 수 있게 여기서 다듬고, 합치고, 저장하면 실패할 개념만 버린다.
// 남은 개념이 없을 때 업로드를 실패로 볼지는 서비스가 정한다
@Component
@Slf4j
public class ExtractedConceptValidator {

    private static final int MAX_CONCEPT_NAME_LENGTH = 255;

    // 개념을 버린 이유. 분석 프롬프트를 고칠 때 어느 규칙에서 많이 떨어지는지 보려고 센다
    enum Rejection {
        // 개념이 null 이거나 이름이 비어 있다
        BLANK_NAME,
        // 이름이 VARCHAR(255) 를 넘는다
        NAME_TOO_LONG,
        // 설명이 비어 있다
        BLANK_DESCRIPTION,
        // 받은 청크를 하나도 가리키지 않는다
        NO_KNOWN_CHUNK
    }

    // 단계마다 앞 단계의 결과를 전제로 한다. 순서를 바꾸면 이름이 없는 개념끼리 합쳐지거나 NPE 가 날 수 있다
    public List<ExtractedConcept> validate(List<MaterialChunk> materialChunks,
                                           List<ExtractedConcept> extractedConcepts) {
        Set<Integer> knownSeqs = materialChunks.stream()
                .map(MaterialChunk::seq)
                .collect(Collectors.toSet());
        // 이 컴포넌트는 여러 업로드가 같이 쓰므로 개수는 필드가 아니라 호출마다 센다
        Map<Rejection, Integer> rejected = new EnumMap<>(Rejection.class);

        List<ExtractedConcept> tidied = tidy(extractedConcepts, rejected);
        List<ExtractedConcept> merged = mergeSameName(tidied);
        List<ExtractedConcept> linked = withKnownChunks(merged, knownSeqs);

        List<ExtractedConcept> kept = new ArrayList<>();
        for (ExtractedConcept concept : linked) {
            Optional<Rejection> rejection = findRejection(concept);
            if (rejection.isPresent()) {
                count(rejected, rejection.get());
                continue;
            }
            kept.add(concept);
        }

        // 강의 내용은 남기지 않고 개수만 남긴다. 받은 수 = 남긴 수 + 합친 수 + 버린 수다
        log.info("분석 개념 검증: 받은 개념 {}개, 남긴 개념 {}개, 합친 개념 {}개, 버린 개념 {}",
                extractedConcepts.size(), kept.size(), tidied.size() - merged.size(), rejected);
        return List.copyOf(kept);
    }

    // 합치기 전에 개념을 다듬는다. 이름으로 묶으므로 null 개념과 이름이 빈 개념은 여기서 빼고,
    // "TCP " 와 "TCP" 가 같은 이름이 되게 앞뒤 공백을 지운다. 목록의 null 과 중복도 여기서 정리해 둔다.
    // 이름이 겹치지 않는 개념은 merge 를 거치지 않아, 한 개념 안의 중복은 여기서 빼야 남지 않는다
    private List<ExtractedConcept> tidy(List<ExtractedConcept> extractedConcepts, Map<Rejection, Integer> rejected) {
        List<ExtractedConcept> result = new ArrayList<>();
        for (ExtractedConcept extractedConcept : extractedConcepts) {
            if (extractedConcept == null || isBlank(extractedConcept.name())) {
                count(rejected, Rejection.BLANK_NAME);
                continue;
            }
            result.add(new ExtractedConcept(extractedConcept.name().strip(), extractedConcept.description(),
                    nullToEmpty(extractedConcept.tags()).stream().distinct().toList(),
                    nullToEmpty(extractedConcept.chunkSeqs()).stream().distinct().toList()));
        }
        return result;
    }

    // 강의 앞뒤에 같은 주제가 나오면 AI 가 이름이 같은 개념을 둘로 나눠 줄 수 있다. 그대로 저장하면
    // (material_id, name) 유일 제약에 걸리고, 하나를 버리면 그쪽 페이지 연결과 태그가 사라져 이름으로 합친다.
    // tidy 를 거친 개념만 받는다. 이름이 비어 있지 않고 다듬어져 있어야 같은 이름끼리 묶인다
    private List<ExtractedConcept> mergeSameName(List<ExtractedConcept> extractedConcepts) {
        // 처음 나온 순서를 지켜, 개념이 자료에 나오는 순서대로 저장되게 한다
        Map<String, ExtractedConcept> mergedByName = new LinkedHashMap<>();
        for (ExtractedConcept extractedConcept : extractedConcepts) {
            mergedByName.merge(extractedConcept.name(), extractedConcept, this::merge);
        }
        return List.copyOf(mergedByName.values());
    }

    // 이름과 설명은 먼저 나온 것을 쓴다. 태그와 청크 번호는 합치고, 청크 번호는 concept_chunk 기본 키가
    // 겹치지 않게 중복을 뺀다
    private ExtractedConcept merge(ExtractedConcept first, ExtractedConcept second) {
        List<ConceptTag> tags = Stream.concat(first.tags().stream(), second.tags().stream())
                .distinct()
                .toList();
        List<Integer> chunkSeqs = Stream.concat(first.chunkSeqs().stream(), second.chunkSeqs().stream())
                .distinct()
                .toList();
        return new ExtractedConcept(first.name(), first.description(), tags, chunkSeqs);
    }

    // 보내지 않은 청크 번호는 연결할 청크가 없어 뺀다. 개념은 버리지 않고, 번호가 다 빠진 개념은 findRejection 이 버린다
    private List<ExtractedConcept> withKnownChunks(List<ExtractedConcept> extractedConcepts, Set<Integer> knownSeqs) {
        List<ExtractedConcept> result = new ArrayList<>();
        for (ExtractedConcept extractedConcept : extractedConcepts) {
            List<Integer> chunkSeqs = new ArrayList<>(extractedConcept.chunkSeqs());
            chunkSeqs.retainAll(knownSeqs);
            result.add(new ExtractedConcept(extractedConcept.name(), extractedConcept.description(),
                    extractedConcept.tags(), List.copyOf(chunkSeqs)));
        }
        return result;
    }

    // 처음 걸린 규칙 하나를 이유로 돌려주고, 모두 통과하면 비어 있다. 버리는 판정은 모두 여기서 한다
    private Optional<Rejection> findRejection(ExtractedConcept concept) {
        // 이름은 VARCHAR(255) 라 넘으면 저장 전체가 실패한다. 자르면 잘린 이름끼리 겹칠 수 있어 버린다
        if (concept.name().length() > MAX_CONCEPT_NAME_LENGTH) {
            return Optional.of(Rejection.NAME_TOO_LONG);
        }
        // 설명은 NOT NULL 컬럼이고, 자료별 화면에 그대로 보여 빈 설명은 개념을 정리하지 못한 것으로 본다
        if (isBlank(concept.description())) {
            return Optional.of(Rejection.BLANK_DESCRIPTION);
        }
        // 연결할 페이지가 없으면 화면에 페이지를 보여 줄 수 없고 그 개념으로 문제를 만들 수도 없다
        if (concept.chunkSeqs().isEmpty()) {
            return Optional.of(Rejection.NO_KNOWN_CHUNK);
        }
        return Optional.empty();
    }

    private boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private void count(Map<Rejection, Integer> rejected, Rejection rejection) {
        rejected.merge(rejection, 1, Integer::sum);
    }

    private <T> List<T> nullToEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }
}
