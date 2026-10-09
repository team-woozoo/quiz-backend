package com.woozoo.quiz.auth.application;

import com.woozoo.quiz.TestcontainersConfiguration;
import com.woozoo.quiz.auth.domain.User;
import com.woozoo.quiz.auth.persistence.UserRepository;
import com.woozoo.quiz.global.error.BusinessException;
import com.woozoo.quiz.global.error.ErrorCode;
import com.woozoo.quiz.global.security.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class AuthServiceTest {

    private static final String PASSWORD = "correct-password";

    @Autowired
    AuthService authService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    JwtProvider jwtProvider;

    @Test
    void 가입하면_비밀번호를_해시로_저장한다() {
        long id = authService.signup(new SignupCommand("test@example.com", PASSWORD, "tester"));

        User saved = userRepository.findByEmail("test@example.com").orElseThrow();
        assertThat(saved.id()).isEqualTo(id);
        assertThat(saved.passwordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, saved.passwordHash())).isTrue();
    }

    @Test
    void 이메일은_공백을_지우고_소문자로_저장한다() {
        authService.signup(new SignupCommand("  Test@Example.COM ", PASSWORD, "tester"));

        assertThat(userRepository.findByEmail("test@example.com")).isPresent();
    }

    @Test
    void 대소문자만_다른_이메일로_다시_가입하면_EMAIL_ALREADY_EXISTS() {
        authService.signup(new SignupCommand("test@example.com", PASSWORD, "tester"));

        assertThatThrownBy(() -> authService.signup(new SignupCommand("TEST@example.com", "other", "other")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @Test
    void 올바른_비밀번호로_로그인하면_그_사용자의_토큰을_발급한다() {
        long id = authService.signup(new SignupCommand("test@example.com", PASSWORD, "tester"));

        String token = authService.login(new LoginCommand("test@example.com", PASSWORD));

        assertThat(jwtProvider.parseUserId(token)).contains(id);
    }

    @Test
    void 대문자로_가입하고_소문자로_로그인해도_된다() {
        long id = authService.signup(new SignupCommand("Test@Example.com", PASSWORD, "tester"));

        String token = authService.login(new LoginCommand("test@example.com", PASSWORD));

        assertThat(jwtProvider.parseUserId(token)).contains(id);
    }

    // 한글 25자는 글자 수로는 64자 상한 안이지만 75 바이트라 BCrypt 한도(72)를 넘는다
    private static final String TOO_LONG_IN_BYTES = "가".repeat(25);

    @Test
    void 바이트로_BCrypt_한도를_넘는_비밀번호로는_가입할_수_없다() {
        assertThatThrownBy(() -> authService.signup(new SignupCommand("test@example.com", TOO_LONG_IN_BYTES, "tester")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
        assertThat(userRepository.existsByEmail("test@example.com")).isFalse();
    }

    @Test
    void 바이트로_BCrypt_한도를_넘는_비밀번호로_로그인하면_500_이_아니라_INVALID_CREDENTIALS() {
        authService.signup(new SignupCommand("test@example.com", PASSWORD, "tester"));

        assertThatThrownBy(() -> authService.login(new LoginCommand("test@example.com", TOO_LONG_IN_BYTES)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    // 어느 쪽이 틀렸는지 알려 주면 가입된 이메일을 골라낼 수 있으므로 같은 오류여야 한다
    @Test
    void 틀린_비밀번호와_없는_이메일은_같은_INVALID_CREDENTIALS() {
        authService.signup(new SignupCommand("test@example.com", PASSWORD, "tester"));

        assertThatThrownBy(() -> authService.login(new LoginCommand("test@example.com", "wrong-password")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        assertThatThrownBy(() -> authService.login(new LoginCommand("nobody@example.com", PASSWORD)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }
}
