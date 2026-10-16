package com.woozoo.quiz.material.web;

import com.woozoo.quiz.material.application.UploadMaterialCommand;
import com.woozoo.quiz.material.domain.FileType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

// multipart 폼으로 받는다. 제목을 비우면 파일 이름을 제목으로 쓴다
public record UploadMaterialRequest(
        @NotNull
        MultipartFile file,

        @Size(max = 255)
        String title
) {

    // 지금은 PDF 만 받는다. 실제로 PDF 인지는 서비스가 파일 내용으로 확인한다
    public UploadMaterialCommand toCommand(long ownerId) throws IOException {
        return new UploadMaterialCommand(file.getBytes(), ownerId, title, file.getOriginalFilename(), FileType.PDF);
    }
}
