package com.woozoo.quiz.auth.web;

import com.woozoo.quiz.auth.application.LoginCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 로그인에는 가입 규칙(형식, 최소 길이)을 걸지 않는다. 틀린 값은 INVALID_CREDENTIALS 로 답한다
public record LoginRequest(
        @NotBlank @Size(max = 255)
        String email,

        // 가입과 같은 글자 수 상한. 바이트 길이 검사는 서비스에서 한다
        @NotBlank @Size(max = 64)
        String password
) {

    public LoginCommand toCommand() {
        return new LoginCommand(email, password);
    }
}
