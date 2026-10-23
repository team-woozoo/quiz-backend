package com.woozoo.quiz.learning.application;

import com.woozoo.quiz.global.error.BusinessException;
import com.woozoo.quiz.global.error.ErrorCode;
import com.woozoo.quiz.learning.domain.MaterialConceptStat;
import com.woozoo.quiz.learning.domain.MaterialStat;
import com.woozoo.quiz.learning.persistence.MaterialStudyRepository;
import com.woozoo.quiz.material.domain.MaterialSummary;
import com.woozoo.quiz.material.persistence.MaterialRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// 내 자료에 내 풀이 기록을 붙여 보여 준다. QuizHistoryService 와 같은 이유로 material 이 아니라 learning 에 둔다.
// material 에 두면 material 이 제출 기록을 알게 되어, 여기서 쓰는 learning → material 의존과 순환이 생긴다
@Service
public class MaterialStudyService {

    // 부족한 개념부터, 같은 상태면 정답률이 낮은 것부터, 그다음 앞 페이지에 나온 개념부터 보여 준다
    private static final Comparator<MaterialConceptScore> CONCEPT_ORDER =
            Comparator.comparing(MaterialConceptScore::status)
                    .thenComparing(MaterialConceptScore::accuracy, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(score -> score.pageNos().isEmpty() ? Integer.MAX_VALUE : score.pageNos().getFirst())
                    .thenComparing(MaterialConceptScore::conceptId);

    private final MaterialRepository materialRepository;
    private final MaterialStudyRepository materialStudyRepository;
    private final double weakAccuracyBelow;

    public MaterialStudyService(MaterialRepository materialRepository,
                                MaterialStudyRepository materialStudyRepository,
                                @Value("${learning.material.weak-accuracy-below}") double weakAccuracyBelow) {
        this.materialRepository = materialRepository;
        this.materialStudyRepository = materialStudyRepository;
        this.weakAccuracyBelow = weakAccuracyBelow;
    }

    public List<MaterialOverview> getMyMaterials(long userId) {
        Map<Long, MaterialStat> statByMaterial = materialStudyRepository.findMaterialStats(userId).stream()
                .collect(Collectors.toMap(MaterialStat::materialId, Function.identity()));

        return materialRepository.findSummariesByOwnerId(userId).stream()
                .map(material -> MaterialOverview.of(material,
                        statByMaterial.getOrDefault(material.id(), MaterialStat.empty(material.id()))))
                .toList();
    }

    public MaterialOverview getMyMaterial(long userId, long materialId) {
        MaterialSummary material = materialRepository.findSummary(materialId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MATERIAL_NOT_FOUND));
        MaterialStat stat = materialStudyRepository.findMaterialStat(userId, materialId)
                .orElse(MaterialStat.empty(materialId));
        return MaterialOverview.of(material, stat);
    }

    public List<MaterialConceptScore> getMyMaterialConcepts(long userId, long materialId) {
        if (!materialRepository.existsByIdAndOwnerId(materialId, userId)) {
            throw new BusinessException(ErrorCode.MATERIAL_NOT_FOUND);
        }
        return materialStudyRepository.findConceptStats(userId, materialId).stream()
                .map(this::toScore)
                .sorted(CONCEPT_ORDER)
                .toList();
    }

    private MaterialConceptScore toScore(MaterialConceptStat stat) {
        Double accuracy = stat.questionCount() == 0 ? null : (double) stat.correctCount() / stat.questionCount();
        return new MaterialConceptScore(stat.conceptId(), stat.conceptName(), stat.pageNos(),
                stat.questionCount(), stat.answeredCount(), stat.correctCount(), accuracy,
                ConceptStatus.of(stat.questionCount(), stat.answeredCount(), stat.correctCount(), weakAccuracyBelow));
    }
}
