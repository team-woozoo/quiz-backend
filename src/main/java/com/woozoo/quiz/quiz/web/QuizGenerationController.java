package com.woozoo.quiz.quiz.web;

import com.woozoo.quiz.quiz.application.QuizGenerationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class QuizGenerationController {

    private final QuizGenerationService quizGenerationService;

    public QuizGenerationController(QuizGenerationService quizGenerationService) {
        this.quizGenerationService = quizGenerationService;
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
}
