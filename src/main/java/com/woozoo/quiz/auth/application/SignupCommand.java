package com.woozoo.quiz.auth.application;

public record SignupCommand(String email, String password, String nickname) {
}
