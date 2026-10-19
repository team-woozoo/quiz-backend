package com.woozoo.quiz.quiz.web;

import com.jayway.jsonpath.JsonPath;
import com.woozoo.quiz.SyncTaskExecutorConfiguration;
import com.woozoo.quiz.TestcontainersConfiguration;
import com.woozoo.quiz.auth.domain.NewUser;
import com.woozoo.quiz.auth.persistence.UserRepository;
import com.woozoo.quiz.global.security.JwtProvider;
import com.woozoo.quiz.material.domain.FileType;
import com.woozoo.quiz.material.domain.MaterialChunk;
import com.woozoo.quiz.material.domain.NewMaterial;
import com.woozoo.quiz.material.persistence.MaterialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 보안 설정, 컨트롤러, 서비스, 가짜 생성기, DB 를 거쳐 생성 요청부터 작업 조회까지 본다
@Import({TestcontainersConfiguration.class, SyncTaskExecutorConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class QuizGenerationControllerTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JwtProvider jwtProvider;
    @Autowired
    UserRepository userRepository;
    @Autowired
    MaterialRepository materialRepository;
    @Autowired
    JdbcClient jdbcClient;
    @Value("${quiz.generation.daily-limit}")
    int dailyLimit;

    long ownerId;
    String ownerToken;
    String otherToken;
    long materialId;

    @BeforeEach
    void setup() {
        ownerId = userRepository.save(new NewUser("owner@test.com", "1234", "주인"));
        long otherId = userRepository.save(new NewUser("other@test.com", "1234", "남"));
        ownerToken = jwtProvider.createAccessToken(ownerId);
        otherToken = jwtProvider.createAccessToken(otherId);

        materialId = materialRepository.save(new NewMaterial(ownerId, "네트워크 5강", "05.pdf", FileType.PDF));
        materialRepository.saveChunks(materialId, List.of(
                new MaterialChunk(1, 1, "TCP는 연결 지향 프로토콜이다."),
                new MaterialChunk(2, 2, "UDP는 비연결형 프로토콜이다.")));
    }

    private ResultActions requestGeneration(String token, long materialId, String body) throws Exception {
        return mockMvc.perform(post("/materials/{materialId}/quizzes", materialId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private void insertJobs(int count, String status) {
        for (int i = 0; i < count; i++) {
            jdbcClient.sql("""
                            INSERT INTO generation_job (user_id, material_id, kind, format, difficulty, status)
                            VALUES (:userId, :materialId, 'NORMAL', 'OX', 'NORMAL', :status)
                            """)
                    .param("userId", ownerId)
                    .param("materialId", materialId)
                    .param("status", status)
                    .update();
        }
    }

    private ResultActions getJob(String token, long jobId) throws Exception {
        return mockMvc.perform(get("/jobs/{jobId}", jobId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    @Test
    void 생성을_요청하면_202_와_작업_아이디를_받고_조회하면_DONE_과_퀴즈_아이디가_온다() throws Exception {
        String body = requestGeneration(ownerToken, materialId, """
                {"format": "OX", "difficulty": "NORMAL"}
                """)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").isNumber())
                .andReturn().getResponse().getContentAsString();
        long jobId = ((Number) JsonPath.read(body, "$.jobId")).longValue();

        getJob(ownerToken, jobId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.quizId").isNumber())
                .andExpect(jsonPath("$.errorCode").doesNotExist());
    }

    @Test
    void 형식이나_난이도가_없거나_틀리면_400_이다() throws Exception {
        requestGeneration(ownerToken, materialId, """
                {"format": "OX"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        requestGeneration(ownerToken, materialId, """
                {"format": "ESSAY", "difficulty": "NORMAL"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 남의_자료로_요청하면_404_MATERIAL_NOT_FOUND_다() throws Exception {
        requestGeneration(otherToken, materialId, """
                {"format": "OX", "difficulty": "NORMAL"}
                """)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MATERIAL_NOT_FOUND"));
    }

    @Test
    void 남의_작업을_조회하면_404_JOB_NOT_FOUND_다() throws Exception {
        String body = requestGeneration(ownerToken, materialId, """
                {"format": "OX", "difficulty": "NORMAL"}
                """)
                .andReturn().getResponse().getContentAsString();
        long jobId = ((Number) JsonPath.read(body, "$.jobId")).longValue();

        getJob(otherToken, jobId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
    }

    @Test
    void 토큰이_없으면_401_이다() throws Exception {
        mockMvc.perform(post("/materials/{materialId}/quizzes", materialId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"format": "OX", "difficulty": "NORMAL"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 오늘_한도만큼_만들었으면_429_DAILY_GENERATION_LIMIT_EXCEEDED_다() throws Exception {
        insertJobs(dailyLimit, "DONE");

        requestGeneration(ownerToken, materialId, """
                {"format": "OX", "difficulty": "NORMAL"}
                """)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("DAILY_GENERATION_LIMIT_EXCEEDED"));
    }

    @Test
    void 진행_중인_작업이_있으면_409_GENERATION_IN_PROGRESS_다() throws Exception {
        insertJobs(1, "PENDING");

        requestGeneration(ownerToken, materialId, """
                {"format": "OX", "difficulty": "NORMAL"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GENERATION_IN_PROGRESS"));
    }
}
