package com.woozoo.quiz.material.web;

import com.woozoo.quiz.material.application.MaterialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@Tag(name = "Material", description = "강의 자료 업로드와 목록")
@RestController
@RequestMapping("/materials")
public class MaterialController {

    private final MaterialService materialService;

    public MaterialController(MaterialService materialService) {
        this.materialService = materialService;
    }

    // 추출과 저장까지 요청 안에서 끝나므로 만들어진 자료 id 를 201 로 돌려준다
    @Operation(operationId = "uploadMaterial", summary = "PDF 업로드",
            description = """
                    PDF 를 올리면 페이지별 글자를 추출해 저장하고 materialId 를 준다. 추출까지 요청 안에서 끝나 몇 초 걸릴 수 있다.
                    title 을 비우면 확장자를 뺀 파일 이름을 제목으로 쓴다. 파일은 20MB, 글자가 있는 페이지는 100쪽까지다.
                    오류: INVALID_INPUT(400, 파일 없음), INVALID_FILE(400, PDF 가 아님), FILE_TOO_LARGE(413),
                    EXTRACTION_FAILED(422, 글자를 뽑을 수 없는 PDF), TOO_MANY_PAGES(422), UNAUTHORIZED(401)""")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UploadMaterialResponse upload(@AuthenticationPrincipal Long userId,
                                         @Valid @ModelAttribute UploadMaterialRequest request) throws IOException {
        return new UploadMaterialResponse(materialService.upload(request.toCommand(userId)));
    }

    @Operation(operationId = "getMyMaterials", summary = "내 자료 목록",
            description = "최근에 올린 자료부터 전체를 준다. pageCount 는 글자가 있어 저장된 페이지 수다. 오류: UNAUTHORIZED(401)")
    @GetMapping
    public List<MaterialResponse> getMyMaterials(@AuthenticationPrincipal Long userId) {
        return materialService.getMyMaterials(userId).stream()
                .map(MaterialResponse::from)
                .toList();
    }
}
