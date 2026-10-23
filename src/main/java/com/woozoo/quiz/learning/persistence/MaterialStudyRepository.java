package com.woozoo.quiz.learning.persistence;

import com.woozoo.quiz.learning.domain.MaterialConceptStat;
import com.woozoo.quiz.learning.domain.MaterialStat;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

// 자료 단위로 내 풀이 기록을 센다. 만든 문제 전체를 세고, 안 푼 문제는 최근 답이 없어 푼 수와 맞힌 수에서 빠진다.
// 약점 퀴즈는 자료 없이 만들어 자료별 수에 넣지 않는다
@Repository
public class MaterialStudyRepository {

    private static final String MATERIAL_STATS = LatestAnswers.WITH_LATEST + """
            SELECT qz.material_id,
                   COUNT(*) AS question_count,
                   COUNT(l.question_id) AS answered_count,
                   COUNT(l.question_id) FILTER (WHERE l.is_correct) AS correct_count
            FROM quiz qz
            JOIN question q ON q.quiz_id = qz.id
            LEFT JOIN latest l ON l.question_id = q.id
            WHERE qz.user_id = :userId AND qz.material_id IS NOT NULL
            """;

    private final JdbcClient jdbcClient;

    public MaterialStudyRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    // 퀴즈를 만든 적 없는 자료는 결과에 없다
    public List<MaterialStat> findMaterialStats(long userId) {
        return jdbcClient.sql(MATERIAL_STATS + """
                        GROUP BY qz.material_id
                        """)
                .param("userId", userId)
                .query(MaterialStat.class)
                .list();
    }

    public Optional<MaterialStat> findMaterialStat(long userId, long materialId) {
        return jdbcClient.sql(MATERIAL_STATS + """
                        AND qz.material_id = :materialId
                        GROUP BY qz.material_id
                        """)
                .param("userId", userId)
                .param("materialId", materialId)
                .query(MaterialStat.class)
                .optional();
    }

    // 문제가 하나도 안 남은 개념도 페이지는 있으니 빼지 않는다. 페이지는 작은 번호부터 준다
    public List<MaterialConceptStat> findConceptStats(long userId, long materialId) {
        return jdbcClient.sql(LatestAnswers.WITH_LATEST + """
                        SELECT mc.id AS concept_id,
                               mc.name AS concept_name,
                               ARRAY(SELECT c.page_no
                                     FROM concept_chunk cc
                                     JOIN material_chunk c ON c.id = cc.material_chunk_id
                                     WHERE cc.material_concept_id = mc.id AND c.page_no IS NOT NULL
                                     ORDER BY c.page_no) AS page_nos,
                               COUNT(q.id) AS question_count,
                               COUNT(l.question_id) AS answered_count,
                               COUNT(l.question_id) FILTER (WHERE l.is_correct) AS correct_count
                        FROM material_concept mc
                        LEFT JOIN question q ON q.material_concept_id = mc.id
                        LEFT JOIN latest l ON l.question_id = q.id
                        WHERE mc.material_id = :materialId
                        GROUP BY mc.id, mc.name
                        ORDER BY mc.id
                        """)
                .param("userId", userId)
                .param("materialId", materialId)
                .query((rs, rowNum) -> toConceptStat(rs))
                .list();
    }

    // 배열 컬럼은 레코드로 바로 옮겨지지 않아 직접 꺼낸다
    private static MaterialConceptStat toConceptStat(ResultSet rs) throws SQLException {
        Integer[] pageNos = (Integer[]) rs.getArray("page_nos").getArray();
        return new MaterialConceptStat(
                rs.getLong("concept_id"),
                rs.getString("concept_name"),
                List.of(pageNos),
                rs.getInt("question_count"),
                rs.getInt("answered_count"),
                rs.getInt("correct_count"));
    }
}
