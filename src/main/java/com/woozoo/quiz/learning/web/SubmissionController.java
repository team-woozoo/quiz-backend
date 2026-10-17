package com.woozoo.quiz.learning.web;

import com.woozoo.quiz.learning.application.SubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Submission", description = "퀴즈 제출과 채점")
@RestController
public class SubmissionController {

    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    // 제출할 때마다 새 제출 기록이 생기므로 201 로 답한다
    @Operation(operationId = "submitQuiz", summary = "퀴즈 제출",
            description = """
                    퀴즈 한 판을 끝까지 푼 답을 제출하면 서버가 채점해 문제마다 맞았는지 돌려준다.
                    푼 문제만 보내고, 보내지 않은 문제는 안 푼 것으로 보고 틀림으로 저장한다. 같은 퀴즈를 여러 번 제출할 수 있다.
                    틀린 문제와 안 푼 문제는 오답 노트에 들어가고, 오답 노트에 있던 문제를 맞히면 해결된다.
                    정답과 해설은 퀴즈 조회(GET /quizzes/{quizId}) 응답에 있다.
                    오류: INVALID_INPUT(400, 답이 없거나 필드가 빠짐), INVALID_SUBMISSION(400, 같은 문제를 두 번 보내거나
                    이 퀴즈에 없는 보기, 다른 문제의 보기), QUIZ_NOT_FOUND(404, 없거나 내 것이 아닌 퀴즈), UNAUTHORIZED(401)""")
    @PostMapping("/quizzes/{quizId}/submissions")
    @ResponseStatus(HttpStatus.CREATED)
    public SubmissionResponse submit(@AuthenticationPrincipal Long userId,
                                     @PathVariable long quizId,
                                     @Valid @RequestBody SubmitQuizRequest request) {
        return SubmissionResponse.from(submissionService.submit(request.toCommand(userId, quizId)));
    }
}
