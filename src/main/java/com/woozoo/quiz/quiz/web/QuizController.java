package com.woozoo.quiz.quiz.web;

import com.woozoo.quiz.quiz.application.QuizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Quiz", description = "만든 퀴즈 조회")
@RestController
@RequestMapping("/quizzes")
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    @Operation(operationId = "getQuiz", summary = "퀴즈 조회",
            description = """
                    문제와 보기를 orderNo 순서로 준다. isCorrect 로 고르는 즉시 정답을 보여 줄 수 있고, 기록에 남는 채점은 제출할 때 서버가 한다.
                    sourceQuote 와 sourcePageNo 는 문제의 근거가 된 원문과 그 쪽 번호다.
                    오류: QUIZ_NOT_FOUND(404, 없거나 내 것이 아닌 퀴즈), UNAUTHORIZED(401)""")
    @GetMapping("/{quizId}")
    public QuizResponse getQuiz(@AuthenticationPrincipal Long userId, @PathVariable long quizId) {
        return QuizResponse.from(quizService.getQuiz(userId, quizId));
    }
}
