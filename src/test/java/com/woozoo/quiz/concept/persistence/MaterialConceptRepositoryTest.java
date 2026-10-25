package com.woozoo.quiz.concept.persistence;

import com.woozoo.quiz.TestcontainersConfiguration;
import com.woozoo.quiz.auth.domain.NewUser;
import com.woozoo.quiz.auth.persistence.UserRepository;
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
import static org.assertj.core.api.Assertions.tuple;

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
        materialId = materialRepository.save(new NewMaterial(userId, "네트워크 5강", "05.pdf", FileType.PDF, 30));
        otherMaterialId = materialRepository.save(new NewMaterial(userId, "네트워크 6강", "06.pdf", FileType.PDF, 30));
    }

    // 개념 저장은 퀴즈 저장 단계에서 만들어서, 지금은 직접 넣는다
    private long insertConcept(long materialId, String name, String description) {
        return jdbcClient.sql("""
                        INSERT INTO material_concept (material_id, name, description)
                        VALUES (:materialId, :name, :description)
                        RETURNING id
                        """)
                .param("materialId", materialId)
                .param("name", name)
                .param("description", description)
                .query(Long.class)
                .single();
    }

    @Test
    void 자료의_개념만_만든_순서대로_조회한다() {
        long tcpId = insertConcept(materialId, "TCP 연결", "연결 지향 전송");
        long udpId = insertConcept(materialId, "UDP", "비연결형 전송");
        insertConcept(otherMaterialId, "IP", "패킷 전달");

        List<MaterialConcept> result = materialConceptRepository.findByMaterialId(materialId);

        assertThat(result).extracting(MaterialConcept::id, MaterialConcept::name, MaterialConcept::description)
                .containsExactly(
                        tuple(tcpId, "TCP 연결", "연결 지향 전송"),
                        tuple(udpId, "UDP", "비연결형 전송"));
    }

    @Test
    void 개념이_없는_자료는_빈_목록을_돌려준다() {
        assertThat(materialConceptRepository.findByMaterialId(materialId)).isEmpty();
    }

    @Test
    void 같은_자료에_같은_이름이면_기존_개념_아이디를_돌려주고_처음_설명을_둔다() {
        long first = materialConceptRepository.findOrCreate(materialId, "TCP 연결", "처음 설명");
        long second = materialConceptRepository.findOrCreate(materialId, "TCP 연결", "나중 설명");

        assertThat(second).isEqualTo(first);
        assertThat(materialConceptRepository.findByMaterialId(materialId))
                .singleElement()
                .extracting(MaterialConcept::description)
                .isEqualTo("처음 설명");
    }

    @Test
    void 다른_자료면_같은_이름이라도_새_개념을_만든다() {
        long first = materialConceptRepository.findOrCreate(materialId, "TCP 연결", "설명");
        long other = materialConceptRepository.findOrCreate(otherMaterialId, "TCP 연결", "설명");

        assertThat(other).isNotEqualTo(first);
    }

    @Test
    void 이미_이어진_청크를_다시_이어도_한_번만_남는다() {
        long conceptId = materialConceptRepository.findOrCreate(materialId, "TCP 연결", "설명");
        materialRepository.saveChunks(materialId, List.of(new MaterialChunk(1, 1, "첫 페이지")));
        long chunkId = materialRepository.findChunks(materialId).getFirst().id();

        materialConceptRepository.linkChunk(conceptId, chunkId);
        materialConceptRepository.linkChunk(conceptId, chunkId);

        Long count = jdbcClient.sql("SELECT count(*) FROM concept_chunk WHERE material_concept_id = :conceptId")
                .param("conceptId", conceptId)
                .query(Long.class)
                .single();
        assertThat(count).isEqualTo(1L);
    }
}
