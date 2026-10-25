package com.woozoo.quiz.quiz.web;

import com.jayway.jsonpath.JsonPath;
import com.woozoo.quiz.SyncTaskExecutorConfiguration;
import com.woozoo.quiz.TestPdfs;
import com.woozoo.quiz.TestcontainersConfiguration;
import com.woozoo.quiz.auth.domain.NewUser;
import com.woozoo.quiz.auth.persistence.UserRepository;
import com.woozoo.quiz.global.security.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 앱이 실제로 거치는 순서대로 PDF 업로드 → 생성 요청 → 작업 조회 → 퀴즈 조회를 API 로만 이어서 본다
@Import({TestcontainersConfiguration.class, SyncTaskExecutorConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class QuizControllerTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JwtProvider jwtProvider;
    @Autowired
    UserRepository userRepository;

    String ownerToken;
    String otherToken;

    @BeforeEach
    void setup() {
        ownerToken = jwtProvider.createAccessToken(userRepository.save(new NewUser("owner@test.com", "1234", "주인")));
        otherToken = jwtProvider.createAccessToken(userRepository.save(new NewUser("other@test.com", "1234", "남")));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private long uploadAndGenerate(String format) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "05.pdf", "application/pdf",
                TestPdfs.withPages("TCP is connection oriented", "UDP is connectionless"));
        String uploaded = mockMvc.perform(multipart("/materials").file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andReturn().getResponse().getContentAsString();
        long materialId = ((Number) JsonPath.read(uploaded, "$.materialId")).longValue();

        String requested = mockMvc.perform(post("/materials/{materialId}/quizzes", materialId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"format": "%s", "difficulty": "NORMAL"}
                                """.formatted(format)))
                .andReturn().getResponse().getContentAsString();
        long jobId = ((Number) JsonPath.read(requested, "$.jobId")).longValue();

        String job = mockMvc.perform(get("/jobs/{jobId}", jobId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(job, "$.quizId")).longValue();
    }

    @Test
    void 올린_PDF_로_만든_퀴즈를_문제_보기_근거와_함께_조회한다() throws Exception {
        long quizId = uploadAndGenerate("OX");

        mockMvc.perform(get("/quizzes/{quizId}", quizId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quizId").value(quizId))
                .andExpect(jsonPath("$.format").value("OX"))
                .andExpect(jsonPath("$.difficulty").value("NORMAL"))
                .andExpect(jsonPath("$.questions.length()").value(2))
                .andExpect(jsonPath("$.questions[0].orderNo").value(1))
                .andExpect(jsonPath("$.questions[0].sourceQuote").value("TCP is connection oriented"))
                .andExpect(jsonPath("$.questions[0].sourcePageNo").value(1))
                .andExpect(jsonPath("$.questions[0].totalPageCount").value(2))
                .andExpect(jsonPath("$.questions[0].conceptName").isString())
                .andExpect(jsonPath("$.questions[0].cognitiveType").value("REMEMBER"))
                .andExpect(jsonPath("$.questions[0].explanation").isString())
                .andExpect(jsonPath("$.questions[0].options.length()").value(2))
                .andExpect(jsonPath("$.questions[0].options[0].orderNo").value(1))
                .andExpect(jsonPath("$.questions[0].options[0].content").value("O"))
                .andExpect(jsonPath("$.questions[0].options[0].isCorrect").value(true))
                .andExpect(jsonPath("$.questions[0].options[1].isCorrect").value(false))
                .andExpect(jsonPath("$.questions[1].orderNo").value(2))
                .andExpect(jsonPath("$.questions[1].sourcePageNo").value(2))
                .andExpect(jsonPath("$.questions[1].totalPageCount").value(2));
    }

    @Test
    void 객관식은_문제마다_보기_4개가_순서대로_온다() throws Exception {
        long quizId = uploadAndGenerate("MULTIPLE_CHOICE");

        mockMvc.perform(get("/quizzes/{quizId}", quizId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].options.length()").value(4))
                .andExpect(jsonPath("$.questions[0].options[*].orderNo").value(
                        org.hamcrest.Matchers.contains(1, 2, 3, 4)));
    }

    @Test
    void 남의_퀴즈는_404_QUIZ_NOT_FOUND_다() throws Exception {
        long quizId = uploadAndGenerate("OX");

        mockMvc.perform(get("/quizzes/{quizId}", quizId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUIZ_NOT_FOUND"));
    }

    @Test
    void 없는_퀴즈는_404_QUIZ_NOT_FOUND_다() throws Exception {
        mockMvc.perform(get("/quizzes/{quizId}", -1)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUIZ_NOT_FOUND"));
    }

    @Test
    void 토큰이_없으면_401_이다() throws Exception {
        mockMvc.perform(get("/quizzes/{quizId}", 1))
                .andExpect(status().isUnauthorized());
    }
}
