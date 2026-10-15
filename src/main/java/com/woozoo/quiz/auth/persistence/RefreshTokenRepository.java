package com.woozoo.quiz.auth.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

@Repository
public class RefreshTokenRepository {

    private final JdbcClient jdbcClient;

    public RefreshTokenRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void save(long userId, String tokenHash, Instant expiresAt) {
        jdbcClient.sql("""
                        INSERT INTO refresh_token (user_id, token_hash, expires_at)
                        VALUES (:userId, :tokenHash, :expiresAt)
                        """)
                .param("userId", userId)
                .param("tokenHash", tokenHash)
                // Postgres 드라이버가 Instant 의 SQL 타입을 추론하지 못해 OffsetDateTime 으로 바꿔 넘긴다
                .param("expiresAt", expiresAt.atOffset(ZoneOffset.UTC))
                .update();
    }

    // 확인과 폐기를 한 문장으로 한다. 같은 토큰으로 동시에 와도 DB 가 차례로 처리해 하나만 user_id 를 받는다
    public Optional<Long> revoke(String tokenHash) {
        return jdbcClient.sql("""
                         UPDATE refresh_token SET revoked_at = now()
                         WHERE token_hash = :tokenHash
                         AND revoked_at is NULL
                         AND expires_at > now()
                         RETURNING user_id
                        """)
                .param("tokenHash", tokenHash)
                .query(Long.class)
                .optional();
    }

    // 쓴 토큰을 지우지 않고 남겨 두는 이유다. 지우면 재사용이 와도 처음 보는 토큰과 구분하지 못한다
    public Optional<Long> findUserIdOfRevoked(String tokenHash) {
        return jdbcClient.sql("""
                         SELECT user_id
                         FROM refresh_token
                         WHERE token_hash = :tokenHash
                         AND revoked_at is NOT NULL
                        """)
                .param("tokenHash", tokenHash)
                .query(Long.class)
                .optional();
    }

    // 이미 폐기된 행은 건드리지 않아 원래 폐기 시각이 남는다
    public void revokeAllByUserId(long userId) {
        jdbcClient.sql("""
                UPDATE refresh_token SET revoked_at = now()
                WHERE user_id = :userId
                AND revoked_at is NULL
                """)
                .param("userId", userId)
                .update();
    }
}
