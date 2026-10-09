package com.woozoo.quiz.global.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 실제 SecurityConfig 와 JwtFilter 를 거쳐 요청이 막히고 통과하는지 본다
@WebMvcTest(controllers = SecurityConfigTest.TestController.class)
@Import({SecurityConfig.class, JwtProvider.class, SecurityConfigTest.TestController.class})
class SecurityConfigTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtProvider jwtProvider;

    @Test
    void 토큰_없이_보호된_주소를_부르면_401_과_UNAUTHORIZED_로_응답한다() throws Exception {
        mockMvc.perform(get("/test/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 올바른_토큰이면_통과하고_토큰의_사용자_아이디가_컨트롤러에_전달된다() throws Exception {
        String token = jwtProvider.createAccessToken(42L);

        mockMvc.perform(get("/test/protected")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("42"));
    }

    @Test
    void 잘못된_토큰이면_401_로_응답한다() throws Exception {
        mockMvc.perform(get("/test/protected")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void Bearer_접두사가_없으면_토큰으로_보지_않는다() throws Exception {
        String token = jwtProvider.createAccessToken(42L);

        mockMvc.perform(get("/test/protected")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 인증_주소는_토큰_없이_열린다() throws Exception {
        mockMvc.perform(get("/auth/test-open"))
                .andExpect(status().isOk());
    }

    @RestController
    static class TestController {

        @GetMapping("/test/protected")
        Long protectedEndpoint(@AuthenticationPrincipal Long userId) {
            return userId;
        }

        @GetMapping("/auth/test-open")
        String openEndpoint() {
            return "ok";
        }
    }
}
