package com.woozoo.quiz.material.application;

import com.woozoo.quiz.material.domain.FileType;

public record UploadMaterialCommand(byte[] content, long ownerId, String title,
                                    String originalFilename, FileType fileType) {
}
