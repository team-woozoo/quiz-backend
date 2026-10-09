package com.woozoo.quiz.auth.persistence;

import com.woozoo.quiz.TestcontainersConfiguration;
import com.woozoo.quiz.auth.domain.NewUser;
import com.woozoo.quiz.auth.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class UserRepositoryTest {

    @Autowired
    UserRepository userRepository;

    private final NewUser newUser = new NewUser("test@example.com", "hash", "tester");

    @Test
    void 저장한_사용자를_이메일로_찾는다() {
        long id = userRepository.save(newUser);

        assertThat(userRepository.findByEmail("test@example.com"))
                .contains(new User(id, "test@example.com", "hash", "tester"));
    }

    @Test
    void 없는_이메일이면_빈_값을_돌려준다() {
        assertThat(userRepository.findByEmail("nobody@example.com")).isEmpty();
    }

    @Test
    void 이메일이_있는지_확인한다() {
        userRepository.save(newUser);

        assertThat(userRepository.existsByEmail("test@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("nobody@example.com")).isFalse();
    }

    // 동시 가입처럼 확인을 통과한 중복은 DB 제약이 막는다. 서비스는 이 예외를 EMAIL_ALREADY_EXISTS 로 바꾼다
    @Test
    void 같은_이메일을_두_번_저장하면_DB_제약이_막는다() {
        userRepository.save(newUser);

        assertThatThrownBy(() -> userRepository.save(new NewUser("test@example.com", "other", "other")))
                .isInstanceOf(DuplicateKeyException.class);
    }
}
