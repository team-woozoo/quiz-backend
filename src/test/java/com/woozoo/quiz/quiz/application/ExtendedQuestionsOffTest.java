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

// 운영 기본값처럼 심화를 끄면, HARD 도 지금까지처럼 근거 문제를 NORMAL 비율로 요청하는지 본다
@Import({TestcontainersConfiguration.class, SyncTaskExecutorConfiguration.class})
@SpringBootTest(properties = {
        "quiz.generation.cognitive-mix.normal=REMEMBER:2,UNDERSTAND:2,APPLY:3,ANALYZE:2,EVALUATE:1",
        "quiz.generation.cognitive-mix.hard=APPLY:3,ANALYZE:4,EVALUATE:3",
        "quiz.generation.extended.enabled=false"
})
@Transactional
class ExtendedQuestionsOffTest {

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
        userId = userRepository.save(new NewUser("off@test.com", "1234", "홍길동"));
        materialId = materialRepository.save(new NewMaterial(userId, "네트워크 5강", "05.pdf", FileType.PDF, 1));
        materialRepository.saveChunks(materialId, List.of(new MaterialChunk(1, 1, "TCP는 연결 지향 프로토콜이다.")));
        given(quizGenerationPort.generateQuiz(any())).willThrow(new QuizGenerationException("확인만 한다"));
    }

    @Test
    void HARD_도_근거_문제를_NORMAL_비율로_요청한다() {
        quizGenerationService.requestGeneration(
                new GenerateQuizCommand(userId, materialId, QuizFormat.MULTIPLE_CHOICE, Difficulty.HARD));

        ArgumentCaptor<QuizGenerationRequest> sent = ArgumentCaptor.forClass(QuizGenerationRequest.class);
        verify(quizGenerationPort).generateQuiz(sent.capture());
        assertThat(sent.getValue().scope()).isEqualTo(SourceScope.GROUNDED);
        assertThat(sent.getValue().cognitiveTypeCounts()).containsExactlyInAnyOrderEntriesOf(Map.of(
                CognitiveType.REMEMBER, 2, CognitiveType.UNDERSTAND, 2, CognitiveType.APPLY, 3,
                CognitiveType.ANALYZE, 2, CognitiveType.EVALUATE, 1));
    }
}
