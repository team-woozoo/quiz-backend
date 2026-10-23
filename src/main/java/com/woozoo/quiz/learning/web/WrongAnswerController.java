package com.woozoo.quiz.learning.web;

import com.woozoo.quiz.learning.application.WrongAnswerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "WrongAnswer", description = "오답 노트와 복습 채점")
@RestController
public class WrongAnswerController {

    private final WrongAnswerService wrongAnswerService;

    public WrongAnswerController(WrongAnswerService wrongAnswerService) {
        this.wrongAnswerService = wrongAnswerService;
    }

    @Operation(operationId = "getMyWrongAnswers", summary = "오답 노트",
            description = """
                    아직 해결하지 않은 오답을 처음 틀린 순서로 전체 준다. 퀴즈에서 틀리거나 안 푼 문제가 들어오고, 다시 맞히면 빠진다.
                    문제마다 보기와 정답(isCorrect), 해설, 근거와 페이지, 개념, 자료, 틀린 횟수를 준다. 앱이 바로 채점할 수 있다.
                    오류: UNAUTHORIZED(401)""")
    @GetMapping("/users/me/wrong-answers")
    public List<WrongAnswerResponse> getMyWrongAnswers(@AuthenticationPrincipal Long userId) {
        return wrongAnswerService.getMyWrongAnswers(userId).stream()
                .map(WrongAnswerResponse::from)
                .toList();
    }

    // 보낼 때마다 복습 제출 기록이 생기므로 201 로 답한다
    @Operation(operationId = "submitWrongAnswerReview", summary = "오답 노트 복습 채점",
            description = """
                    오답 노트에서 푼 답을 한 개 이상 보내면 서버가 다시 채점해 문제마다 맞았는지 돌려준다. 여러 퀴즈의 문제를 섞어 보내도 된다.
                    보낸 문제만 채점하고, 보내지 않은 문제는 그대로 둔다. 맞힌 문제는 오답 노트에서 빠지고, 틀린 문제는 틀린 횟수가 늘어난다.
                    결과는 블룸 유형별 정답률, 약한 개념, 자료의 정답·오답 수에 들어가고, 퀴즈별 기록(푼 횟수, 최근 점수)은 바뀌지 않는다.
                    같은 답을 두 번 보내면 두 번 기록되니, 한 번 보낸 답은 다시 보내지 않는다.
                    오류: INVALID_INPUT(400, 답이 없거나 필드가 빠짐), INVALID_SUBMISSION(400, 같은 문제를 두 번 보내거나
                    내 문제가 아니거나 그 문제의 보기가 아님), UNAUTHORIZED(401)""")
    @PostMapping("/users/me/wrong-answers/submissions")
    @ResponseStatus(HttpStatus.CREATED)
    public SubmissionResponse review(@AuthenticationPrincipal Long userId,
                                     @Valid @RequestBody SubmitQuizRequest request) {
        return SubmissionResponse.from(wrongAnswerService.review(request.toReviewCommand(userId)));
    }
}
