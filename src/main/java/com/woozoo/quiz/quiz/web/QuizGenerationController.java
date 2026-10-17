package com.woozoo.quiz.quiz.web;

import com.woozoo.quiz.quiz.application.QuizGenerationService;
import com.woozoo.quiz.quiz.domain.GenerationJob;
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
    @PostMapping("/materials/{materialId}/quizzes")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public GenerateQuizResponse generate(@AuthenticationPrincipal Long userId,
                                         @PathVariable long materialId,
                                         @Valid @RequestBody GenerateQuizRequest request) {
        long jobId = quizGenerationService.requestGeneration(request.toCommand(userId, materialId));
        return new GenerateQuizResponse(jobId);
    }

    @GetMapping("/jobs/{jobId}")
    public GenerationJobResponse getJob(@AuthenticationPrincipal Long userId,
                                        @PathVariable long jobId) {
        return GenerationJobResponse.from(quizGenerationService.getJob(userId, jobId));
    }

    // 주인 확인은 등록 전에 한다. 작업당 연결이 하나라, 남이 등록하면 주인의 연결이 끊긴다.
    // 현재 상태는 등록한 뒤에 다시 읽는다. 그 사이 끝난 작업의 알림을 놓치지 않으려는 것이다
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
