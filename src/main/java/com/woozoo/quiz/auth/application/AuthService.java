package com.woozoo.quiz.auth.application;

import com.woozoo.quiz.auth.domain.NewUser;
import com.woozoo.quiz.auth.domain.User;
import com.woozoo.quiz.auth.persistence.RefreshTokenRepository;
import com.woozoo.quiz.auth.persistence.UserRepository;
import com.woozoo.quiz.global.error.BusinessException;
import com.woozoo.quiz.global.error.ErrorCode;
import com.woozoo.quiz.global.security.JwtProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Service
@Slf4j
public class AuthService {

    // BCrypt 는 72 바이트를 넘는 비밀번호에 예외를 던진다. 요청 DTO 의 글자 수 상한으로는
    // 한글처럼 글자당 여러 바이트인 비밀번호를 다 막지 못해 여기서 바이트로 확인한다
    private static final int BCRYPT_MAX_BYTES = 72;
    private static final String PLACEHOLDER_PASSWORD = "account-absent-placeholder";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final String dummyPasswordHash;
    private final Duration refreshTokenExpiration;
    private final Duration accessTokenExpiration;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       RefreshTokenGenerator refreshTokenGenerator,
                       PasswordEncoder passwordEncoder,
                       JwtProvider jwtProvider,
                       @Value("${jwt.refresh-token-expiration}")
                       Duration refreshTokenExpiration,
                       @Value("${jwt.access-token-expiration}")
                       Duration accessTokenExpiration) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.refreshTokenExpiration = refreshTokenExpiration;
        this.accessTokenExpiration = accessTokenExpiration;
        // 계정이 없을 때 비교할 대조용 해시. 상수로 두지 않고 실제 인코더로 만들어
        // 비밀번호 해시와 같은 강도(cost)를 유지한다
        this.dummyPasswordHash = passwordEncoder.encode(PLACEHOLDER_PASSWORD);
    }

    public long signup(SignupCommand signupCommand) {
        if (exceedsBcryptLimit(signupCommand.password())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        String email = normalizeEmail(signupCommand.email());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        String passwordHash = passwordEncoder.encode(signupCommand.password());

        try {
            return userRepository.save(new NewUser(email, passwordHash, signupCommand.nickname()));
        } catch (DuplicateKeyException e) {
            // 동시에 들어온 가입은 둘 다 중복 확인을 통과한다. DB 의 UNIQUE 제약이 막은 것을 409 로 바꾼다
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS, e);
        }
    }

    /**
     * 이메일과 비밀번호를 확인하고 Access 토큰과 Refresh 토큰을 발급한다.
     * <p>
     * 응답 내용도 응답 시간도 가입 여부를 드러내지 않아야 한다. 계정이 없을 때 바로 실패하면
     * BCrypt 비교(수십~수백 ms)를 건너뛰어 응답이 눈에 띄게 빨라지고, 그 차이로 가입된 이메일을
     * 골라낼 수 있다. 그래서 계정이 없어도 대조용 해시로 비교를 한 번 한 뒤 같은 오류로 실패시킨다.
     */
    public IssuedTokens login(LoginCommand loginCommand) {
        String email = normalizeEmail(loginCommand.email());
        Optional<User> user = userRepository.findByEmail(email);

        // 너무 긴 비밀번호로는 가입할 수 없으니 틀린 비밀번호와 같다. 바로 실패하면 시간 차가 생기므로
        // 대조용 비밀번호로 바꿔 비교는 똑같이 한 번 하고 실패시킨다
        boolean passwordTooLong = exceedsBcryptLimit(loginCommand.password());
        String candidate = passwordTooLong ? PLACEHOLDER_PASSWORD : loginCommand.password();

        // || 의 단락 평가로 BCrypt 비교를 건너뛰지 않도록 먼저 계산한다
        String passwordHash = user.map(User::passwordHash).orElse(dummyPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(candidate, passwordHash);

        if (user.isEmpty() || passwordTooLong || !passwordMatches) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        return issueTokens(user.get().id());
    }

    // 아무것도 내주지 않으므로 결과를 확인하지 않는다. 없거나 이미 폐기된 토큰이어도 같은 결과라 재시도에 안전하다
    public void logout(String refreshToken) {
        refreshTokenRepository.revoke(refreshTokenGenerator.hashToken(refreshToken));
    }

    // 폐기와 새 토큰 저장을 한 트랜잭션으로 묶는다. 다만 재사용을 감지해 토큰을 모두 폐기한 뒤 던지는
    // BusinessException 이 그 폐기까지 되돌리지 않도록 롤백 대상에서 뺀다 (RefreshTokenReuseTest 가 지킨다)
    @Transactional(noRollbackFor = BusinessException.class)
    public IssuedTokens refresh(String refreshToken) {
        String hash = refreshTokenGenerator.hashToken(refreshToken);
        Optional<Long> userId = refreshTokenRepository.revoke(hash);

        if (userId.isPresent()) {
            return issueTokens(userId.get());
        }

        refreshTokenRepository.findUserIdOfRevoked(hash)
                .ifPresent(ownerId -> {
                    log.warn("이미 사용된 refresh 토큰 재사용 감지, 사용자 {} 의 토큰을 모두 폐기한다", ownerId);
                    refreshTokenRepository.revokeAllByUserId(ownerId);
                });

        // 없는 토큰, 만료, 재사용을 같은 오류로 답해 무슨 일이 일어났는지 드러내지 않는다
        throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    // Postgres 의 UNIQUE 는 대소문자를 구분해서, 정규화하지 않으면 같은 주소로 계정이 두 개 생긴다
    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private boolean exceedsBcryptLimit(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES;
    }

    private IssuedTokens issueTokens(long userId) {
        String accessToken = jwtProvider.createAccessToken(userId);
        String refreshToken = refreshTokenGenerator.generateToken();

        // 발급할 때마다 만료를 다시 잡아(슬라이딩), 앱을 계속 쓰면 로그인이 유지된다
        refreshTokenRepository.save(userId, refreshTokenGenerator.hashToken(refreshToken),
                Instant.now().plus(refreshTokenExpiration));

        return new IssuedTokens(accessToken, refreshToken, accessTokenExpiration.toSeconds());
    }
}
