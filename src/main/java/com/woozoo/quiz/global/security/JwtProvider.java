package com.woozoo.quiz.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Component
public class JwtProvider {

    private final SecretKey secretKey;
    private final Duration accessTokenExpiration;

    public JwtProvider(@Value("${jwt.secret}") String secret, @Value("${jwt.access-token-expiration}") Duration accessTokenExpiration) {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenExpiration = accessTokenExpiration;
    }

    public String createAccessToken(long userId) {
        Instant now = Instant.now();
        return Jwts.builder().subject(String.valueOf(userId)).issuedAt(Date.from(now)).expiration(Date.from(now.plus(accessTokenExpiration))).signWith(secretKey).compact();
    }

    public Optional<Long> parseUserId(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
            return Optional.of(Long.valueOf(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            // 토큰 문제만 잡는다. Exception 전체를 잡으면 우리 코드의 버그까지 "잘못된 토큰"으로 묻힌다
            return Optional.empty();
        }
    }
}
