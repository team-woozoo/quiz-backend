package com.woozoo.quiz.quiz.web;

import com.jayway.jsonpath.JsonPath;
import com.woozoo.quiz.SyncTaskExecutorConfiguration;
import com.woozoo.quiz.TestcontainersConfiguration;
import com.woozoo.quiz.auth.domain.NewUser;
import com.woozoo.quiz.auth.persistence.UserRepository;
import com.woozoo.quiz.global.error.ErrorCode;
import com.woozoo.quiz.global.security.JwtProvider;
import com.woozoo.quiz.material.domain.FileType;
import com.woozoo.quiz.material.domain.MaterialChunk;
import com.woozoo.quiz.material.domain.NewMaterial;
import com.woozoo.quiz.material.persistence.MaterialRepository;
import com.woozoo.quiz.quiz.application.JobStatusChanged;
import com.woozoo.quiz.quiz.domain.Difficulty;
import com.woozoo.quiz.quiz.domain.NewGenerationJob;
import com.woozoo.quiz.quiz.domain.QuizFormat;
import com.woozoo.quiz.quiz.domain.QuizKind;
import com.woozoo.quiz.quiz.persistence.GenerationJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 보안 설정, 구독 API, 레지스트리, 리스너를 거쳐 앱이 받는 이벤트 본문을 확인한다
@Import({TestcontainersConfiguration.class, SyncTaskExecutorConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class JobEventsControllerTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JwtProvider jwtProvider;
    @Autowired
    UserRepository userRepository;
    @Autowired
    MaterialRepository materialRepository;
    @Autowired
    GenerationJobRepository generationJobRepository;
    @Autowired
    JobEmitterRegistry jobEmitterRegistry;
    @Autowired
    ApplicationEventPublisher eventPublisher;

    long ownerId;
    String ownerToken;
    String otherToken;
    long materialId;

    @BeforeEach
    void setup() {
        ownerId = userRepository.save(new NewUser("owner@test.com", "1234", "주인"));
        ownerToken = jwtProvider.createAccessToken(ownerId);
        otherToken = jwtProvider.createAccessToken(userRepository.save(new NewUser("other@test.com", "1234", "남")));

        materialId = materialRepository.save(new NewMaterial(ownerId, "네트워크 5강", "05.pdf", FileType.PDF, 30));
        materialRepository.saveChunks(materialId, List.of(new MaterialChunk(1, 1, "TCP는 연결 지향 프로토콜이다.")));
    }

    // 생성기를 거치지 않고 PENDING 작업만 만든다. 상태는 테스트가 직접 바꾼다
    private long createPendingJob() {
        return generationJobRepository.create(
                new NewGenerationJob(ownerId, materialId, QuizKind.NORMAL, QuizFormat.OX, Difficulty.NORMAL));
    }

    private MvcResult subscribe(String token, long jobId) throws Exception {
        return mockMvc.perform(get("/jobs/{jobId}/events", jobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    @Test
    void 이미_끝난_작업을_구독하면_DONE_과_퀴즈_아이디를_한_번_받고_닫힌다() throws Exception {
        // 테스트에서는 생성이 같은 스레드에서 끝나, 요청이 돌아오면 이미 DONE 이다
        String requested = mockMvc.perform(post("/materials/{materialId}/quizzes", materialId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"format": "OX", "difficulty": "NORMAL"}
                                """))
                .andReturn().getResponse().getContentAsString();
        long jobId = ((Number) JsonPath.read(requested, "$.jobId")).longValue();

        MvcResult result = subscribe(ownerToken, jobId);

        assertThat(result.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(body(result))
                .contains("event:status")
                .contains("\"jobId\":" + jobId)
                .contains("\"status\":\"DONE\"")
                .containsPattern("\"quizId\":\\d+");
        // 반환 전에 닫힌 emitter 는 완료 콜백이 등록되지 않는다. 그래도 레지스트리에 남으면 안 된다
        assertThat(jobEmitterRegistry.find(jobId)).isEmpty();
    }

    @Test
    void 진행_중인_작업을_구독하면_현재_상태부터_바뀌는_상태를_차례로_받는다() throws Exception {
        long jobId = createPendingJob();

        MvcResult result = subscribe(ownerToken, jobId);
        assertThat(body(result)).contains("\"status\":\"PENDING\"");

        generationJobRepository.markProcessing(jobId);
        eventPublisher.publishEvent(new JobStatusChanged(jobId));
        assertThat(body(result)).contains("\"status\":\"PROCESSING\"");
        assertThat(jobEmitterRegistry.find(jobId)).isPresent();

        generationJobRepository.markFailed(jobId, ErrorCode.GENERATION_FAILED);
        eventPublisher.publishEvent(new JobStatusChanged(jobId));
        assertThat(body(result))
                .contains("\"status\":\"FAILED\"")
                .contains("\"errorCode\":\"GENERATION_FAILED\"");

        // 상태는 보낸 순서대로 쌓인다
        String events = body(result);
        assertThat(events.indexOf("PENDING")).isLessThan(events.indexOf("PROCESSING"));
        assertThat(events.indexOf("PROCESSING")).isLessThan(events.indexOf("FAILED"));
    }

    // 응답을 마무리하는 ASYNC 디스패치는 JwtFilter 를 다시 거치지 않는다. 보안 설정이 막으면 401 이 된다
    @Test
    void 끝난_연결을_마무리하는_비동기_디스패치는_막히지_않는다() throws Exception {
        long jobId = createPendingJob();
        MvcResult result = subscribe(ownerToken, jobId);

        generationJobRepository.markFailed(jobId, ErrorCode.GENERATION_FAILED);
        eventPublisher.publishEvent(new JobStatusChanged(jobId));

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk());
    }

    @Test
    void 남의_작업은_404_이고_연결을_등록하지_않는다() throws Exception {
        long jobId = createPendingJob();

        mockMvc.perform(get("/jobs/{jobId}/events", jobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));

        // 등록됐다면 진짜 주인의 연결을 밀어낼 수 있다
        assertThat(jobEmitterRegistry.find(jobId)).isEmpty();
    }

    @Test
    void 다시_구독하면_새_연결이_옛_연결을_대신한다() throws Exception {
        long jobId = createPendingJob();

        MvcResult first = subscribe(ownerToken, jobId);
        MvcResult second = subscribe(ownerToken, jobId);

        generationJobRepository.markProcessing(jobId);
        eventPublisher.publishEvent(new JobStatusChanged(jobId));

        assertThat(body(second)).contains("\"status\":\"PROCESSING\"");
        assertThat(body(first)).doesNotContain("PROCESSING");
        assertThat(jobEmitterRegistry.find(jobId)).isPresent();
    }

    @Test
    void 토큰이_없으면_401_이다() throws Exception {
        long jobId = createPendingJob();

        mockMvc.perform(get("/jobs/{jobId}/events", jobId).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());
    }
}
