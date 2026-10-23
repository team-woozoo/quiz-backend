package com.woozoo.quiz.learning.application;

import com.woozoo.quiz.global.error.BusinessException;
import com.woozoo.quiz.global.error.ErrorCode;
import com.woozoo.quiz.learning.domain.MaterialConceptStat;
import com.woozoo.quiz.learning.domain.MaterialStat;
import com.woozoo.quiz.learning.persistence.MaterialStudyRepository;
import com.woozoo.quiz.material.domain.MaterialSummary;
import com.woozoo.quiz.material.persistence.MaterialRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// 집계는 저장소 테스트가 실제 DB 로 보고, 여기서는 통계 붙이기, 틀린 수와 정답률, 개념 상태와 순서, 404 를 본다
class MaterialStudyServiceTest {

    private static final long USER_ID = 1L;
    private static final long MATERIAL_ID = 10L;
    private static final double WEAK_BELOW = 0.6;

    private final MaterialRepository materialRepository = mock(MaterialRepository.class);
    private final MaterialStudyRepository materialStudyRepository = mock(MaterialStudyRepository.class);
    private final MaterialStudyService service =
            new MaterialStudyService(materialRepository, materialStudyRepository, WEAK_BELOW);

    private static MaterialSummary material(long id) {
        return new MaterialSummary(id, "자료 " + id, id + ".pdf", 12, Instant.parse("2026-10-14T01:00:00Z"), false);
    }

    @Test
    void 자료_목록에_자료별_통계를_붙이고_퀴즈가_없는_자료는_0_이다() {
        when(materialRepository.findSummariesByOwnerId(USER_ID)).thenReturn(List.of(material(2), material(1)));
        when(materialStudyRepository.findMaterialStats(USER_ID)).thenReturn(List.of(new MaterialStat(1, 30, 25, 22)));

        assertThat(service.getMyMaterials(USER_ID))
                .extracting(overview -> overview.material().id(), MaterialOverview::questionCount,
                        MaterialOverview::correctCount, MaterialOverview::wrongCount, MaterialOverview::accuracy)
                .containsExactly(
                        tuple(2L, 0, 0, 0, null),
                        tuple(1L, 30, 22, 8, 22 / 30.0));
    }

    // 안 푼 5문제도 틀린 것으로 세어 정답 + 오답 = 문제 수가 된다
    @Test
    void 자료_상세의_틀린_수는_안_푼_문제까지_센다() {
        when(materialRepository.findSummary(MATERIAL_ID, USER_ID)).thenReturn(Optional.of(material(MATERIAL_ID)));
        when(materialStudyRepository.findMaterialStat(USER_ID, MATERIAL_ID))
                .thenReturn(Optional.of(new MaterialStat(MATERIAL_ID, 30, 25, 22)));

        MaterialOverview overview = service.getMyMaterial(USER_ID, MATERIAL_ID);

        assertThat(overview.answeredCount()).isEqualTo(25);
        assertThat(overview.wrongCount()).isEqualTo(8);
    }

    @Test
    void 없거나_남의_자료면_MATERIAL_NOT_FOUND_다() {
        when(materialRepository.findSummary(MATERIAL_ID, USER_ID)).thenReturn(Optional.empty());
        when(materialRepository.existsByIdAndOwnerId(MATERIAL_ID, USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.getMyMaterial(USER_ID, MATERIAL_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MATERIAL_NOT_FOUND);
        assertThatThrownBy(() -> service.getMyMaterialConcepts(USER_ID, MATERIAL_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MATERIAL_NOT_FOUND);
    }

    private List<MaterialConceptScore> conceptsOf(MaterialConceptStat... stats) {
        when(materialRepository.existsByIdAndOwnerId(MATERIAL_ID, USER_ID)).thenReturn(true);
        when(materialStudyRepository.findConceptStats(USER_ID, MATERIAL_ID)).thenReturn(List.of(stats));
        return service.getMyMaterialConcepts(USER_ID, MATERIAL_ID);
    }

    private static MaterialConceptStat stat(long id, int firstPage, int questions, int answered, int correct) {
        return new MaterialConceptStat(id, "개념 " + id, List.of(firstPage), questions, answered, correct);
    }

    // 기준(60%) 아래면 부족, 기준 이상이면 보통, 다 맞히면 완벽, 하나도 안 풀면 미확인이다
    @Test
    void 개념_상태는_안_푼_문제를_틀린_것으로_본_정답률로_정한다() {
        List<MaterialConceptScore> scores = conceptsOf(
                stat(1, 1, 3, 3, 3),
                stat(2, 2, 5, 5, 3),
                stat(3, 3, 3, 1, 1),
                stat(4, 4, 2, 0, 0));

        assertThat(scores).extracting(MaterialConceptScore::conceptId, MaterialConceptScore::status,
                        MaterialConceptScore::accuracy)
                .containsExactlyInAnyOrder(
                        tuple(1L, ConceptStatus.PERFECT, 1.0),
                        tuple(2L, ConceptStatus.GOOD, 0.6),
                        // 1문제만 풀어 맞혔지만 안 푼 2문제가 틀린 것으로 들어가 1/3 이다
                        tuple(3L, ConceptStatus.WEAK, 1 / 3.0),
                        tuple(4L, ConceptStatus.UNCHECKED, 0.0));
    }

    @Test
    void 문제가_없는_개념은_정답률_없이_미확인이다() {
        assertThat(conceptsOf(stat(1, 1, 0, 0, 0)))
                .singleElement()
                .satisfies(score -> {
                    assertThat(score.accuracy()).isNull();
                    assertThat(score.status()).isEqualTo(ConceptStatus.UNCHECKED);
                });
    }

    // 부족한 개념부터, 같은 상태면 정답률이 낮은 것, 그다음 앞 페이지에 나온 것부터 보여 준다
    @Test
    void 개념은_상태_정답률_첫_페이지_순으로_준다() {
        List<MaterialConceptScore> scores = conceptsOf(
                stat(1, 6, 2, 0, 0),
                stat(2, 7, 2, 2, 2),
                stat(3, 9, 4, 4, 1),
                stat(4, 4, 4, 4, 2),
                stat(5, 3, 2, 2, 2),
                stat(6, 5, 4, 4, 1));

        assertThat(scores).extracting(MaterialConceptScore::conceptId)
                .containsExactly(
                        6L, 3L,   // WEAK 1/4: 5쪽이 9쪽보다 앞
                        4L,       // WEAK 2/4
                        5L, 2L,   // PERFECT: 3쪽이 7쪽보다 앞
                        1L);      // UNCHECKED
    }
}
