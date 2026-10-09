package com.woozoo.quiz.auth.domain;

public record NewUser(String email, String passwordHash, String nickname) {
}
