package com.woozoo.quiz.auth.persistence;

import com.woozoo.quiz.auth.domain.NewUser;
import com.woozoo.quiz.auth.domain.User;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserRepository {
    private final JdbcClient jdbcClient;

    public UserRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public long save(NewUser newUser) {
        return jdbcClient.sql("""
                INSERT INTO users (email, password_hash, nickname)
                VALUES (:email, :passwordHash, :nickname)
                RETURNING id
                """)
                .param("email", newUser.email())
                .param("passwordHash", newUser.passwordHash())
                .param("nickname", newUser.nickname())
                .query(Long.class)
                .single();
    }

    public boolean existsByEmail(String email) {
        return jdbcClient.sql("""
                SELECT EXISTS (SELECT 1 FROM users WHERE email = :email)
                """)
                .param("email", email)
                .query(Boolean.class)
                .single();
    }

    public Optional<User> findByEmail(String email) {
        return jdbcClient.sql("""
                SELECT id, email, password_hash, nickname
                FROM users
                WHERE email = :email
                """)
                .param("email", email)
                .query(User.class)
                .optional();
    }
}
