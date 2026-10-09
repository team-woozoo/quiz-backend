package com.woozoo.quiz.auth.domain;

public record User(long id, String email, String passwordHash, String nickname) {
}
