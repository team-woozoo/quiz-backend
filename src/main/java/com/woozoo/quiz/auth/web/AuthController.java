package com.woozoo.quiz.auth.web;

import com.woozoo.quiz.auth.application.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "가입, 로그인, 토큰 재발급, 로그아웃")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // 상태를 애너테이션으로 적어야 Swagger 문서에도 201 로 나온다 (ResponseEntity 로 정하면 200 으로 적힌다)
    @Operation(operationId = "signup", summary = "회원가입",
            description = """
                    이메일은 대소문자를 구분하지 않는다. 가입만 하고 로그인은 따로 한다.
                    오류: INVALID_INPUT(400), EMAIL_ALREADY_EXISTS(409)""")
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public SignupResponse signup(@Valid @RequestBody SignupRequest request) {
        return new SignupResponse(authService.signup(request.toCommand()));
    }

    @Operation(operationId = "login", summary = "로그인",
            description = """
                    Access 토큰(30분)과 Refresh 토큰(14일)을 준다. expiresIn 은 Access 토큰이 만료될 때까지 남은 초다.
                    오류: INVALID_INPUT(400), INVALID_CREDENTIALS(401, 이메일과 비밀번호 중 무엇이 틀렸는지 구분하지 않는다)""")
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return TokenResponse.from(authService.login(request.toCommand()));
    }

    @Operation(operationId = "logout", summary = "로그아웃",
            description = """
                    Refresh 토큰을 폐기한다. 토큰이 없거나 이미 폐기됐어도 204 로 답한다. 앱은 응답과 상관없이 저장한 토큰을 지운다.
                    오류: INVALID_INPUT(400)""")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
    }

    @Operation(operationId = "refreshToken", summary = "토큰 재발급",
            description = """
                    Refresh 토큰으로 두 토큰을 새로 받는다. 쓴 Refresh 토큰은 폐기되므로 두 토큰을 모두 바꿔 저장한다.
                    이미 쓴 토큰이 다시 오면 그 사용자의 모든 토큰을 폐기한다. 재발급은 한 번에 하나만 보낸다.
                    오류: INVALID_INPUT(400), INVALID_REFRESH_TOKEN(401, 재발급을 다시 시도하지 말고 로그인 화면으로)""")
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return TokenResponse.from(authService.refresh(request.refreshToken()));
    }
}
