package com.woozoo.quiz.learning.web;

import com.woozoo.quiz.learning.application.QuizHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "QuizHistory", description = "내 퀴즈 목록과 풀이 기록")
@RestController
public class QuizHistoryController {

    private final QuizHistoryService quizHistoryService;

    public QuizHistoryController(QuizHistoryService quizHistoryService) {
        this.quizHistoryService = quizHistoryService;
    }

    @Operation(operationId = "getMyQuizzes", summary = "내 퀴즈 목록",
            description = """
                    내가 만든 퀴즈를 최근 것부터 전체 준다. 퀴즈마다 문제 수, 푼 횟수, 가장 최근 제출의 맞힌 수와 제출 시각을 같이 준다.
                    아직 안 푼 퀴즈는 lastSubmission 이 null 이다. 문제와 보기는 GET /quizzes/{quizId} 로 받는다.
                    오류: UNAUTHORIZED(401)""")
    @GetMapping("/quizzes")
    public List<QuizSummaryResponse> getMyQuizzes(@AuthenticationPrincipal Long userId) {
        return quizHistoryService.getMyQuizzes(userId).stream()
                .map(QuizSummaryResponse::from)
                .toList();
    }
}
