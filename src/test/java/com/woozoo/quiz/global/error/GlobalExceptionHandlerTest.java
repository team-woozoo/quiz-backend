package com.woozoo.quiz.global.error;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 예외 응답의 모양만 본다. 인증은 보안 테스트에서 따로 확인하므로 보안 필터는 끈다
@WebMvcTest(controllers = GlobalExceptionHandlerTest.ThrowingController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandlerTest.ThrowingController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void 업무_예외는_ErrorCode_의_상태와_메시지와_코드로_응답한다() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("EXTRACTION_FAILED"))
                .andExpect(jsonPath("$.detail").value(ErrorCode.EXTRACTION_FAILED.getMessage()));
    }

    @Test
    void 예상하지_못한_예외는_내부_메시지를_숨기고_500으로_응답한다() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value(ErrorCode.INTERNAL_ERROR.getMessage()))
                .andExpect(jsonPath("$.detail").value(not(containsString("secret"))));
    }

    // Exception 핸들러가 Spring 예외까지 500 으로 바꾸지 않는지 본다
    @Test
    void 없는_주소는_404_를_유지하고_코드를_붙인다() throws Exception {
        mockMvc.perform(get("/test/no-such-path"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 지원하지_않는_메서드는_405_를_유지하고_코드를_붙인다() throws Exception {
        mockMvc.perform(post("/test/business"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 업로드_크기_초과는_413_과_FILE_TOO_LARGE_로_응답한다() throws Exception {
        mockMvc.perform(get("/test/too-large"))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/test/business")
        void business() {
            throw new BusinessException(ErrorCode.EXTRACTION_FAILED);
        }

        @GetMapping("/test/unexpected")
        void unexpected() {
            throw new IllegalStateException("secret: SELECT * FROM users");
        }

        @GetMapping("/test/too-large")
        void tooLarge() {
            throw new MaxUploadSizeExceededException(1);
        }
    }
}
