package com.woozoo.quiz.auth.web;

import com.woozoo.quiz.auth.application.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // 주소로 id 를 받지 않고 토큰의 사용자만 조회해, 남의 정보를 요청할 방법을 없앤다
    @GetMapping("/me")
    public MeResponse getMe(@AuthenticationPrincipal Long userId) {
        return MeResponse.from(userService.getMe(userId));
    }
}
