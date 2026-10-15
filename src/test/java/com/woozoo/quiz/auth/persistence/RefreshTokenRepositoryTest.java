package com.woozoo.quiz.auth.persistence;

import com.woozoo.quiz.TestcontainersConfiguration;
import com.woozoo.quiz.auth.domain.NewUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
public class RefreshTokenRepositoryTest {

    static final Instant FUTURE = Instant.now().plus(Duration.ofDays(14));
    static final Instant PAST = Instant.now().minus(Duration.ofMinutes(1));

    @Autowired
    RefreshTokenRepository refreshTokenRepository;
    @Autowired
    UserRepository userRepository;

    long userId;

    @BeforeEach
    void setup() {
        NewUser newUser = new NewUser("example@test.com",
                "1234", "홍길동");
        userId = userRepository.save(newUser);
    }


    @Test
    void 저장한_토큰을_폐기하면_사용자_아이디를_돌려주고_한번만_폐기된다() {
        refreshTokenRepository.save(userId, "hash-a", FUTURE);

        assertThat(refreshTokenRepository.revoke("hash-a")).contains(userId);
        assertThat(refreshTokenRepository.revoke("hash-a")).isEmpty();
    }

    @Test
    void 만료된_토큰은_폐기되지_않는다() {
        refreshTokenRepository.save(userId, "hash-b", PAST);

        assertThat(refreshTokenRepository.revoke("hash-b")).isEmpty();
    }

    @Test
    void 없는_토큰은_빈값을_반환한다() {
        refreshTokenRepository.save(userId, "hash-c", FUTURE);

        assertThat(refreshTokenRepository.revoke("hash-a")).isEmpty();
    }

    @Test
    void 폐기된_토큰이면_주인을_찾는다() {
        refreshTokenRepository.save(userId, "hash-d", FUTURE);

        assertThat(refreshTokenRepository.findUserIdOfRevoked("hash-d")).isEmpty();

        refreshTokenRepository.revoke("hash-d");

        assertThat(refreshTokenRepository
                .findUserIdOfRevoked("hash-d")).contains(userId);
    }

    @Test
    void 아이디로_사용자의_모든_토큰을_폐기한다() {

        NewUser other = new NewUser("other@test.com",
                "4321", "아무개");
        long otherId = userRepository.save(other);

        refreshTokenRepository.save(userId, "hash-e", FUTURE);
        refreshTokenRepository.save(userId, "hash-f", FUTURE);

        refreshTokenRepository.save(otherId, "hash-g", FUTURE);

        refreshTokenRepository.revokeAllByUserId(userId);

        assertThat(refreshTokenRepository.revoke("hash-e")).isEmpty();
        assertThat(refreshTokenRepository.revoke("hash-f")).isEmpty();

        assertThat(refreshTokenRepository.revoke("hash-g")).contains(otherId);
    }
}
