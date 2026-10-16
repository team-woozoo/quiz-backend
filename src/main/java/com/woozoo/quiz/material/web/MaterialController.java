package com.woozoo.quiz.material.web;

import com.woozoo.quiz.material.application.MaterialService;
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

@RestController
@RequestMapping("/materials")
public class MaterialController {

    private final MaterialService materialService;

    public MaterialController(MaterialService materialService) {
        this.materialService = materialService;
    }

    // 추출과 저장까지 요청 안에서 끝나므로 만들어진 자료 id 를 201 로 돌려준다
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UploadMaterialResponse upload(@AuthenticationPrincipal Long userId,
                                         @Valid @ModelAttribute UploadMaterialRequest request) throws IOException {
        return new UploadMaterialResponse(materialService.upload(request.toCommand(userId)));
    }

    @GetMapping
    public List<MaterialResponse> getMyMaterials(@AuthenticationPrincipal Long userId) {
        return materialService.getMyMaterials(userId).stream()
                .map(MaterialResponse::from)
                .toList();
    }
}
