package com.woozoo.quiz.learning.web;

import com.woozoo.quiz.learning.application.LearningProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "LearningProfile", description = "풀이 기록으로 본 내 학습 상태")
@RestController
public class LearningProfileController {

    private final LearningProfileService learningProfileService;

    public LearningProfileController(LearningProfileService learningProfileService) {
        this.learningProfileService = learningProfileService;
    }

    @Operation(operationId = "getMyCognitiveTypeScores", summary = "블룸 유형별 정답률",
            description = """
                    기억, 이해, 적용, 분석, 평가 순서로 다섯 유형을 항상 모두 준다. 같은 문제를 여러 번 풀었으면 가장 최근 답 하나만 센다.
                    푼 적 없는 유형은 answeredCount 가 0, accuracy 가 null 이다. isEnoughData 가 false 면 푼 문제가 적어 정답률을 믿기 어렵다.
                    오류: UNAUTHORIZED(401)""")
    @GetMapping("/users/me/cognitive-type-scores")
    public List<CognitiveTypeScoreResponse> getMyCognitiveTypeScores(@AuthenticationPrincipal Long userId) {
        return learningProfileService.getMyCognitiveTypeScores(userId).stream()
                .map(CognitiveTypeScoreResponse::from)
                .toList();
    }

    @Operation(operationId = "getMyWeakConcepts", summary = "약한 개념",
            description = """
                    기준 수 이상 푼 개념 중 정답률이 낮은 순으로 몇 개만 준다. 정답률이 같으면 많이 푼 개념이 먼저다.
                    같은 문제를 여러 번 풀었으면 가장 최근 답 하나만 센다. 기준을 넘는 개념이 없으면 빈 목록이다.
                    개념은 자료마다 따로 있어, 이름이 같아도 자료가 다르면 따로 나온다.
                    오류: UNAUTHORIZED(401)""")
    @GetMapping("/users/me/weak-concepts")
    public List<WeakConceptResponse> getMyWeakConcepts(@AuthenticationPrincipal Long userId) {
        return learningProfileService.getMyWeakConcepts(userId).stream()
                .map(WeakConceptResponse::from)
                .toList();
    }
}
