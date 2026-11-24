package com.woozoo.quiz.material.web;

import com.woozoo.quiz.material.application.MaterialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@Tag(name = "Material", description = "강의 자료 업로드, 목록, 자료별 학습 기록")
@RestController
@RequestMapping("/materials")
public class MaterialController {

    private final MaterialService materialService;

    public MaterialController(MaterialService materialService) {
        this.materialService = materialService;
    }

    // 추출, 분석, 저장까지 요청 안에서 끝나므로 만들어진 자료 id 를 201 로 돌려준다
    @Operation(operationId = "uploadMaterial", summary = "PDF 업로드",
            description = """
                    PDF 를 올리면 페이지별 글자를 추출하고, AI 가 학습 자료인지 판단해 개념으로 나눈 뒤 함께 저장하고 materialId 를 준다.
                    분석까지 요청 안에서 끝나 보통 10~30초 걸린다. 응답이 올 때까지 업로드 버튼을 막아 두면 같은 파일이 두 번 분석되지 않는다.
                    분석에 실패하거나 학습 자료가 아니면 아무것도 저장하지 않는다.
                    같은 사용자가 같은 파일(바이트가 같은 파일)을 다시 올리면 거부한다. 파일 이름만 바꾼 것도 같은 파일이다.
                    title 을 비우면 확장자를 뺀 파일 이름을 제목으로 쓴다. 파일은 20MB, 글자가 있는 페이지는 100쪽까지다.
                    오류: INVALID_INPUT(400, 파일 없음), INVALID_FILE(400, PDF 가 아님), FILE_TOO_LARGE(413),
                    EXTRACTION_FAILED(422, 글자를 뽑을 수 없는 PDF), TOO_MANY_PAGES(422),
                    NOT_STUDY_MATERIAL(422, 강의 계획서나 공지처럼 학습 내용이 없는 자료),
                    MATERIAL_ALREADY_EXISTS(409, 이미 올린 파일), ANALYSIS_FAILED(502, 분석 실패. 다시 시도할 수 있다),
                    UNAUTHORIZED(401)""")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UploadMaterialResponse upload(@AuthenticationPrincipal Long userId,
                                         @Valid @ModelAttribute UploadMaterialRequest request) throws IOException {
        return new UploadMaterialResponse(materialService.upload(request.toCommand(userId)));
    }
}
