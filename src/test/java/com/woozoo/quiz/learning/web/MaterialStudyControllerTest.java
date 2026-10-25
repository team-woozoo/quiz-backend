package com.woozoo.quiz.learning.web;

import com.woozoo.quiz.TestcontainersConfiguration;
import com.woozoo.quiz.auth.persistence.UserRepository;
import com.woozoo.quiz.concept.persistence.MaterialConceptRepository;
import com.woozoo.quiz.global.security.JwtProvider;
import com.woozoo.quiz.learning.QuizFixture;
import com.woozoo.quiz.material.domain.FileType;
import com.woozoo.quiz.material.domain.NewMaterial;
import com.woozoo.quiz.material.persistence.MaterialRepository;
import com.woozoo.quiz.quiz.domain.Difficulty;
import com.woozoo.quiz.quiz.domain.NewGenerationJob;
import com.woozoo.quiz.quiz.domain.QuizFormat;
import com.woozoo.quiz.quiz.domain.QuizKind;
import com.woozoo.quiz.quiz.persistence.GenerationJobRepository;
import com.woozoo.quiz.quiz.persistence.QuizRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 제출 API 로 푼 기록이 자료 목록, 자료 상세, 자료의 개념 상태에 어떻게 나오는지 API 로만 이어서 본다.
// QuizFixture 는 퀴즈마다 "네트워크 5강" 자료(1쪽)와 TCP 개념을 새로 만든다
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MaterialStudyControllerTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JwtProvider jwtProvider;
    @Autowired
    UserRepository userRepository;
    @Autowired
    MaterialRepository materialRepository;
    @Autowired
    MaterialConceptRepository materialConceptRepository;
    @Autowired
    QuizRepository quizRepository;
    @Autowired
    GenerationJobRepository generationJobRepository;
    @Autowired
    JdbcClient jdbcClient;

    QuizFixture fixture;
    long userId;
    String token;

    @BeforeEach
    void setup() {
        fixture = new QuizFixture(userRepository, materialRepository, materialConceptRepository, quizRepository);
        userId = fixture.createUser("material@test.com");
        token = jwtProvider.createAccessToken(userId);
    }

    private ResultActions getWithToken(String path, Object... uriVariables) throws Exception {
        return mockMvc.perform(get(path, uriVariables).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private long materialIdOf(QuizFixture.Created quiz) {
        return jdbcClient.sql("SELECT material_id FROM quiz WHERE id = :id")
                .param("id", quiz.quizId())
                .query(Long.class)
                .single();
    }

    // 첫 문제만 맞히고 나머지는 보내지 않는다. 보내지 않은 문제는 서버가 틀린 것으로 채점한다
    private void submitFirstCorrect(QuizFixture.Created quiz) throws Exception {
        long first = quiz.questionIds().getFirst();
        mockMvc.perform(post("/quizzes/{quizId}/submissions", quiz.quizId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\": [{\"questionId\": %d, \"optionId\": %d}]}"
                                .formatted(first, quiz.correctOptionOf(first))))
                .andExpect(status().isCreated());
    }

    @Test
    void 내_자료_목록은_자료마다_문제_수와_정답_오답_정답률을_준다() throws Exception {
        QuizFixture.Created quiz = fixture.createQuiz(userId, 4);
        submitFirstCorrect(quiz);
        long untouched = materialRepository.save(new NewMaterial(userId, "운영체제 6주차", "06.pdf", FileType.PDF, 30));

        getWithToken("/materials")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // 퀴즈를 만든 적 없는 자료는 0 이고 정답률이 없다
                .andExpect(jsonPath("$[0].materialId").value(untouched))
                .andExpect(jsonPath("$[0].questionCount").value(0))
                .andExpect(jsonPath("$[0].accuracy").value(nullValue()))
                .andExpect(jsonPath("$[1].materialId").value(materialIdOf(quiz)))
                .andExpect(jsonPath("$[1].title").value("네트워크 5강"))
                .andExpect(jsonPath("$[1].pageCount").value(1))
                .andExpect(jsonPath("$[1].createdAt").isString())
                .andExpect(jsonPath("$[1].questionCount").value(4))
                .andExpect(jsonPath("$[1].answeredCount").value(4))
                .andExpect(jsonPath("$[1].correctCount").value(1))
                .andExpect(jsonPath("$[1].wrongCount").value(3))
                .andExpect(jsonPath("$[1].accuracy").value(0.25))
                .andExpect(jsonPath("$[1].generating").value(false));
    }

    @Test
    void 퀴즈를_만드는_중인_자료는_generating_이_true_다() throws Exception {
        long materialId = materialRepository.save(new NewMaterial(userId, "데이터베이스 정규화", "db.pdf", FileType.PDF, 30));
        generationJobRepository.create(new NewGenerationJob(userId, materialId, QuizKind.NORMAL,
                QuizFormat.MULTIPLE_CHOICE, Difficulty.NORMAL));

        getWithToken("/materials")
                .andExpect(jsonPath("$[0].generating").value(true));
        getWithToken("/materials/{materialId}", materialId)
                .andExpect(jsonPath("$.generating").value(true));
    }

    @Test
    void 자료_상세는_자료_정보와_문제_정답_오답_수를_준다() throws Exception {
        QuizFixture.Created quiz = fixture.createQuiz(userId, 4);
        submitFirstCorrect(quiz);

        getWithToken("/materials/{materialId}", materialIdOf(quiz))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("네트워크 5강"))
                .andExpect(jsonPath("$.pageCount").value(1))
                .andExpect(jsonPath("$.questionCount").value(4))
                .andExpect(jsonPath("$.correctCount").value(1))
                .andExpect(jsonPath("$.wrongCount").value(3));
    }

    @Test
    void 자료의_개념마다_나온_페이지와_정답률과_상태를_준다() throws Exception {
        QuizFixture.Created quiz = fixture.createQuiz(userId, 4);
        long materialId = materialIdOf(quiz);
        long conceptId = jdbcClient.sql("SELECT id FROM material_concept WHERE material_id = :id")
                .param("id", materialId).query(Long.class).single();
        materialConceptRepository.linkChunk(conceptId, materialRepository.findChunks(materialId).getFirst().id());
        submitFirstCorrect(quiz);

        getWithToken("/materials/{materialId}/concepts", materialId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].conceptId").value(conceptId))
                .andExpect(jsonPath("$[0].conceptName").value("TCP"))
                .andExpect(jsonPath("$[0].pageNos", contains(1)))
                .andExpect(jsonPath("$[0].questionCount").value(4))
                .andExpect(jsonPath("$[0].correctCount").value(1))
                .andExpect(jsonPath("$[0].accuracy").value(0.25))
                .andExpect(jsonPath("$[0].status").value("WEAK"));
    }

    @Test
    void 안_푼_개념은_UNCHECKED_다() throws Exception {
        QuizFixture.Created quiz = fixture.createQuiz(userId, 2);

        getWithToken("/materials/{materialId}/concepts", materialIdOf(quiz))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].answeredCount").value(0))
                .andExpect(jsonPath("$[0].status").value("UNCHECKED"));
    }

    @Test
    void 남의_자료의_상세와_개념은_404_MATERIAL_NOT_FOUND_다() throws Exception {
        QuizFixture.Created others = fixture.createQuiz("other@test.com", 1);
        long othersMaterialId = materialIdOf(others);

        getWithToken("/materials/{materialId}", othersMaterialId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MATERIAL_NOT_FOUND"));
        getWithToken("/materials/{materialId}/concepts", othersMaterialId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MATERIAL_NOT_FOUND"));
    }

    @Test
    void 토큰이_없으면_401_이다() throws Exception {
        mockMvc.perform(get("/materials/{materialId}", 1))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/materials/{materialId}/concepts", 1))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
