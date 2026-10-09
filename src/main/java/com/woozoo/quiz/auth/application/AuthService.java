package com.woozoo.quiz.auth.application;

import com.woozoo.quiz.auth.domain.NewUser;
import com.woozoo.quiz.auth.domain.User;
import com.woozoo.quiz.auth.persistence.UserRepository;
import com.woozoo.quiz.global.error.BusinessException;
import com.woozoo.quiz.global.error.ErrorCode;
import com.woozoo.quiz.global.security.JwtProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {

    // BCrypt 는 72 바이트를 넘는 비밀번호에 예외를 던진다. 요청 DTO 의 글자 수 상한으로는
    // 한글처럼 글자당 여러 바이트인 비밀번호를 다 막지 못해 여기서 바이트로 확인한다
    private static final int BCRYPT_MAX_BYTES = 72;
    private static final String PLACEHOLDER_PASSWORD = "account-absent-placeholder";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtProvider jwtProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
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
     * 이메일과 비밀번호를 확인하고 Access 토큰을 발급한다.
     * <p>
     * 응답 내용도 응답 시간도 가입 여부를 드러내지 않아야 한다. 계정이 없을 때 바로 실패하면
     * BCrypt 비교(수십~수백 ms)를 건너뛰어 응답이 눈에 띄게 빨라지고, 그 차이로 가입된 이메일을
     * 골라낼 수 있다. 그래서 계정이 없어도 대조용 해시로 비교를 한 번 한 뒤 같은 오류로 실패시킨다.
     */
    public String login(LoginCommand loginCommand) {
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
        return jwtProvider.createAccessToken(user.get().id());
    }

    // Postgres 의 UNIQUE 는 대소문자를 구분해서, 정규화하지 않으면 같은 주소로 계정이 두 개 생긴다
    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private boolean exceedsBcryptLimit(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES;
    }
}
