package com.woozoo.quiz.auth.web;

import com.woozoo.quiz.auth.application.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User", description = "내 정보")
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // 주소로 id 를 받지 않고 토큰의 사용자만 조회해, 남의 정보를 요청할 방법을 없앤다
    @Operation(operationId = "getMe", summary = "내 정보 조회",
            description = "토큰의 사용자 정보를 준다. 오류: UNAUTHORIZED(401)")
    @GetMapping("/me")
    public MeResponse getMe(@AuthenticationPrincipal Long userId) {
        return MeResponse.from(userService.getMe(userId));
    }
}
