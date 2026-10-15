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
                .param("expiresAt", expiresAt.atOffset(ZoneOffset.UTC))
                .update();
    }

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
