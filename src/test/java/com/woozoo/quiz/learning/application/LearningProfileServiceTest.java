package com.woozoo.quiz.learning.application;

import com.woozoo.quiz.learning.domain.CognitiveTypeStat;
import com.woozoo.quiz.learning.domain.ConceptStat;
import com.woozoo.quiz.learning.persistence.LearningProfileRepository;
import com.woozoo.quiz.quiz.domain.CognitiveType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 집계는 저장소 테스트가 실제 DB 로 보고, 여기서는 빠진 유형 채우기와 정답률·데이터 부족 계산만 본다
class LearningProfileServiceTest {

    private static final long USER_ID = 1L;
    private static final int MIN_ANSWERED_PER_TYPE = 5;
    private static final int MIN_ANSWERED_PER_CONCEPT = 3;
    private static final int WEAK_CONCEPT_LIMIT = 5;

    private final LearningProfileRepository repository = mock(LearningProfileRepository.class);
    private final LearningProfileService service = new LearningProfileService(
            repository, MIN_ANSWERED_PER_TYPE, MIN_ANSWERED_PER_CONCEPT, WEAK_CONCEPT_LIMIT);

    private List<CognitiveTypeScore> cognitiveTypeScoresOf(CognitiveTypeStat... stats) {
        when(repository.countByCognitiveType(USER_ID)).thenReturn(List.of(stats));
        return service.getMyCognitiveTypeScores(USER_ID);
    }

    private List<WeakConceptScore> weakConceptsOf(ConceptStat... stats) {
        when(repository.findWeakConcepts(USER_ID, MIN_ANSWERED_PER_CONCEPT, WEAK_CONCEPT_LIMIT)).thenReturn(List.of(stats));
        return service.getMyWeakConcepts(USER_ID);
    }

    // 오각형 꼭짓점이 빠지지 않게, 푼 적 없는 유형도 블룸 순서대로 다섯 개를 준다
    @Test
    void 푼_유형만_있어도_다섯_유형을_블룸_순서로_준다() {
        List<CognitiveTypeScore> scores = cognitiveTypeScoresOf(
                new CognitiveTypeStat(CognitiveType.APPLY, 2, 1),
                new CognitiveTypeStat(CognitiveType.REMEMBER, 6, 3));

        assertThat(scores)
                .extracting(CognitiveTypeScore::cognitiveType)
                .containsExactly(CognitiveType.REMEMBER, CognitiveType.UNDERSTAND, CognitiveType.APPLY,
                        CognitiveType.ANALYZE, CognitiveType.EVALUATE);
    }

    @Test
    void 정답률은_맞힌_수를_푼_수로_나누고_기준_이상_풀어야_데이터가_충분하다() {
        List<CognitiveTypeScore> scores = cognitiveTypeScoresOf(
                new CognitiveTypeStat(CognitiveType.REMEMBER, 6, 3),
                new CognitiveTypeStat(CognitiveType.UNDERSTAND, 5, 0),
                new CognitiveTypeStat(CognitiveType.APPLY, 4, 4));

        assertThat(scores.subList(0, 3))
                .extracting(CognitiveTypeScore::answeredCount, CognitiveTypeScore::correctAnswerCount,
                        CognitiveTypeScore::accuracy, CognitiveTypeScore::isEnoughData)
                .containsExactly(
                        tuple(6, 3, 0.5, true),
                        tuple(5, 0, 0.0, true),
                        tuple(4, 4, 1.0, false));
    }

    // 다 틀린 0% 와 안 풀어 본 것을 구분하려고 정답률을 비운다
    @Test
    void 안_푼_유형은_0_개이고_정답률이_없고_데이터가_부족하다() {
        assertThat(cognitiveTypeScoresOf()).hasSize(5).allSatisfy(score -> {
            assertThat(score.answeredCount()).isZero();
            assertThat(score.correctAnswerCount()).isZero();
            assertThat(score.accuracy()).isNull();
            assertThat(score.isEnoughData()).isFalse();
        });
    }

    @Test
    void 약한_개념은_저장소가_준_순서대로_정답률을_붙여_준다() {
        List<WeakConceptScore> weakConcepts = weakConceptsOf(
                new ConceptStat(12L, "SXSSF", 3L, "파일처리-엑셀", 4, 0),
                new ConceptStat(7L, "셀 병합", 3L, "파일처리-엑셀", 4, 1));

        assertThat(weakConcepts)
                .extracting(WeakConceptScore::conceptId, WeakConceptScore::conceptName,
                        WeakConceptScore::materialId, WeakConceptScore::materialTitle,
                        WeakConceptScore::answeredCount, WeakConceptScore::correctAnswerCount, WeakConceptScore::accuracy)
                .containsExactly(
                        tuple(12L, "SXSSF", 3L, "파일처리-엑셀", 4, 0, 0.0),
                        tuple(7L, "셀 병합", 3L, "파일처리-엑셀", 4, 1, 0.25));
    }

    @Test
    void 설정한_최소_문제_수와_개수로_약한_개념을_찾는다() {
        weakConceptsOf();

        verify(repository).findWeakConcepts(USER_ID, MIN_ANSWERED_PER_CONCEPT, WEAK_CONCEPT_LIMIT);
    }
}
