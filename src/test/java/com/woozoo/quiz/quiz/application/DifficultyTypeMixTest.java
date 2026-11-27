package com.woozoo.quiz.quiz.application;

import com.woozoo.quiz.TestHashes;
import com.woozoo.quiz.ConceptFixture;
import com.woozoo.quiz.concept.persistence.MaterialConceptRepository;
import com.woozoo.quiz.SyncTaskExecutorConfiguration;
import com.woozoo.quiz.TestcontainersConfiguration;
import com.woozoo.quiz.auth.domain.NewUser;
import com.woozoo.quiz.auth.persistence.UserRepository;
import com.woozoo.quiz.material.domain.FileType;
import com.woozoo.quiz.material.domain.MaterialChunk;
import com.woozoo.quiz.material.domain.NewMaterial;
import com.woozoo.quiz.material.persistence.MaterialRepository;
import com.woozoo.quiz.quiz.domain.CognitiveType;
import com.woozoo.quiz.quiz.domain.Difficulty;
import com.woozoo.quiz.quiz.domain.QuizFormat;
import com.woozoo.quiz.quiz.domain.SourceScope;
import com.woozoo.quiz.quiz.port.QuizGenerationException;
import com.woozoo.quiz.quiz.port.QuizGenerationPort;
import com.woozoo.quiz.quiz.port.QuizGenerationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

// 고른 난이도의 유형별 문제 수와 문제 종류가 생성 요청에 담기는지 본다. 생성 결과는 보지 않아 생성기는 실패하게 둔다
@Import({TestcontainersConfiguration.class, SyncTaskExecutorConfiguration.class})
@SpringBootTest(properties = {
        "quiz.generation.cognitive-mix.easy=REMEMBER:4,UNDERSTAND:3,APPLY:3",
        "quiz.generation.cognitive-mix.normal=REMEMBER:2,UNDERSTAND:2,APPLY:3,ANALYZE:2,EVALUATE:1",
        "quiz.generation.cognitive-mix.hard=APPLY:3,ANALYZE:4,EVALUATE:3",
        "quiz.generation.extended.enabled=true"
})
@Transactional
class DifficultyTypeMixTest {

    @MockitoBean
    QuizGenerationPort quizGenerationPort;

    @Autowired
    QuizGenerationService quizGenerationService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    MaterialRepository materialRepository;
    @Autowired
    MaterialConceptRepository materialConceptRepository;

    long userId;
    long materialId;

    @BeforeEach
    void setup() {
        userId = userRepository.save(new NewUser("mix@test.com", "1234", "홍길동"));
        materialId = materialRepository.save(new NewMaterial(userId, "네트워크 5강", "05.pdf", FileType.PDF, 1, TestHashes.unique()));
        materialRepository.saveChunks(materialId, List.of(new MaterialChunk(1, 1, "TCP는 연결 지향 프로토콜이다.")));
        ConceptFixture.conceptPerChunk(materialRepository, materialConceptRepository, materialId);
        given(quizGenerationPort.generateQuiz(any())).willThrow(new QuizGenerationException("확인만 한다"));
    }

    private QuizGenerationRequest sentRequest(Difficulty difficulty) {
        quizGenerationService.requestGeneration(
                new GenerateQuizCommand(userId, materialId, QuizFormat.MULTIPLE_CHOICE, difficulty));
        ArgumentCaptor<QuizGenerationRequest> sent = ArgumentCaptor.forClass(QuizGenerationRequest.class);
        verify(quizGenerationPort).generateQuiz(sent.capture());
        return sent.getValue();
    }

    private Map<CognitiveType, Integer> requestedMix(Difficulty difficulty) {
        return sentRequest(difficulty).cognitiveTypeCounts();
    }

    @Test
    void EASY_는_기억_이해_적용만_요청한다() {
        assertThat(requestedMix(Difficulty.EASY)).containsExactlyInAnyOrderEntriesOf(Map.of(
                CognitiveType.REMEMBER, 4, CognitiveType.UNDERSTAND, 3, CognitiveType.APPLY, 3));
    }

    @Test
    void NORMAL_은_다섯_유형을_요청한다() {
        assertThat(requestedMix(Difficulty.NORMAL)).containsExactlyInAnyOrderEntriesOf(Map.of(
                CognitiveType.REMEMBER, 2, CognitiveType.UNDERSTAND, 2, CognitiveType.APPLY, 3,
                CognitiveType.ANALYZE, 2, CognitiveType.EVALUATE, 1));
    }

    // 심화 문제에는 기억과 이해를 두지 않는다
    @Test
    void HARD_는_적용_분석_평가만_요청한다() {
        assertThat(requestedMix(Difficulty.HARD)).containsExactlyInAnyOrderEntriesOf(Map.of(
                CognitiveType.APPLY, 3, CognitiveType.ANALYZE, 4, CognitiveType.EVALUATE, 3));
    }

    @Test
    void EASY_는_근거_문제로_요청한다() {
        assertThat(sentRequest(Difficulty.EASY).scope()).isEqualTo(SourceScope.GROUNDED);
    }

    @Test
    void NORMAL_은_근거_문제로_요청한다() {
        assertThat(sentRequest(Difficulty.NORMAL).scope()).isEqualTo(SourceScope.GROUNDED);
    }

    @Test
    void HARD_는_심화_문제로_요청한다() {
        assertThat(sentRequest(Difficulty.HARD).scope()).isEqualTo(SourceScope.EXTENDED);
    }
}
