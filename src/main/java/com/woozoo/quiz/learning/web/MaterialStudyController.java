package com.woozoo.quiz.learning.web;

import com.woozoo.quiz.learning.application.MaterialStudyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 자료 API 라 앱의 생성 코드에서 같은 클래스에 모이도록 MaterialController 와 같은 태그를 쓴다
@Tag(name = "Material")
@RestController
@RequestMapping("/materials")
public class MaterialStudyController {

    private final MaterialStudyService materialStudyService;

    public MaterialStudyController(MaterialStudyService materialStudyService) {
        this.materialStudyService = materialStudyService;
    }

    @Operation(operationId = "getMyMaterials", summary = "내 자료 목록",
            description = """
                    최근에 올린 자료부터 전체를 준다. 자료마다 그 자료로 만든 문제 수와 맞힌 수, 틀린 수, 정답률을 같이 준다.
                    같은 문제를 여러 번 풀었으면 가장 최근 답 하나로 세고, 안 푼 문제는 틀린 것으로 센다.
                    퀴즈를 만드는 중이면 generating 이 true 다. 오류: UNAUTHORIZED(401)""")
    @GetMapping
    public List<MaterialResponse> getMyMaterials(@AuthenticationPrincipal Long userId) {
        return materialStudyService.getMyMaterials(userId).stream()
                .map(MaterialResponse::from)
                .toList();
    }

    @Operation(operationId = "getMyMaterial", summary = "자료 상세",
            description = """
                    자료 하나의 정보와 문제 수, 맞힌 수, 틀린 수를 준다. 값을 세는 방식은 내 자료 목록과 같다.
                    이 자료로 만든 퀴즈 목록은 GET /quizzes?materialId= 로 받는다.
                    오류: MATERIAL_NOT_FOUND(404, 없거나 남의 자료), UNAUTHORIZED(401)""")
    @GetMapping("/{materialId}")
    public MaterialResponse getMyMaterial(@AuthenticationPrincipal Long userId, @PathVariable long materialId) {
        return MaterialResponse.from(materialStudyService.getMyMaterial(userId, materialId));
    }

    @Operation(operationId = "getMyMaterialConcepts", summary = "자료의 개념별 상태",
            description = """
                    자료에서 뽑은 개념마다 나온 페이지, 문제 수, 맞힌 수, 정답률, 상태를 준다.
                    부족한 개념부터(WEAK, GOOD, PERFECT, UNCHECKED 순), 같은 상태면 정답률이 낮은 것, 그다음 앞 페이지의 개념부터 준다.
                    안 푼 문제는 틀린 것으로 세고, 하나도 안 풀었으면 UNCHECKED 다.
                    오류: MATERIAL_NOT_FOUND(404, 없거나 남의 자료), UNAUTHORIZED(401)""")
    @GetMapping("/{materialId}/concepts")
    public List<MaterialConceptResponse> getMyMaterialConcepts(@AuthenticationPrincipal Long userId,
                                                               @PathVariable long materialId) {
        return materialStudyService.getMyMaterialConcepts(userId, materialId).stream()
                .map(MaterialConceptResponse::from)
                .toList();
    }
}
