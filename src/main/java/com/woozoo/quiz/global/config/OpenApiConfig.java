package com.woozoo.quiz.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.PropertyCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// 앱은 이 문서로 모델과 API 코드를 생성한다(swagger_parser). 그래서 필수 여부와 null 허용을 규약과 같게 적는다
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";
    private static final String ERROR_SCHEMA = "ErrorResponse";
    private static final String PROBLEM_JSON = "application/problem+json";

    // Swagger 화면의 Authorize 버튼에 Access 토큰을 넣으면 모든 요청에 Authorization 헤더가 붙는다.
    // /auth/** 는 헤더가 붙어도 무시하므로 전체에 건다
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Quiz Backend API")
                        .version("v1")
                        .description("""
                                강의 자료로 퀴즈를 만들고 풀이를 분석하는 앱의 API 다.
                                오류는 HTTP 상태와 함께 ProblemDetail 형식으로 오고, 앱은 `code` 로 분기한다.
                                로그인이 필요한 API 에 토큰이 없거나 만료되면 `UNAUTHORIZED`(401) 다.
                                응답의 필드는 항상 있고, 값이 없으면 빼지 않고 `null` 로 보낸다."""))
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    // 응답은 규약대로 모든 필드를 늘 보낸다. 표시가 없으면 생성기가 모든 필드를 null 가능으로 만들어,
    // 이름이 Response 로 끝나는 스키마는 모든 필드를 필수로 적는다. null 이 될 수 있는 필드는 각 레코드에서 nullable 로 표시한다
    @Bean
    @SuppressWarnings({"rawtypes", "unchecked"})
    public OpenApiCustomizer responseFieldsRequired() {
        return openApi -> {
            Map<String, Schema> schemas = openApi.getComponents().getSchemas();
            schemas.forEach((name, schema) -> {
                if (name.endsWith("Response") && schema.getProperties() != null) {
                    schema.setRequired(new ArrayList<>(schema.getProperties().keySet()));
                }
            });
            cleanUpNullableReferenceTargets(schemas);
        };
    }

    // swagger-core 는 참조 필드의 @Schema(nullable, description) 을 가리키는 스키마 자체에도 붙인다.
    // 그러면 그 스키마를 어디서 쓰든 null 가능처럼 보여, 감싼 필드에만 남기고 스키마에서는 지운다
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void cleanUpNullableReferenceTargets(Map<String, Schema> schemas) {
        schemas.values().stream()
                .filter(schema -> schema.getProperties() != null)
                .flatMap(schema -> ((Map<String, Schema>) schema.getProperties()).values().stream())
                .filter(property -> Boolean.TRUE.equals(property.getNullable()) && property.getAllOf() != null)
                .forEach(property -> {
                    String ref = ((Schema<?>) property.getAllOf().getFirst()).get$ref();
                    Schema<?> target = schemas.get(ref.substring(ref.lastIndexOf('/') + 1));
                    if (target != null) {
                        target.setNullable(null);
                        if (Objects.equals(target.getDescription(), property.getDescription())) {
                            target.setDescription(null);
                        }
                    }
                });
    }

    // OpenAPI 3.0 은 다른 스키마를 가리키는 필드($ref) 옆의 nullable 을 무시한다. springdoc 도 그 표시를 빼 버려,
    // @Schema(nullable = true) 가 붙은 참조 필드는 allOf 로 한 번 감싸 null 가능을 남긴다(swagger_parser 가 읽는 모양)
    @Bean
    public PropertyCustomizer nullableReferences() {
        return (property, type) -> {
            if (property == null || property.get$ref() == null || !isMarkedNullable(type.getCtxAnnotations())) {
                return property;
            }
            ComposedSchema nullableReference = new ComposedSchema();
            nullableReference.addAllOfItem(new Schema<>().$ref(property.get$ref()));
            nullableReference.setNullable(true);
            nullableReference.setDescription(descriptionOf(type.getCtxAnnotations()));
            return nullableReference;
        };
    }

    private static boolean isMarkedNullable(java.lang.annotation.Annotation[] annotations) {
        return annotations != null && Arrays.stream(annotations)
                .filter(io.swagger.v3.oas.annotations.media.Schema.class::isInstance)
                .map(io.swagger.v3.oas.annotations.media.Schema.class::cast)
                .anyMatch(io.swagger.v3.oas.annotations.media.Schema::nullable);
    }

    private static String descriptionOf(java.lang.annotation.Annotation[] annotations) {
        return Arrays.stream(annotations)
                .filter(io.swagger.v3.oas.annotations.media.Schema.class::isInstance)
                .map(io.swagger.v3.oas.annotations.media.Schema.class::cast)
                .map(io.swagger.v3.oas.annotations.media.Schema::description)
                .filter(description -> !description.isBlank())
                .findFirst()
                .orElse(null);
    }

    // 모든 API 에 오류 응답 모양을 붙여, 앱이 오류 모델도 같이 생성하게 한다.
    // springdoc 은 컨트롤러에서 쓰이지 않는 스키마를 문서에서 빼서, 스키마도 여기서 함께 넣는다
    @Bean
    public OpenApiCustomizer errorResponseOnEveryOperation() {
        return openApi -> {
            openApi.getComponents().addSchemas(ERROR_SCHEMA, errorSchema());
            openApi.getPaths().values().forEach(path ->
                    path.readOperations().forEach(operation ->
                            operation.getResponses().addApiResponse("default", new ApiResponse()
                                    .description("오류. `code` 로 종류를 구분한다")
                                    .content(new Content().addMediaType(PROBLEM_JSON,
                                            new MediaType().schema(new Schema<>().$ref(ERROR_SCHEMA)))))));
        };
    }

    private static Schema<?> errorSchema() {
        ObjectSchema schema = new ObjectSchema();
        schema.setDescription("RFC 9457 ProblemDetail 에 오류 코드를 더한 모양");
        schema.addProperty("type", new StringSchema().example("about:blank"));
        schema.addProperty("title", new StringSchema().example("Not Found"));
        schema.addProperty("status", new IntegerSchema().example(404));
        schema.addProperty("detail", new StringSchema().description("사용자에게 보여 줄 한국어 메시지")
                .example("퀴즈를 찾을 수 없습니다"));
        schema.addProperty("instance", new StringSchema().example("/quizzes/42"));
        schema.addProperty("code", new StringSchema().description("오류 종류. 앱은 이 값으로 분기한다")
                .example("QUIZ_NOT_FOUND"));
        schema.setRequired(List.of("type", "title", "status", "detail", "instance", "code"));
        return schema;
    }
}
