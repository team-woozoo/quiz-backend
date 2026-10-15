package com.woozoo.quiz.auth.application;

public record IssuedTokens(String accessToken, String refreshToken, long expiresIn) {
}
