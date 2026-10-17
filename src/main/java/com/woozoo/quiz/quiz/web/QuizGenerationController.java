package com.woozoo.quiz.quiz.web;

import com.woozoo.quiz.quiz.application.QuizGenerationService;
import com.woozoo.quiz.quiz.domain.GenerationJob;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Optional;

@Tag(name = "QuizGeneration", description = "퀴즈 생성 요청과 진행 상황")
@RestController
public class QuizGenerationController {

    private final QuizGenerationService quizGenerationService;
    private final JobEmitterRegistry jobEmitterRegistry;

    public QuizGenerationController(QuizGenerationService quizGenerationService,
                                    JobEmitterRegistry jobEmitterRegistry) {
        this.quizGenerationService = quizGenerationService;
        this.jobEmitterRegistry = jobEmitterRegistry;
    }

    // 생성은 백그라운드에서 끝나므로 받았다는 뜻의 202 와 작업 id 만 돌려준다
    @Operation(operationId = "requestQuizGeneration", summary = "퀴즈 생성 요청",
            description = """
                    내 자료로 퀴즈 생성을 요청한다. 생성은 백그라운드에서 수십 초 걸려 202 와 jobId 를 바로 준다.
                    진행 상황은 GET /jobs/{jobId}/events 로 받고, DONE 이 되면 quizId 로 퀴즈를 조회한다.
                    오류: INVALID_INPUT(400), MATERIAL_NOT_FOUND(404, 없거나 내 것이 아닌 자료), UNAUTHORIZED(401)""")
    @PostMapping("/materials/{materialId}/quizzes")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public GenerateQuizResponse generate(@AuthenticationPrincipal Long userId,
                                         @PathVariable long materialId,
                                         @Valid @RequestBody GenerateQuizRequest request) {
        long jobId = quizGenerationService.requestGeneration(request.toCommand(userId, materialId));
        return new GenerateQuizResponse(jobId);
    }

    @Operation(operationId = "getGenerationJob", summary = "생성 작업 상태 조회",
            description = """
                    작업 상태를 한 번 준다. 상태는 PENDING → PROCESSING → DONE 또는 FAILED 로 바뀐다.
                    작업이 실패해도 조회는 200 이고, 실패 사유는 errorCode 다: GENERATION_FAILED, NO_VALID_QUESTIONS, INTERNAL_ERROR.
                    오류: JOB_NOT_FOUND(404, 없거나 내 것이 아닌 작업), UNAUTHORIZED(401)""")
    @GetMapping("/jobs/{jobId}")
    public GenerationJobResponse getJob(@AuthenticationPrincipal Long userId,
                                        @PathVariable long jobId) {
        return GenerationJobResponse.from(quizGenerationService.getJob(userId, jobId));
    }

    // 주인 확인은 등록 전에 한다. 작업당 연결이 하나라, 남이 등록하면 주인의 연결이 끊긴다.
    // 현재 상태는 등록한 뒤에 다시 읽는다. 그 사이 끝난 작업의 알림을 놓치지 않으려는 것이다
    @Operation(operationId = "subscribeGenerationJob", summary = "생성 진행 상황 구독 (SSE)",
            description = """
                    text/event-stream 으로 연결을 열어 둔다. 연결 즉시 현재 상태를 한 번 보내고, 상태가 바뀔 때마다 보낸다.
                    이벤트 이름은 status, data 는 GET /jobs/{jobId} 응답과 같은 JSON 이다. DONE 이나 FAILED 를 보내면 연결을 닫는다.
                    그 전에 끊기면 다시 구독하면 된다. 연결은 3분이 지나면 닫힌다. 같은 작업을 다시 구독하면 이전 연결은 닫힌다.
                    오류: JOB_NOT_FOUND(404), UNAUTHORIZED(401)""",
            responses = @ApiResponse(responseCode = "200", description = "상태 이벤트 스트림",
                    content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                            schema = @Schema(implementation = GenerationJobResponse.class))))
    @GetMapping(value = "/jobs/{jobId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@AuthenticationPrincipal Long userId,
                                @PathVariable long jobId) {
        quizGenerationService.getJob(userId, jobId);
        SseEmitter emitter = jobEmitterRegistry.connect(jobId);
        quizGenerationService.findJob(jobId)
                .ifPresent(job ->
                        jobEmitterRegistry.send(emitter, GenerationJobResponse.from(job)));
        return emitter;
    }
}
