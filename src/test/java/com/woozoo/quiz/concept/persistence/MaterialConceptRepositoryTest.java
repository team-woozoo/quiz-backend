package com.woozoo.quiz.concept.persistence;

import com.woozoo.quiz.TestcontainersConfiguration;
import com.woozoo.quiz.auth.domain.NewUser;
import com.woozoo.quiz.auth.persistence.UserRepository;
import com.woozoo.quiz.concept.domain.ConceptTag;
import com.woozoo.quiz.concept.domain.MaterialConcept;
import com.woozoo.quiz.material.domain.FileType;
import com.woozoo.quiz.material.domain.MaterialChunk;
import com.woozoo.quiz.material.domain.NewMaterial;
import com.woozoo.quiz.material.persistence.MaterialRepository;
import org.junit.jupiter.api.BeforeEach;
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
class MaterialConceptRepositoryTest {

    @Autowired
    MaterialConceptRepository materialConceptRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    MaterialRepository materialRepository;
    @Autowired
    JdbcClient jdbcClient;

    long materialId;
    long otherMaterialId;

    @BeforeEach
    void setup() {
        long userId = userRepository.save(new NewUser("concept@test.com", "1234", "홍길동"));
        materialId = materialRepository.save(new NewMaterial(userId, "네트워크 5강", "05.pdf", FileType.PDF, 30, null));
        otherMaterialId = materialRepository.save(new NewMaterial(userId, "네트워크 6강", "06.pdf", FileType.PDF, 30, null));
    }

    private long saveChunk(long materialId, int seq, int pageNo) {
        materialRepository.saveChunks(materialId, List.of(new MaterialChunk(seq, pageNo, "페이지 " + pageNo)));
        return materialRepository.findChunks(materialId).stream()
                .filter(chunk -> chunk.seq() == seq)
                .findFirst().orElseThrow().id();
    }

    @Test
    void 생성에_쓸_개념을_청크_순번_태그_문제_수와_함께_만든_순서대로_조회한다() {
        long chunk1 = saveChunk(materialId, 1, 3);
        long chunk2 = saveChunk(materialId, 2, 4);
        long tcpId = materialConceptRepository.save(materialId, "TCP 연결", "연결 지향 전송",
                List.of(ConceptTag.RULE, ConceptTag.CONTRAST));
        long udpId = materialConceptRepository.save(materialId, "UDP", "비연결형 전송", List.of());
        materialConceptRepository.save(otherMaterialId, "IP", "패킷 전달", List.of());
        // 연결 순서와 상관없이 청크 순번은 작은 것부터 온다
        materialConceptRepository.linkChunk(tcpId, chunk2);
        materialConceptRepository.linkChunk(tcpId, chunk1);

        List<MaterialConcept> result = materialConceptRepository.findForGeneration(materialId);

        assertThat(result).containsExactly(
                new MaterialConcept(tcpId, "TCP 연결", "연결 지향 전송",
                        List.of(ConceptTag.RULE, ConceptTag.CONTRAST), List.of(1, 2), 0),
                new MaterialConcept(udpId, "UDP", "비연결형 전송", List.of(), List.of(), 0));
    }

    // 업로드 분석 전에 생긴 개념은 태그가 NULL 이다. 생성 쪽은 빈 목록으로 받아 NULL 을 다루지 않게 한다
    @Test
    void 태그가_NULL_인_개념은_빈_목록으로_조회한다() {
        jdbcClient.sql("INSERT INTO material_concept (material_id, name, description) VALUES (:materialId, '예전 개념', '설명')")
                .param("materialId", materialId)
                .update();

        assertThat(materialConceptRepository.findForGeneration(materialId))
                .singleElement()
                .satisfies(concept -> assertThat(concept.tags()).isEmpty());
    }

    @Test
    void 분석한_개념을_태그와_함께_저장한다() {
        long id = materialConceptRepository.save(materialId, "TCP 연결", "연결 지향 전송",
                List.of(ConceptTag.CONTRAST, ConceptTag.RULE));

        String saved = jdbcClient.sql("""
                        SELECT name || ':' || description || ':' || array_to_string(tags, ',')
                        FROM material_concept WHERE id = :id
                        """)
                .param("id", id)
                .query(String.class)
                .single();
        assertThat(saved).isEqualTo("TCP 연결:연결 지향 전송:CONTRAST,RULE");
    }

    // 빈 배열은 분석했지만 재료가 없다는 뜻이라, 분석 전 개념의 NULL 과 구분돼야 한다
    @Test
    void 태그가_없으면_빈_배열로_저장한다() {
        long id = materialConceptRepository.save(materialId, "TCP 연결", "연결 지향 전송", List.of());

        Integer tagCount = jdbcClient.sql("SELECT cardinality(tags) FROM material_concept WHERE id = :id")
                .param("id", id)
                .query(Integer.class)
                .single();
        assertThat(tagCount).isZero();
    }

    @Test
    void 개념이_없는_자료는_빈_목록을_돌려준다() {
        assertThat(materialConceptRepository.findForGeneration(materialId)).isEmpty();
    }

    // 이름 유일 제약은 자료마다 걸린다
    @Test
    void 다른_자료면_같은_이름의_개념을_만들_수_있다() {
        long first = materialConceptRepository.save(materialId, "TCP 연결", "설명", List.of());
        long other = materialConceptRepository.save(otherMaterialId, "TCP 연결", "설명", List.of());

        assertThat(other).isNotEqualTo(first);
    }

    @Test
    void 이미_이어진_청크를_다시_이어도_한_번만_남는다() {
        long conceptId = materialConceptRepository.save(materialId, "TCP 연결", "설명", List.of());
        long chunkId = saveChunk(materialId, 1, 1);

        materialConceptRepository.linkChunk(conceptId, chunkId);
        materialConceptRepository.linkChunk(conceptId, chunkId);

        Long count = jdbcClient.sql("SELECT count(*) FROM concept_chunk WHERE material_concept_id = :conceptId")
                .param("conceptId", conceptId)
                .query(Long.class)
                .single();
        assertThat(count).isEqualTo(1L);
    }
}
