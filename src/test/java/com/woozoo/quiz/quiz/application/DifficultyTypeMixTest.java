package com.woozoo.quiz.quiz.application;

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

// 고른 난이도의 유형별 문제 수가 생성 요청에 담기는지 본다. 생성 결과는 보지 않아 생성기는 실패하게 둔다
@Import({TestcontainersConfiguration.class, SyncTaskExecutorConfiguration.class})
@SpringBootTest(properties = {
        "quiz.generation.cognitive-mix.easy=REMEMBER:4,UNDERSTAND:3,APPLY:3",
        "quiz.generation.cognitive-mix.normal=REMEMBER:2,UNDERSTAND:2,APPLY:3,ANALYZE:2,EVALUATE:1",
        "quiz.generation.cognitive-mix.hard=REMEMBER:1,UNDERSTAND:1,APPLY:2,ANALYZE:3,EVALUATE:3"
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

    long userId;
    long materialId;

    @BeforeEach
    void setup() {
        userId = userRepository.save(new NewUser("mix@test.com", "1234", "홍길동"));
        materialId = materialRepository.save(new NewMaterial(userId, "네트워크 5강", "05.pdf", FileType.PDF, 1));
        materialRepository.saveChunks(materialId, List.of(new MaterialChunk(1, 1, "TCP는 연결 지향 프로토콜이다.")));
        given(quizGenerationPort.generateQuiz(any())).willThrow(new QuizGenerationException("확인만 한다"));
    }

    private Map<CognitiveType, Integer> requestedMix(Difficulty difficulty) {
        quizGenerationService.requestGeneration(
                new GenerateQuizCommand(userId, materialId, QuizFormat.MULTIPLE_CHOICE, difficulty));
        ArgumentCaptor<QuizGenerationRequest> sent = ArgumentCaptor.forClass(QuizGenerationRequest.class);
        verify(quizGenerationPort).generateQuiz(sent.capture());
        return sent.getValue().cognitiveTypeCounts();
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

    // NORMAL 과 같은 값이면 HARD 가 NORMAL 비율을 잘못 가져와도 통과해서, 이 테스트에서는 다른 값을 준다
    @Test
    void HARD_는_HARD_의_비율을_요청한다() {
        assertThat(requestedMix(Difficulty.HARD)).containsExactlyInAnyOrderEntriesOf(Map.of(
                CognitiveType.REMEMBER, 1, CognitiveType.UNDERSTAND, 1, CognitiveType.APPLY, 2,
                CognitiveType.ANALYZE, 3, CognitiveType.EVALUATE, 3));
    }
}
