package com.woozoo.quiz.learning.persistence;

import com.woozoo.quiz.TestcontainersConfiguration;
import com.woozoo.quiz.auth.domain.NewUser;
import com.woozoo.quiz.auth.persistence.UserRepository;
import com.woozoo.quiz.concept.persistence.MaterialConceptRepository;
import com.woozoo.quiz.learning.domain.MaterialConceptStat;
import com.woozoo.quiz.learning.domain.MaterialStat;
import com.woozoo.quiz.learning.domain.NewSubmission;
import com.woozoo.quiz.learning.domain.QuestionResult;
import com.woozoo.quiz.material.domain.FileType;
import com.woozoo.quiz.material.domain.MaterialChunk;
import com.woozoo.quiz.material.domain.NewMaterial;
import com.woozoo.quiz.material.domain.StoredChunk;
import com.woozoo.quiz.material.persistence.MaterialRepository;
import com.woozoo.quiz.quiz.domain.CognitiveType;
import com.woozoo.quiz.quiz.domain.Difficulty;
import com.woozoo.quiz.quiz.domain.NewQuestion;
import com.woozoo.quiz.quiz.domain.NewQuiz;
import com.woozoo.quiz.quiz.domain.QuizFormat;
import com.woozoo.quiz.quiz.domain.QuizKind;
import com.woozoo.quiz.quiz.domain.StoredOption;
import com.woozoo.quiz.quiz.persistence.QuizRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

// 자료 하나에 페이지 다섯 쪽과 개념을 두고, 문제와 제출을 저장소로 직접 넣어 자료 단위로 센 값을 본다
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class MaterialStudyRepositoryTest {

    @Autowired
    MaterialStudyRepository materialStudyRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    MaterialRepository materialRepository;
    @Autowired
    MaterialConceptRepository materialConceptRepository;
    @Autowired
    QuizRepository quizRepository;
    @Autowired
    SubmissionRepository submissionRepository;

    long userId;
    long materialId;
    // 페이지 번호 → 청크 id
    Map<Integer, Long> chunkIdByPage;
    final Map<Long, Integer> lastOrderNoByQuiz = new HashMap<>();

    @BeforeEach
    void setup() {
        userId = createUser("material@test.com");
        materialId = createMaterial(userId, "컴퓨터네트워크 4주차");
    }

    private long createUser(String email) {
        return userRepository.save(new NewUser(email, "hash", "tester"));
    }

    // 2쪽은 글자가 없는 페이지라 청크가 없다고 보고, 청크 번호와 페이지 번호가 어긋나게 둔다
    private long createMaterial(long ownerId, String title) {
        long id = materialRepository.save(new NewMaterial(ownerId, title, "lecture.pdf", FileType.PDF));
        materialRepository.saveChunks(id, List.of(
                new MaterialChunk(1, 1, "1쪽"),
                new MaterialChunk(2, 3, "3쪽"),
                new MaterialChunk(3, 4, "4쪽"),
                new MaterialChunk(4, 5, "5쪽")));
        List<StoredChunk> chunks = materialRepository.findChunks(id);
        chunkIdByPage = Map.of(1, chunks.get(0).id(), 3, chunks.get(1).id(), 4, chunks.get(2).id(), 5, chunks.get(3).id());
        return id;
    }

    private long concept(String name, int... pages) {
        long conceptId = materialConceptRepository.findOrCreate(materialId, name, "설명");
        for (int page : pages) {
            materialConceptRepository.linkChunk(conceptId, chunkIdByPage.get(page));
        }
        return conceptId;
    }

    private long createQuiz(long ownerId, Long materialId) {
        QuizKind kind = materialId == null ? QuizKind.WEAKNESS : QuizKind.NORMAL;
        return quizRepository.save(new NewQuiz(ownerId, materialId, kind, QuizFormat.OX, Difficulty.NORMAL));
    }

    // 보기 O 가 정답인 OX 문제
    private long question(long quizId, long conceptId) {
        int orderNo = lastOrderNoByQuiz.merge(quizId, 1, Integer::sum);
        long questionId = quizRepository.saveQuestion(new NewQuestion(quizId, orderNo, conceptId,
                chunkIdByPage.get(1), "원문", CognitiveType.REMEMBER, "문제 " + orderNo, "해설"));
        quizRepository.saveOption(questionId, 1, "O", true);
        quizRepository.saveOption(questionId, 2, "X", false);
        return questionId;
    }

    // 보낸 문제만 저장한다. 보내지 않은 문제는 최근 답이 없는 안 푼 문제다
    private void submit(long userId, long quizId, Map<Long, Boolean> correctByQuestion) {
        List<StoredOption> options = quizRepository.findOptions(quizId);
        long submissionId = submissionRepository.save(new NewSubmission(userId, quizId));
        submissionRepository.saveAnswers(submissionId, correctByQuestion.entrySet().stream()
                .map(answer -> new QuestionResult(answer.getKey(), options.stream()
                        .filter(option -> option.questionId() == answer.getKey()
                                && option.isCorrect() == answer.getValue())
                        .findFirst().orElseThrow().id(), answer.getValue()))
                .toList());
    }

    @Test
    void 자료별로_만든_문제_수와_푼_수와_맞힌_수를_센다() {
        long tcp = concept("TCP 연결 과정", 4);
        long quizId = createQuiz(userId, materialId);
        long right = question(quizId, tcp);
        long wrong = question(quizId, tcp);
        question(quizId, tcp);
        submit(userId, quizId, Map.of(right, true, wrong, false));

        assertThat(materialStudyRepository.findMaterialStats(userId))
                .containsExactly(new MaterialStat(materialId, 3, 2, 1));
    }

    @Test
    void 자료의_여러_퀴즈를_합치고_같은_문제는_최근_답으로_센다() {
        long tcp = concept("TCP 연결 과정", 4);
        long firstQuiz = createQuiz(userId, materialId);
        long retried = question(firstQuiz, tcp);
        long secondQuiz = createQuiz(userId, materialId);
        long other = question(secondQuiz, tcp);
        submit(userId, firstQuiz, Map.of(retried, false));
        submit(userId, firstQuiz, Map.of(retried, true));
        submit(userId, secondQuiz, Map.of(other, false));

        assertThat(materialStudyRepository.findMaterialStat(userId, materialId))
                .contains(new MaterialStat(materialId, 2, 2, 1));
    }

    @Test
    void 퀴즈를_만든_적_없는_자료는_통계가_없다() {
        assertThat(materialStudyRepository.findMaterialStats(userId)).isEmpty();
        assertThat(materialStudyRepository.findMaterialStat(userId, materialId)).isEmpty();
    }

    // 약점 퀴즈는 자료 없이 만들어 자료의 문제 수에 넣지 않는다
    @Test
    void 자료_없이_만든_퀴즈는_자료_통계에_넣지_않는다() {
        long tcp = concept("TCP 연결 과정", 4);
        long weaknessQuiz = createQuiz(userId, null);
        long question = question(weaknessQuiz, tcp);
        submit(userId, weaknessQuiz, Map.of(question, true));

        assertThat(materialStudyRepository.findMaterialStats(userId)).isEmpty();
    }

    @Test
    void 개념마다_나온_페이지와_문제_수와_푼_수와_맞힌_수를_준다() {
        long tcp = concept("TCP 연결 과정", 5, 4);
        long udp = concept("UDP 특징", 3);
        long quizId = createQuiz(userId, materialId);
        long tcpRight = question(quizId, tcp);
        long tcpWrong = question(quizId, tcp);
        question(quizId, tcp);
        long udpRight = question(quizId, udp);
        submit(userId, quizId, Map.of(tcpRight, true, tcpWrong, false, udpRight, true));

        assertThat(materialStudyRepository.findConceptStats(userId, materialId))
                .extracting(MaterialConceptStat::conceptId, MaterialConceptStat::conceptName, MaterialConceptStat::pageNos,
                        MaterialConceptStat::questionCount, MaterialConceptStat::answeredCount, MaterialConceptStat::correctCount)
                .containsExactly(
                        tuple(tcp, "TCP 연결 과정", List.of(4, 5), 3, 2, 1),
                        tuple(udp, "UDP 특징", List.of(3), 1, 1, 1));
    }

    @Test
    void 문제가_없는_개념도_페이지와_함께_0_개로_나온다() {
        long untouched = concept("연결 종료 과정", 1);

        assertThat(materialStudyRepository.findConceptStats(userId, materialId))
                .extracting(MaterialConceptStat::conceptId, MaterialConceptStat::pageNos,
                        MaterialConceptStat::questionCount, MaterialConceptStat::answeredCount)
                .containsExactly(tuple(untouched, List.of(1), 0, 0));
    }

    @Test
    void 남의_풀이는_내_자료_통계에_섞이지_않는다() {
        long tcp = concept("TCP 연결 과정", 4);
        long quizId = createQuiz(userId, materialId);
        long question = question(quizId, tcp);
        long otherUserId = createUser("other@test.com");
        submit(otherUserId, quizId, Map.of(question, true));

        assertThat(materialStudyRepository.findMaterialStat(userId, materialId))
                .contains(new MaterialStat(materialId, 1, 0, 0));
        assertThat(materialStudyRepository.findConceptStats(userId, materialId))
                .extracting(MaterialConceptStat::answeredCount)
                .containsExactly(0);
    }
}
