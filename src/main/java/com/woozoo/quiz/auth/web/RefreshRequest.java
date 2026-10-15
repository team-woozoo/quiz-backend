package com.woozoo.quiz.auth.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshRequest(@NotBlank @Size(min = 1) String refreshToken) {
}
