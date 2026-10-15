package com.woozoo.quiz.auth.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenGeneratorTest {

    private final RefreshTokenGenerator refreshTokenGenerator = new RefreshTokenGenerator();

    @Test
    void 매번_다른_값을_반환한다() {
        String token1 = refreshTokenGenerator.generateToken();
        String token2 = refreshTokenGenerator.generateToken();

        assertThat(token1).isNotEqualTo(token2);
    }

    @Test
    void 같은_입력엔_같은_해쉬를_반환한다() {
        String token1 = refreshTokenGenerator.generateToken();
        String token2 = refreshTokenGenerator.generateToken();

        String hash1 = refreshTokenGenerator.hashToken(token1);
        String hash2 = refreshTokenGenerator.hashToken(token2);
        String hash3 = refreshTokenGenerator.hashToken(token1);
        String hash4 = refreshTokenGenerator.hashToken(token2);

        assertThat(hash1).isEqualTo(hash3);
        assertThat(hash2).isEqualTo(hash4);

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void 토큰은_url에_안전한_43자다() {
        String token = refreshTokenGenerator.generateToken();

        assertThat(token).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    void 해쉬는_64자_16진수다() {
        String hash = refreshTokenGenerator
                .hashToken(refreshTokenGenerator.generateToken());

        assertThat(hash).matches("[0-9a-f]{64}");
    }
}
