package com.woozoo.quiz.quiz.application;

import com.woozoo.quiz.SyncTaskExecutorConfiguration;
import com.woozoo.quiz.TestcontainersConfiguration;
import com.woozoo.quiz.auth.domain.NewUser;
import com.woozoo.quiz.auth.persistence.UserRepository;
import com.woozoo.quiz.global.error.ErrorCode;
import com.woozoo.quiz.material.domain.FileType;
import com.woozoo.quiz.material.domain.MaterialChunk;
import com.woozoo.quiz.material.domain.NewMaterial;
import com.woozoo.quiz.material.persistence.MaterialRepository;
import com.woozoo.quiz.quiz.domain.CognitiveType;
import com.woozoo.quiz.quiz.domain.Difficulty;
import com.woozoo.quiz.quiz.domain.GenerationJob;
import com.woozoo.quiz.quiz.domain.GenerationStatus;
import com.woozoo.quiz.quiz.domain.QuizFormat;
import com.woozoo.quiz.quiz.port.JudgedQuestionType;
import com.woozoo.quiz.quiz.port.QuestionTypeJudgeException;
import com.woozoo.quiz.quiz.port.QuestionTypeJudgePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

// 판정 결과가 작업 상태와 저장되는 문제에 그대로 이어지는지 본다. 생성기는 가짜라 모든 문제가 REMEMBER 다
@Import({TestcontainersConfiguration.class, SyncTaskExecutorConfiguration.class})
@SpringBootTest
@Transactional
class QuestionTypeJudgeFlowTest {

    @MockitoBean
    QuestionTypeJudgePort questionTypeJudgePort;

    @Autowired
    QuizGenerationService quizGenerationService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    MaterialRepository materialRepository;
    @Autowired
    JdbcClient jdbcClient;

    long userId;
    long materialId;

    @BeforeEach
    void setup() {
        userId = userRepository.save(new NewUser("judge@test.com", "1234", "홍길동"));
        materialId = materialRepository.save(new NewMaterial(userId, "네트워크 5강", "05.pdf", FileType.PDF, 30));
        materialRepository.saveChunks(materialId, List.of(
                new MaterialChunk(1, 1, "TCP는 연결 지향 프로토콜이다."),
                new MaterialChunk(2, 2, "UDP는 비연결형 프로토콜이다."),
                new MaterialChunk(3, 3, "IP는 패킷을 목적지까지 전달한다.")));
    }

    private GenerationJob requestAndGetJob() {
        long jobId = quizGenerationService.requestGeneration(
                new GenerateQuizCommand(userId, materialId, QuizFormat.OX, Difficulty.NORMAL));
        return quizGenerationService.getJob(userId, jobId);
    }

    private long questionCount(long quizId) {
        return jdbcClient.sql("SELECT count(*) FROM question WHERE quiz_id = :quizId")
                .param("quizId", quizId)
                .query(Long.class)
                .single();
    }

    private long quizCount() {
        return jdbcClient.sql("SELECT count(*) FROM quiz WHERE user_id = :userId")
                .param("userId", userId)
                .query(Long.class)
                .single();
    }

    @Test
    void 판정이_일치한_문제만_저장하고_작업은_DONE_이_된다() {
        given(questionTypeJudgePort.judge(any())).willReturn(List.of(
                new JudgedQuestionType(0, null, CognitiveType.REMEMBER),
                new JudgedQuestionType(1, null, CognitiveType.UNDERSTAND),
                new JudgedQuestionType(2, null, CognitiveType.REMEMBER)));

        GenerationJob job = requestAndGetJob();

        assertThat(job.status()).isEqualTo(GenerationStatus.DONE);
        assertThat(questionCount(job.quizId())).isEqualTo(2);
    }

    @Test
    void 모두_불일치하면_NO_VALID_QUESTIONS_가_되고_퀴즈는_남지_않는다() {
        given(questionTypeJudgePort.judge(any())).willReturn(List.of(
                new JudgedQuestionType(0, null, CognitiveType.APPLY),
                new JudgedQuestionType(1, null, CognitiveType.APPLY),
                new JudgedQuestionType(2, null, CognitiveType.APPLY)));

        GenerationJob job = requestAndGetJob();

        assertThat(job.status()).isEqualTo(GenerationStatus.FAILED);
        assertThat(job.errorCode()).isEqualTo(ErrorCode.NO_VALID_QUESTIONS.name());
        assertThat(quizCount()).isZero();
    }

    @Test
    void 판정_호출이_실패해도_문제를_모두_저장하고_작업은_DONE_이_된다() {
        given(questionTypeJudgePort.judge(any())).willThrow(new QuestionTypeJudgeException("호출 실패"));

        GenerationJob job = requestAndGetJob();

        assertThat(job.status()).isEqualTo(GenerationStatus.DONE);
        assertThat(questionCount(job.quizId())).isEqualTo(3);
    }
}
