package com.woozoo.quiz.auth.application;

import com.woozoo.quiz.TestcontainersConfiguration;
import com.woozoo.quiz.global.error.BusinessException;
import com.woozoo.quiz.global.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 재사용을 감지해 전부 폐기한 결과가 예외 때문에 롤백되지 않는지 본다.
// 테스트 트랜잭션 안에서 돌리면 refresh 가 그 안에 묶여 롤백 여부를 확인할 수 없으므로 @Transactional 을 붙이지 않고,
// 끝나면 만든 사용자를 직접 지운다 (refresh_token 은 CASCADE 로 함께 지워진다)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RefreshTokenReuseTest {

    private static final String PASSWORD = "correct-password";

    @Autowired
    AuthService authService;
    @Autowired
    JdbcClient jdbcClient;

    private final String email = "reuse-" + UUID.randomUUID() + "@example.com";

    @AfterEach
    void deleteUser() {
        jdbcClient.sql("DELETE FROM users WHERE email = :email").param("email", email).update();
    }

    private void assertRefreshRejected(String refreshToken) {
        assertThatThrownBy(() -> authService.refresh(refreshToken))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    void 이미_쓴_Refresh_토큰이_다시_오면_그_사용자의_다른_토큰도_모두_폐기한다() {
        authService.signup(new SignupCommand(email, PASSWORD, "tester"));
        IssuedTokens phone = authService.login(new LoginCommand(email, PASSWORD));
        IssuedTokens tablet = authService.login(new LoginCommand(email, PASSWORD));
        IssuedTokens phoneRefreshed = authService.refresh(phone.refreshToken());

        // 이미 쓴 휴대폰 토큰이 다시 온다 = 탈취 의심
        assertRefreshRejected(phone.refreshToken());

        // 그 사용자의 살아 있던 토큰(휴대폰의 새 토큰, 태블릿 토큰)도 함께 폐기되어야 한다
        assertRefreshRejected(phoneRefreshed.refreshToken());
        assertRefreshRejected(tablet.refreshToken());
    }
}
