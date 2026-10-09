package com.woozoo.quiz.auth.web;

import com.jayway.jsonpath.JsonPath;
import com.woozoo.quiz.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 실제 보안 설정, 컨트롤러, 서비스, DB 를 거쳐 가입부터 인증된 요청까지 한 흐름으로 본다
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    private ResultActions signup(String email, String password, String nickname) throws Exception {
        return mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s", "nickname": "%s"}
                        """.formatted(email, password, nickname)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password)));
    }

    @Test
    void 가입하고_로그인해서_받은_토큰으로_인증된_요청을_보낸다() throws Exception {
        signup("test@example.com", "correct-password", "tester")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber());

        String body = login("test@example.com", "correct-password")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String accessToken = JsonPath.read(body, "$.accessToken");

        // 아직 보호된 실제 API 가 없어 없는 주소를 쓴다. 토큰이 없으면 401, 있으면 인증을 통과해 404 가 난다
        mockMvc.perform(get("/materials"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/materials")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void 이메일_형식이_틀리면_400_과_INVALID_INPUT() throws Exception {
        signup("not-an-email", "correct-password", "tester")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 비밀번호가_8자보다_짧으면_400_과_INVALID_INPUT() throws Exception {
        signup("test@example.com", "short", "tester")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 같은_이메일로_다시_가입하면_409_와_EMAIL_ALREADY_EXISTS() throws Exception {
        signup("test@example.com", "correct-password", "tester");

        signup("test@example.com", "correct-password", "other")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void 틀린_비밀번호로_로그인하면_401_과_INVALID_CREDENTIALS() throws Exception {
        signup("test@example.com", "correct-password", "tester");

        login("test@example.com", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }
}
