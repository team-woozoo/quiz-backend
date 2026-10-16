package com.woozoo.quiz.quiz.web;

import com.woozoo.quiz.quiz.application.QuizService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/quizzes")
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    @GetMapping("/{quizId}")
    public QuizResponse getQuiz(@AuthenticationPrincipal Long userId, @PathVariable long quizId) {
        return QuizResponse.from(quizService.getQuiz(userId, quizId));
    }
}
