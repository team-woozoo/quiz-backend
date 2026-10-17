package com.woozoo.quiz.global.config;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.woozoo.quiz.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

// 앱은 이 문서로 모델과 API 코드를 생성한다. 생성 결과가 바뀌는 부분(필수, null 허용, 이름)을 지킨다
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ApiDocsTest {

    @Autowired
    MockMvc mockMvc;

    DocumentContext docs;

    @BeforeEach
    void loadDocs() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
        docs = JsonPath.parse(body);
    }

    private Map<String, Object> schema(String name) {
        return docs.read("$.components.schemas." + name);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> property(String schema, String property) {
        return (Map<String, Object>) ((Map<String, Object>) schema(schema).get("properties")).get(property);
    }

    @Test
    void 생성기가_nullable_을_읽을_수_있게_OpenAPI_3_0_으로_낸다() {
        assertThat(docs.read("$.openapi", String.class)).startsWith("3.0");
    }

    @Test
    void 응답_스키마는_모든_필드가_필수다() {
        Map<String, Map<String, Object>> schemas = docs.read("$.components.schemas");

        assertThat(schemas).isNotEmpty();
        schemas.forEach((name, schema) -> {
            if (name.endsWith("Response")) {
                Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
                assertThat((List<String>) schema.get("required"))
                        .as(name)
                        .containsExactlyInAnyOrderElementsOf(properties.keySet());
            }
        });
    }

    @Test
    void 값이_없을_수_있는_필드만_nullable_이다() {
        assertThat(property("GenerationJobResponse", "quizId")).containsEntry("nullable", true);
        assertThat(property("GenerationJobResponse", "errorCode")).containsEntry("nullable", true);
        assertThat(property("QuizResponse", "materialId")).containsEntry("nullable", true);
        assertThat(property("QuestionResponse", "sourcePageNo")).containsEntry("nullable", true);
        assertThat(property("QuizSummaryResponse", "materialTitle")).containsEntry("nullable", true);
        // 다른 스키마를 가리키는 필드는 OpenAPI 3.0 에서 allOf 로 감싸야 nullable 이 남는다
        assertThat(property("QuizSummaryResponse", "lastSubmission")).containsEntry("nullable", true).containsKey("allOf");
        assertThat(schema("LastSubmissionResponse")).doesNotContainKey("nullable");

        assertThat(property("GenerationJobResponse", "jobId")).doesNotContainKey("nullable");
        assertThat(property("GenerationJobResponse", "status")).doesNotContainKey("nullable");
        assertThat(property("QuizResponse", "quizId")).doesNotContainKey("nullable");
    }

    // 생성기는 operationId 를 메서드 이름으로, 첫 태그를 클라이언트 이름으로 쓴다
    @Test
    void 모든_API_에_겹치지_않는_operationId_와_태그와_요약이_있다() {
        List<String> operationIds = docs.read("$.paths.*.*.operationId");
        List<List<String>> tags = docs.read("$.paths.*.*.tags");
        List<String> summaries = docs.read("$.paths.*.*.summary");

        assertThat(operationIds).isNotEmpty().doesNotHaveDuplicates();
        assertThat(operationIds).noneMatch(id -> id.matches(".*_\\d+$"));
        assertThat(tags).hasSameSizeAs(operationIds).allSatisfy(tag -> assertThat(tag).isNotEmpty());
        assertThat(summaries).hasSameSizeAs(operationIds);
    }

    @Test
    void 모든_API_에_오류_응답_모양이_붙는다() {
        List<String> errorRefs = docs.read("$.paths.*.*.responses.default.content['application/problem+json'].schema.$ref");
        List<String> operationIds = docs.read("$.paths.*.*.operationId");

        assertThat(errorRefs).hasSameSizeAs(operationIds).allMatch(ref -> ref.endsWith("/ErrorResponse"));
        assertThat((List<String>) schema("ErrorResponse").get("required")).contains("code", "detail", "status");
    }

    @Test
    void SSE_구독은_이벤트_스트림이고_본문은_작업_상태다() {
        String ref = docs.read("$.paths['/jobs/{jobId}/events'].get.responses['200'].content['text/event-stream'].schema.$ref");

        assertThat(ref).endsWith("/GenerationJobResponse");
        assertThat(docs.read("$.components.schemas", Map.class)).doesNotContainKey("SseEmitter");
    }
}
