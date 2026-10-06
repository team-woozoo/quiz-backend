package com.woozoo.quiz.schema;


import com.woozoo.quiz.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
public class DeletePolicyTest {

    // 외래 키 순서와 무관하게 전부 센다. 테이블이 늘면 여기에도 추가한다
    private static final List<String> USER_OWNED_TABLES = List.of(
            "users", "refresh_token",
            "material", "material_chunk",
            "material_concept", "concept_chunk", "user_concept", "concept_mapping",
            "quiz", "weakness_target", "generation_job", "question", "question_option",
            "submission", "submission_answer", "wrong_answer");

    @Autowired
    JdbcClient jdbcClient;

    @Test
    void 사용자를_지우면_그_사용자의_데이터가_모두_지워진다(){
        // 1. 준비: 사용자와 모든 테이블에 데이터 한 줄씩
        Long userId = insert("INSERT INTO users (email, password_hash, nickname) VALUES (?, ?, ?) RETURNING id",
                "test@example.com", "hash", "tester");
        insert("INSERT INTO refresh_token (user_id, token_hash, expires_at) VALUES (?, ?, now() + interval '14 days') RETURNING id",
                userId, "a".repeat(64));

        Long materialId = insert("INSERT INTO material (owner_id, title, original_filename, file_type) VALUES (?, ?, ?, ?) RETURNING id",
                userId, "네트워크 3주차", "network-03.pdf", "PDF");
        Long chunkId = insert("INSERT INTO material_chunk (material_id, seq, page_no, content) VALUES (?, ?, ?, ?) RETURNING id",
                materialId, 1, 1, "클라이언트는 먼저 SYN 패킷을 보낸다.");

        Long materialConceptId = insert("INSERT INTO material_concept (material_id, name, description) VALUES (?, ?, ?) RETURNING id",
                materialId, "TCP 연결 수립", "SYN, SYN-ACK, ACK 로 연결을 맺는 절차");
        execute("INSERT INTO concept_chunk (material_concept_id, material_chunk_id) VALUES (?, ?)",
                materialConceptId, chunkId);
        Long userConceptId = insert("INSERT INTO user_concept (user_id, name, description) VALUES (?, ?, ?) RETURNING id",
                userId, "TCP 연결 수립", "SYN, SYN-ACK, ACK 로 연결을 맺는 절차");
        execute("INSERT INTO concept_mapping (material_concept_id, user_concept_id) VALUES (?, ?)",
                materialConceptId, userConceptId);

        // 일반 퀴즈와 약점 퀴즈를 둘 다 만들어 weakness_target 까지 지나가게 한다
        Long normalQuizId = insert("INSERT INTO quiz (user_id, material_id, kind, format, difficulty) VALUES (?, ?, 'NORMAL', 'OX', 'NORMAL') RETURNING id",
                userId, materialId);
        Long weaknessQuizId = insert("INSERT INTO quiz (user_id, kind, format, difficulty) VALUES (?, 'WEAKNESS', 'OX', 'NORMAL') RETURNING id",
                userId);
        insert("INSERT INTO weakness_target (quiz_id, user_concept_id, cognitive_type) VALUES (?, ?, 'APPLY') RETURNING id",
                weaknessQuizId, userConceptId);
        insert("INSERT INTO generation_job (user_id, material_id, kind, format, difficulty, status, quiz_id) VALUES (?, ?, 'NORMAL', 'OX', 'NORMAL', 'DONE', ?) RETURNING id",
                userId, materialId, normalQuizId);
        insert("INSERT INTO generation_job (user_id, kind, format, difficulty, status) VALUES (?, 'WEAKNESS', 'OX', 'NORMAL', 'PENDING') RETURNING id",
                userId);

        Long normalQuestionId = insertOxQuestion(normalQuizId, materialConceptId, chunkId);
        insertOxQuestion(weaknessQuizId, materialConceptId, chunkId);
        Long wrongOptionId = jdbcClient.sql("SELECT id FROM question_option WHERE question_id = ? AND NOT is_correct")
                .params(normalQuestionId)
                .query(Long.class)
                .single();

        Long submissionId = insert("INSERT INTO submission (user_id, quiz_id) VALUES (?, ?) RETURNING id",
                userId, normalQuizId);
        insert("INSERT INTO submission_answer (submission_id, question_id, selected_option_id, is_correct) VALUES (?, ?, ?, false) RETURNING id",
                submissionId, normalQuestionId, wrongOptionId);
        insert("INSERT INTO wrong_answer (user_id, question_id) VALUES (?, ?) RETURNING id",
                userId, normalQuestionId);

        // 준비가 의도대로 됐는지 먼저 확인한다. 비어 있는 테이블이 있으면 아래 검증이 아무것도 증명하지 못한다
        for (String table : USER_OWNED_TABLES) {
            assertThat(count(table)).as(table).isPositive();
        }

        // 2. 실행: 사용자 삭제
        execute("DELETE FROM users WHERE id = ?", userId);

        // 3. 검증: 모든 테이블이 비었는지
        for (String table : USER_OWNED_TABLES) {
            assertThat(count(table)).as(table).isZero();
        }
    }

    @Test
    void 자료를_지우면_그_자료에서_나온_것만_지워지고_약점_퀴즈는_남는다() {
        // 1. 준비: 자료 두 개(A 는 지울 것, B 는 남길 것)와 두 자료에 걸친 약점 퀴즈
        Long userId = insert("INSERT INTO users (email, password_hash, nickname) VALUES (?, ?, ?) RETURNING id",
                "test@example.com", "hash", "tester");
        MaterialFixture a = insertMaterial(userId, "네트워크 3주차");
        MaterialFixture b = insertMaterial(userId, "네트워크 4주차");

        Long normalQuizA = insert("INSERT INTO quiz (user_id, material_id, kind, format, difficulty) VALUES (?, ?, 'NORMAL', 'OX', 'NORMAL') RETURNING id",
                userId, a.materialId());
        Long normalQuizB = insert("INSERT INTO quiz (user_id, material_id, kind, format, difficulty) VALUES (?, ?, 'NORMAL', 'OX', 'NORMAL') RETURNING id",
                userId, b.materialId());
        insertOxQuestion(normalQuizA, a.conceptId(), a.chunkId());
        insertOxQuestion(normalQuizB, b.conceptId(), b.chunkId());
        insert("INSERT INTO generation_job (user_id, material_id, kind, format, difficulty, status, quiz_id) VALUES (?, ?, 'NORMAL', 'OX', 'NORMAL', 'DONE', ?) RETURNING id",
                userId, a.materialId(), normalQuizA);

        Long weaknessQuizId = insert("INSERT INTO quiz (user_id, kind, format, difficulty) VALUES (?, 'WEAKNESS', 'OX', 'NORMAL') RETURNING id",
                userId);
        Long weaknessQuestionFromA = insertOxQuestion(weaknessQuizId, a.conceptId(), a.chunkId(), 1);
        Long weaknessQuestionFromB = insertOxQuestion(weaknessQuizId, b.conceptId(), b.chunkId(), 2);
        Long submissionId = insert("INSERT INTO submission (user_id, quiz_id) VALUES (?, ?) RETURNING id",
                userId, weaknessQuizId);
        insert("INSERT INTO submission_answer (submission_id, question_id, is_correct) VALUES (?, ?, false) RETURNING id",
                submissionId, weaknessQuestionFromA);
        insert("INSERT INTO submission_answer (submission_id, question_id, is_correct) VALUES (?, ?, true) RETURNING id",
                submissionId, weaknessQuestionFromB);
        insert("INSERT INTO wrong_answer (user_id, question_id) VALUES (?, ?) RETURNING id",
                userId, weaknessQuestionFromA);

        // 2. 실행: 자료 A 삭제
        execute("DELETE FROM material WHERE id = ?", a.materialId());

        // 3. 검증
        // A 에서 나온 것은 모두 사라진다
        assertThat(countWhere("material_chunk", "material_id", a.materialId())).isZero();
        assertThat(countWhere("material_concept", "material_id", a.materialId())).isZero();
        assertThat(countWhere("concept_mapping", "material_concept_id", a.conceptId())).isZero();
        assertThat(countWhere("quiz", "id", normalQuizA)).isZero();
        assertThat(countWhere("generation_job", "material_id", a.materialId())).isZero();
        assertThat(countWhere("question", "id", weaknessQuestionFromA)).isZero();
        assertThat(countWhere("wrong_answer", "question_id", weaknessQuestionFromA)).isZero();

        // 약점 퀴즈와 그 제출 기록은 남고, B 에서 나온 문제와 답만 남는다
        assertThat(countWhere("quiz", "id", weaknessQuizId)).isOne();
        assertThat(countWhere("question", "quiz_id", weaknessQuizId)).isOne();
        assertThat(countWhere("submission", "id", submissionId)).isOne();
        assertThat(countWhere("submission_answer", "submission_id", submissionId)).isOne();

        // B 는 그대로다
        assertThat(countWhere("quiz", "id", normalQuizB)).isOne();
        assertThat(countWhere("material_concept", "material_id", b.materialId())).isOne();

        // 대표 개념은 사용자의 것이라 자료를 지워도 남는다. A 에서만 매핑됐던 대표 개념은 매핑 없이 남는다
        assertThat(countWhere("user_concept", "id", a.userConceptId())).isOne();
        assertThat(countWhere("concept_mapping", "user_concept_id", a.userConceptId())).isZero();
    }

    private record MaterialFixture(Long materialId, Long chunkId, Long conceptId, Long userConceptId) {
    }

    private MaterialFixture insertMaterial(Long userId, String title) {
        Long materialId = insert("INSERT INTO material (owner_id, title, original_filename, file_type) VALUES (?, ?, ?, 'PDF') RETURNING id",
                userId, title, title + ".pdf");
        Long chunkId = insert("INSERT INTO material_chunk (material_id, seq, page_no, content) VALUES (?, 1, 1, ?) RETURNING id",
                materialId, "클라이언트는 먼저 SYN 패킷을 보낸다.");
        Long conceptId = insert("INSERT INTO material_concept (material_id, name, description) VALUES (?, ?, ?) RETURNING id",
                materialId, "TCP 연결 수립", "SYN, SYN-ACK, ACK 로 연결을 맺는 절차");
        execute("INSERT INTO concept_chunk (material_concept_id, material_chunk_id) VALUES (?, ?)",
                conceptId, chunkId);
        Long userConceptId = insert("INSERT INTO user_concept (user_id, name, description) VALUES (?, ?, ?) RETURNING id",
                userId, "TCP 연결 수립", "SYN, SYN-ACK, ACK 로 연결을 맺는 절차");
        execute("INSERT INTO concept_mapping (material_concept_id, user_concept_id) VALUES (?, ?)",
                conceptId, userConceptId);
        return new MaterialFixture(materialId, chunkId, conceptId, userConceptId);
    }

    private Long insertOxQuestion(Long quizId, Long materialConceptId, Long chunkId) {
        return insertOxQuestion(quizId, materialConceptId, chunkId, 1);
    }

    private Long insertOxQuestion(Long quizId, Long materialConceptId, Long chunkId, int orderNo) {
        Long questionId = insert("""
                INSERT INTO question (quiz_id, order_no, material_concept_id, source_chunk_id, source_quote, cognitive_type, content, explanation)
                VALUES (?, ?, ?, ?, ?, 'REMEMBER', ?, ?)
                RETURNING id
                """,
                quizId, orderNo, materialConceptId, chunkId, "클라이언트는 먼저 SYN 패킷을 보낸다.",
                "TCP 연결 수립에서 클라이언트가 처음 보내는 패킷은 SYN 이다.", "3-way handshake 의 첫 단계다.");
        insert("INSERT INTO question_option (question_id, order_no, content, is_correct) VALUES (?, 1, 'O', true) RETURNING id",
                questionId);
        insert("INSERT INTO question_option (question_id, order_no, content, is_correct) VALUES (?, 2, 'X', false) RETURNING id",
                questionId);
        return questionId;
    }

    private Long insert(String sql, Object... params) {
        return jdbcClient.sql(sql).params(params).query(Long.class).single();
    }

    private void execute(String sql, Object... params) {
        jdbcClient.sql(sql).params(params).update();
    }

    // 테이블 이름은 파라미터로 넣을 수 없어 문자열로 붙인다. 이름은 위 상수에서만 오므로 테스트에서는 안전하다
    private Long count(String table) {
        return jdbcClient.sql("SELECT COUNT(*) FROM " + table).query(Long.class).single();
    }

    private Long countWhere(String table, String column, Long value) {
        return jdbcClient.sql("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?")
                .params(value)
                .query(Long.class)
                .single();
    }
}
