package com.woozoo.quiz.quiz.application;

import com.woozoo.quiz.SyncTaskExecutorConfiguration;
import com.woozoo.quiz.TestcontainersConfiguration;
import com.woozoo.quiz.auth.domain.NewUser;
import com.woozoo.quiz.auth.persistence.UserRepository;
import com.woozoo.quiz.concept.persistence.MaterialConceptRepository;
import com.woozoo.quiz.global.error.BusinessException;
import com.woozoo.quiz.global.error.ErrorCode;
import com.woozoo.quiz.material.domain.FileType;
import com.woozoo.quiz.material.domain.MaterialChunk;
import com.woozoo.quiz.material.domain.NewMaterial;
import com.woozoo.quiz.material.domain.StoredChunk;
import com.woozoo.quiz.material.persistence.MaterialRepository;
import com.woozoo.quiz.quiz.domain.CognitiveType;
import com.woozoo.quiz.quiz.domain.Difficulty;
import com.woozoo.quiz.quiz.domain.GenerationJob;
import com.woozoo.quiz.quiz.domain.GenerationStatus;
import com.woozoo.quiz.quiz.domain.NewQuestion;
import com.woozoo.quiz.quiz.domain.NewQuiz;
import com.woozoo.quiz.quiz.domain.QuizFormat;
import com.woozoo.quiz.quiz.domain.QuizKind;
import com.woozoo.quiz.quiz.persistence.QuizRepository;
import com.woozoo.quiz.quiz.port.GeneratedConcept;
import com.woozoo.quiz.quiz.port.GeneratedOption;
import com.woozoo.quiz.quiz.port.GeneratedQuestion;
import com.woozoo.quiz.quiz.port.GeneratedQuiz;
import com.woozoo.quiz.quiz.port.QuizGenerationPort;
import com.woozoo.quiz.quiz.port.QuizGenerationRequest;
import com.woozoo.quiz.quiz.port.SourceChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 약한 개념 하나로 퀴즈를 만들 때 생성기에 무엇을 보내고, 결과를 어디에 붙여 저장하는지 실제 DB 로 본다.
// 자료는 1·3·4쪽에 글자가 있고, "TCP 연결 과정" 은 1·4쪽, "UDP 특징" 은 3쪽에 나온다
@Import({TestcontainersConfiguration.class, SyncTaskExecutorConfiguration.class})
@SpringBootTest
@Transactional
class WeaknessQuizGenerationTest {

    private static final String PAGE_1 = "TCP는 연결 지향 프로토콜이다.";
    private static final String PAGE_3 = "UDP는 비연결형 프로토콜이다.";
    private static final String PAGE_4 = "TCP는 3-way handshake로 연결을 맺는다.";

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
    @Autowired
    QuizRepository quizRepository;
    @Autowired
    JdbcClient jdbcClient;
    @Value("${quiz.generation.weakness.question-count}")
    int weaknessQuestionCount;
    @Value("${quiz.generation.daily-limit}")
    int dailyLimit;

    long userId;
    long materialId;
    Map<Integer, Long> chunkIdByPage;
    long tcp;
    long udp;

    @BeforeEach
    void setup() {
        userId = userRepository.save(new NewUser("weakness@test.com", "1234", "홍길동"));
        materialId = materialRepository.save(new NewMaterial(userId, "네트워크 5강", "05.pdf", FileType.PDF, 30));
        materialRepository.saveChunks(materialId, List.of(
                new MaterialChunk(1, 1, PAGE_1), new MaterialChunk(2, 3, PAGE_3), new MaterialChunk(3, 4, PAGE_4)));
        List<StoredChunk> chunks = materialRepository.findChunks(materialId);
        chunkIdByPage = Map.of(1, chunks.get(0).id(), 3, chunks.get(1).id(), 4, chunks.get(2).id());

        tcp = materialConceptRepository.findOrCreate(materialId, "TCP 연결 과정", "연결을 맺고 끊는 과정");
        materialConceptRepository.linkChunk(tcp, chunkIdByPage.get(1));
        materialConceptRepository.linkChunk(tcp, chunkIdByPage.get(4));
        udp = materialConceptRepository.findOrCreate(materialId, "UDP 특징", "비연결형 전송");
        materialConceptRepository.linkChunk(udp, chunkIdByPage.get(3));
    }

    private static GeneratedQuestion question(String content, String quote, int seq) {
        return new GeneratedQuestion(content, quote, CognitiveType.APPLY, "해설",
                List.of(new GeneratedOption("O", true), new GeneratedOption("X", false)), seq);
    }

    // 보낸 번호로 겨냥한 개념의 문제 둘과, 같은 페이지에 있는 다른 개념의 문제 하나를 돌려준다
    private void generatorAnswersWithOtherConceptMixedIn() {
        given(quizGenerationPort.generateQuiz(any())).willReturn(new GeneratedQuiz(List.of(
                new GeneratedConcept("TCP 연결 과정", "연결을 맺고 끊는 과정", List.of(1, 2), List.of(
                        question("1쪽 문제", PAGE_1, 1),
                        question("4쪽 문제", PAGE_4, 2))),
                new GeneratedConcept("연결 지향", "다른 개념", List.of(1), List.of(
                        question("다른 개념 문제", PAGE_1, 1))))));
    }

    private GenerationJob requestWeakness(long conceptId) {
        long jobId = quizGenerationService.requestWeaknessGeneration(
                new GenerateWeaknessQuizCommand(userId, conceptId, QuizFormat.OX, Difficulty.NORMAL));
        return quizGenerationService.getJob(userId, jobId);
    }

    @Test
    void 개념이_나온_페이지만_새_번호로_보내고_겨냥한_개념과_이미_낸_문제를_넘긴다() {
        long earlierQuiz = quizRepository.save(new NewQuiz(userId, materialId, QuizKind.NORMAL, QuizFormat.OX, Difficulty.NORMAL));
        quizRepository.saveQuestion(new NewQuestion(earlierQuiz, 1, tcp, chunkIdByPage.get(1), PAGE_1,
                CognitiveType.REMEMBER, "이미 낸 TCP 문제 1", "해설"));
        quizRepository.saveQuestion(new NewQuestion(earlierQuiz, 2, udp, chunkIdByPage.get(3), PAGE_3,
                CognitiveType.REMEMBER, "UDP 문제", "해설"));
        quizRepository.saveQuestion(new NewQuestion(earlierQuiz, 3, tcp, chunkIdByPage.get(4), PAGE_4,
                CognitiveType.REMEMBER, "이미 낸 TCP 문제 2", "해설"));
        generatorAnswersWithOtherConceptMixedIn();

        requestWeakness(tcp);

        ArgumentCaptor<QuizGenerationRequest> sent = ArgumentCaptor.forClass(QuizGenerationRequest.class);
        verify(quizGenerationPort).generateQuiz(sent.capture());
        QuizGenerationRequest request = sent.getValue();
        // 자료의 seq(1, 3)가 아니라 보내는 순서대로 1, 2 다
        assertThat(request.chunks()).containsExactly(new SourceChunk(1, PAGE_1), new SourceChunk(2, PAGE_4));
        assertThat(request.questionCount()).isEqualTo(weaknessQuestionCount);
        assertThat(request.cognitiveTypeCounts().values().stream().mapToInt(Integer::intValue).sum())
                .isEqualTo(weaknessQuestionCount);
        assertThat(request.existingConcepts()).isEmpty();
        assertThat(request.focus().name()).isEqualTo("TCP 연결 과정");
        assertThat(request.focus().description()).isEqualTo("연결을 맺고 끊는 과정");
        assertThat(request.focus().askedQuestions()).containsExactly("이미 낸 TCP 문제 2", "이미 낸 TCP 문제 1");
    }

    // UDP 특징은 3쪽 한 장에만 나와, 같은 사실을 돌려 쓰지 않게 문제를 덜 내고 높은 유형부터 뺀다
    @Test
    void 페이지가_적은_개념은_페이지_수에_맞춰_문제를_덜_요청한다() {
        given(quizGenerationPort.generateQuiz(any())).willReturn(new GeneratedQuiz(List.of(
                new GeneratedConcept("UDP 특징", "비연결형 전송", List.of(1), List.of(question("3쪽 문제", PAGE_3, 1))))));

        requestWeakness(udp);

        ArgumentCaptor<QuizGenerationRequest> sent = ArgumentCaptor.forClass(QuizGenerationRequest.class);
        verify(quizGenerationPort).generateQuiz(sent.capture());
        assertThat(sent.getValue().chunks()).containsExactly(new SourceChunk(1, PAGE_3));
        assertThat(sent.getValue().questionCount()).isEqualTo(3);
        assertThat(sent.getValue().cognitiveTypeCounts()).containsOnlyKeys(
                CognitiveType.REMEMBER, CognitiveType.UNDERSTAND, CognitiveType.APPLY);
        assertThat(sent.getValue().cognitiveTypeCounts().values().stream().mapToInt(Integer::intValue).sum())
                .isEqualTo(3);
    }

    @Test
    void 만든_문제는_자료_없는_약점_퀴즈로_저장되고_원래_개념과_페이지에_붙는다() {
        generatorAnswersWithOtherConceptMixedIn();

        GenerationJob job = requestWeakness(tcp);

        assertThat(job.status()).isEqualTo(GenerationStatus.DONE);
        assertThat(job.kind()).isEqualTo(QuizKind.WEAKNESS);
        record SavedQuiz(String kind, Long materialId) {
        }
        assertThat(jdbcClient.sql("SELECT kind, material_id FROM quiz WHERE id = :id")
                .param("id", job.quizId()).query(SavedQuiz.class).single())
                .isEqualTo(new SavedQuiz("WEAKNESS", null));

        long userConceptId = jdbcClient.sql("SELECT user_concept_id FROM concept_mapping WHERE material_concept_id = :id")
                .param("id", tcp).query(Long.class).single();
        assertThat(job.userConceptId()).isEqualTo(userConceptId);
        assertThat(jdbcClient.sql("SELECT user_concept_id FROM weakness_target WHERE quiz_id = :id")
                .param("id", job.quizId()).query(Long.class).single())
                .isEqualTo(userConceptId);

        // 다른 개념의 문제는 버려지고, 남은 문제는 보낸 번호가 가리키는 실제 페이지와 원래 개념에 붙는다
        record SavedQuestion(String content, long materialConceptId, long sourceChunkId) {
        }
        assertThat(jdbcClient.sql("""
                        SELECT content, material_concept_id, source_chunk_id FROM question WHERE quiz_id = :id ORDER BY order_no
                        """)
                .param("id", job.quizId()).query(SavedQuestion.class).list())
                .containsExactly(
                        new SavedQuestion("1쪽 문제", tcp, chunkIdByPage.get(1)),
                        new SavedQuestion("4쪽 문제", tcp, chunkIdByPage.get(4)));
    }

    // 자료 개념마다 대표 개념은 하나다. 다시 요청해도 새로 만들지 않고 이어 둔 것을 쓴다
    @Test
    void 같은_개념으로_다시_요청하면_대표_개념을_새로_만들지_않는다() {
        generatorAnswersWithOtherConceptMixedIn();

        GenerationJob first = requestWeakness(tcp);
        GenerationJob second = requestWeakness(tcp);

        assertThat(second.userConceptId()).isEqualTo(first.userConceptId());
        assertThat(jdbcClient.sql("SELECT count(*) FROM user_concept WHERE user_id = :userId")
                .param("userId", userId).query(Long.class).single())
                .isEqualTo(1L);
    }

    @Test
    void 남의_개념이나_없는_개념이면_CONCEPT_NOT_FOUND_이고_작업을_만들지_않는다() {
        long otherId = userRepository.save(new NewUser("other@test.com", "1234", "남"));

        assertThatThrownBy(() -> quizGenerationService.requestWeaknessGeneration(
                new GenerateWeaknessQuizCommand(otherId, tcp, QuizFormat.OX, Difficulty.NORMAL)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONCEPT_NOT_FOUND);
        assertThatThrownBy(() -> quizGenerationService.requestWeaknessGeneration(
                new GenerateWeaknessQuizCommand(userId, Long.MAX_VALUE, QuizFormat.OX, Difficulty.NORMAL)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONCEPT_NOT_FOUND);
        assertThat(jdbcClient.sql("SELECT count(*) FROM generation_job").query(Long.class).single()).isZero();
    }

    @Test
    void 하루_생성_횟수는_자료로_만든_것과_함께_센다() {
        for (int i = 0; i < dailyLimit; i++) {
            jdbcClient.sql("""
                            INSERT INTO generation_job (user_id, material_id, kind, format, difficulty, status)
                            VALUES (:userId, :materialId, 'NORMAL', 'OX', 'NORMAL', 'DONE')
                            """)
                    .param("userId", userId)
                    .param("materialId", materialId)
                    .update();
        }

        assertThatThrownBy(() -> requestWeakness(tcp))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DAILY_GENERATION_LIMIT_EXCEEDED);
    }

    // 생성 결과에서 개념의 페이지가 모두 빠졌을 수 있다. 보낼 페이지가 없으면 생성기를 부르지 않는다
    @Test
    void 나온_페이지가_없는_개념이면_생성기를_부르지_않고_NO_VALID_QUESTIONS_로_실패한다() {
        long pageless = materialConceptRepository.findOrCreate(materialId, "페이지 없는 개념", "설명");

        GenerationJob job = requestWeakness(pageless);

        assertThat(job.status()).isEqualTo(GenerationStatus.FAILED);
        assertThat(job.errorCode()).isEqualTo(ErrorCode.NO_VALID_QUESTIONS.name());
        verify(quizGenerationPort, never()).generateQuiz(any());
    }
}
