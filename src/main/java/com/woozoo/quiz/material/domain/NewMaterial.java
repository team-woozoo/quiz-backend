package com.woozoo.quiz.material.domain;

public record NewMaterial(long ownerId, String title, String originalFilename, FileType fileType) {
}
