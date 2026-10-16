package com.woozoo.quiz.material.web;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 실제 PDF 를 multipart 로 올려 추출과 저장까지 거치고, 내 자료 목록으로 확인한다
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MaterialControllerTest {

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

    private static MockMultipartFile pdf(String filename) {
        return new MockMultipartFile("file", filename, "application/pdf",
                TestPdfs.withPages("TCP is connection oriented", "UDP is connectionless"));
    }

    private ResultActions upload(String token, MockMultipartHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions getMyMaterials(String token) throws Exception {
        return mockMvc.perform(get("/materials").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    @Test
    void PDF_를_올리면_201_과_자료_아이디를_받고_내_자료_목록에_나온다() throws Exception {
        upload(ownerToken, multipart("/materials").file(pdf("05.pdf")).param("title", "네트워크 5강"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.materialId").isNumber());

        getMyMaterials(ownerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("네트워크 5강"))
                .andExpect(jsonPath("$[0].originalFilename").value("05.pdf"))
                .andExpect(jsonPath("$[0].pageCount").value(2))
                .andExpect(jsonPath("$[0].createdAt").isString());
    }

    @Test
    void 제목_없이_올리면_파일_이름이_제목이_된다() throws Exception {
        upload(ownerToken, multipart("/materials").file(pdf("network-05.pdf")))
                .andExpect(status().isCreated());

        getMyMaterials(ownerToken)
                .andExpect(jsonPath("$[0].title").value("network-05"));
    }

    @Test
    void 남의_자료는_내_목록에_나오지_않는다() throws Exception {
        upload(ownerToken, multipart("/materials").file(pdf("05.pdf")));

        getMyMaterials(otherToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void PDF_가_아니면_400_INVALID_FILE_이다() throws Exception {
        MockMultipartFile notPdf = new MockMultipartFile("file", "fake.pdf", "application/pdf",
                "not a pdf".getBytes(StandardCharsets.UTF_8));

        upload(ownerToken, multipart("/materials").file(notPdf))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE"));
    }

    @Test
    void 파일이_없으면_400_INVALID_INPUT_이다() throws Exception {
        upload(ownerToken, multipart("/materials").param("title", "제목만"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 토큰이_없으면_401_이다() throws Exception {
        mockMvc.perform(multipart("/materials").file(pdf("05.pdf")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
