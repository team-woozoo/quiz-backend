package com.woozoo.quiz.auth.web;

import com.woozoo.quiz.auth.domain.User;

public record MeResponse(long id, String email, String nickname) {

    public static MeResponse from(User user) {
        return new MeResponse(user.id(), user.email(), user.nickname());
    }
}
