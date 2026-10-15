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

    @GetMapping("/me")
    public MeResponse getMe(@AuthenticationPrincipal Long userId) {
        return MeResponse.from(userService.getMe(userId));
    }
}
