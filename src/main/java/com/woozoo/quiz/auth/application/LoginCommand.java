package com.woozoo.quiz.auth.application;

public record LoginCommand(String email, String password) {
}
