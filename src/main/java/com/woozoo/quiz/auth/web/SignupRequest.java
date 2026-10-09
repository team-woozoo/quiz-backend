package com.woozoo.quiz.auth.web;

import com.woozoo.quiz.auth.application.SignupCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank @Email @Size(max = 255)
        String email,

        // BCrypt 는 72 바이트를 넘는 비밀번호를 거부한다. 글자 수 상한은 영문 기준이고,
        // 한글처럼 글자당 여러 바이트인 경우는 서비스에서 바이트 길이로 한 번 더 막는다
        @NotBlank @Size(min = 8, max = 64)
        String password,

        @NotBlank @Size(max = 50)
        String nickname
) {

    public SignupCommand toCommand() {
        return new SignupCommand(email, password, nickname);
    }
}
