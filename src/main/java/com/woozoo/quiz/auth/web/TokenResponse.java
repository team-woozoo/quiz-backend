package com.woozoo.quiz.auth.web;

import com.woozoo.quiz.auth.application.IssuedTokens;

public record TokenResponse(String accessToken, String refreshToken, long expiresIn) {

    public static TokenResponse from(IssuedTokens tokens) {
        return new TokenResponse(tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn());
    }
}
