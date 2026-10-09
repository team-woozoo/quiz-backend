package com.woozoo.quiz.global.security;

import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProviderTest {

    private static final Duration THIRTY_MINUTES = Duration.ofMinutes(30);

    private final JwtProvider jwtProvider = new JwtProvider(randomSecret(48), THIRTY_MINUTES);

    // 테스트마다 새 키를 만들어 실제 키를 코드에 남기지 않는다
    private static String randomSecret(int bytes) {
        byte[] key = new byte[bytes];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    @Test
    void 발급한_토큰에서_같은_사용자_아이디를_꺼낸다() {
        String token = jwtProvider.createAccessToken(42L);

        assertThat(jwtProvider.parseUserId(token)).contains(42L);
    }

    @Test
    void 다른_키로_서명한_토큰은_거부한다() {
        JwtProvider otherProvider = new JwtProvider(randomSecret(48), THIRTY_MINUTES);
        String forged = otherProvider.createAccessToken(42L);

        assertThat(jwtProvider.parseUserId(forged)).isEmpty();
    }

    @Test
    void 내용을_바꾼_토큰은_거부한다() {
        String[] parts = jwtProvider.createAccessToken(42L).split("\\.");
        Base64.Decoder decoder = Base64.getUrlDecoder();
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

        // 서명은 그대로 두고 내용의 사용자 아이디만 남의 것으로 바꾼다
        String payload = new String(decoder.decode(parts[1]), StandardCharsets.UTF_8)
                .replace("\"sub\":\"42\"", "\"sub\":\"43\"");
        String tampered = parts[0] + "." + encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + "." + parts[2];

        assertThat(jwtProvider.parseUserId(tampered)).isEmpty();
    }

    @Test
    void 만료된_토큰은_거부한다() {
        String secret = randomSecret(48);
        JwtProvider issuer = new JwtProvider(secret, Duration.ofSeconds(-1));
        JwtProvider verifier = new JwtProvider(secret, THIRTY_MINUTES);

        String expired = issuer.createAccessToken(42L);

        assertThat(verifier.parseUserId(expired)).isEmpty();
    }

    @Test
    void JWT_형식이_아니면_거부한다() {
        assertThat(jwtProvider.parseUserId("not-a-jwt")).isEmpty();
        assertThat(jwtProvider.parseUserId("")).isEmpty();
    }

    @Test
    void 키가_32바이트보다_짧으면_생성할_때_실패한다() {
        assertThatThrownBy(() -> new JwtProvider(randomSecret(16), THIRTY_MINUTES))
                .isInstanceOf(WeakKeyException.class);
    }
}
