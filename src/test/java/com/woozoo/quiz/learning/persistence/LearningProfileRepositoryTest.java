package com.woozoo.quiz.learning.persistence;

import com.woozoo.quiz.TestcontainersConfiguration;
import com.woozoo.quiz.auth.domain.NewUser;
import com.woozoo.quiz.auth.persistence.UserRepository;
import com.woozoo.quiz.concept.persistence.MaterialConceptRepository;
import com.woozoo.quiz.learning.domain.CognitiveTypeStat;
import com.woozoo.quiz.learning.domain.ConceptStat;
import com.woozoo.quiz.learning.domain.NewSubmission;
import com.woozoo.quiz.learning.domain.QuestionResult;
import com.woozoo.quiz.material.domain.FileType;
import com.woozoo.quiz.material.domain.MaterialChunk;
import com.woozoo.quiz.material.domain.NewMaterial;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

// 유형과 개념을 문제마다 다르게 정해야 해서 QuizFixture 대신 저장소로 행을 직접 만든다.
// 맞힌 수는 맞은 보기를, 틀린 수는 틀린 보기를 골라 제출한다
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class LearningProfileRepositoryTest {

    @Autowired
    LearningProfileRepository learningProfileRepository;
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
    Material excel;
    long quizId;
    final Map<Long, Integer> lastOrderNoByQuiz = new HashMap<>();

    record Material(long id, long chunkId) {
    }

    @BeforeEach
    void setup() {
        userId = createUser("profile@test.com");
        excel = createMaterial(userId, "파일처리-엑셀");
        quizId = createQuiz(userId, excel);
    }

    private long createUser(String email) {
        return userRepository.save(new NewUser(email, "hash", "tester"));
    }

    private Material createMaterial(long ownerId, String title) {
        long materialId = materialRepository.save(new NewMaterial(ownerId, title, "lecture.pdf", FileType.PDF, 30));
        materialRepository.saveChunks(materialId, List.of(new MaterialChunk(1, 1, "원문")));
        return new Material(materialId, materialRepository.findChunks(materialId).getFirst().id());
    }

    private long createQuiz(long ownerId, Material material) {
        return quizRepository.save(new NewQuiz(ownerId, material.id(), QuizKind.NORMAL, QuizFormat.OX, Difficulty.NORMAL));
    }

    private long concept(Material material, String name) {
        return materialConceptRepository.findOrCreate(material.id(), name, "설명");
    }

    // 보기 O 가 정답인 OX 문제
    private long question(long quizId, Material material, long conceptId, CognitiveType type) {
        int orderNo = lastOrderNoByQuiz.merge(quizId, 1, Integer::sum);
        long questionId = quizRepository.saveQuestion(new NewQuestion(quizId, orderNo, conceptId, material.chunkId(),
                "원문", type, "문제 " + orderNo, "해설"));
        quizRepository.saveOption(questionId, 1, "O", true);
        quizRepository.saveOption(questionId, 2, "X", false);
        return questionId;
    }

    private long question(long conceptId, CognitiveType type) {
        return question(quizId, excel, conceptId, type);
    }

    // 문제 id 와 맞혔는지를 받아 제출 하나를 저장한다
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

    private void submit(Map<Long, Boolean> correctByQuestion) {
        submit(userId, quizId, correctByQuestion);
    }

    // 문제 여러 개를 맞힌 수만큼 앞에서부터 맞히고 나머지는 틀린다
    private Map<Long, Boolean> answers(List<Long> questionIds, int correctCount) {
        Map<Long, Boolean> answers = new LinkedHashMap<>();
        for (int i = 0; i < questionIds.size(); i++) {
            answers.put(questionIds.get(i), i < correctCount);
        }
        return answers;
    }

    private List<Long> questions(long conceptId, int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> question(conceptId, CognitiveType.REMEMBER))
                .toList();
    }

    @Test
    void 유형별로_푼_문제_수와_맞힌_수를_센다() {
        long tcp = concept(excel, "TCP");
        long remembered = question(tcp, CognitiveType.REMEMBER);
        long forgotten = question(tcp, CognitiveType.REMEMBER);
        long applied = question(tcp, CognitiveType.APPLY);

        submit(Map.of(remembered, true, forgotten, false, applied, true));

        assertThat(learningProfileRepository.countByCognitiveType(userId)).containsExactlyInAnyOrder(
                new CognitiveTypeStat(CognitiveType.REMEMBER, 2, 1),
                new CognitiveTypeStat(CognitiveType.APPLY, 1, 1));
    }

    // 한 트랜잭션 안의 now() 는 같은 시각이라, 제출 시각이 같을 때 나중 제출을 고르는 순서도 함께 본다
    @Test
    void 같은_문제를_여러_번_풀면_가장_최근_답_하나만_센다() {
        long tcp = concept(excel, "TCP");
        long laterRight = question(tcp, CognitiveType.REMEMBER);
        long laterWrong = question(tcp, CognitiveType.REMEMBER);

        submit(Map.of(laterRight, false, laterWrong, true));
        submit(Map.of(laterRight, true, laterWrong, false));

        assertThat(learningProfileRepository.countByCognitiveType(userId))
                .containsExactly(new CognitiveTypeStat(CognitiveType.REMEMBER, 2, 1));
    }

    @Test
    void 푼_문제가_없으면_빈_목록이다() {
        question(concept(excel, "TCP"), CognitiveType.REMEMBER);

        assertThat(learningProfileRepository.countByCognitiveType(userId)).isEmpty();
        assertThat(learningProfileRepository.findWeakConcepts(userId, 1, 5)).isEmpty();
    }

    @Test
    void 남의_제출은_세지_않는다() {
        long otherUserId = createUser("other@test.com");
        Material otherMaterial = createMaterial(otherUserId, "남의 자료");
        long otherQuizId = createQuiz(otherUserId, otherMaterial);
        long otherQuestion = question(otherQuizId, otherMaterial, concept(otherMaterial, "TCP"), CognitiveType.APPLY);
        submit(otherUserId, otherQuizId, Map.of(otherQuestion, false));

        assertThat(learningProfileRepository.countByCognitiveType(userId)).isEmpty();
        assertThat(learningProfileRepository.findWeakConcepts(userId, 1, 5)).isEmpty();
    }

    @Test
    void 약한_개념은_정답률이_낮은_순으로_푼_수와_맞힌_수를_준다() {
        long third = concept(excel, "셀 병합");
        long none = concept(excel, "SXSSF");
        long twoThirds = concept(excel, "Cell Style");
        Map<Long, Boolean> all = new LinkedHashMap<>();
        all.putAll(answers(questions(third, 3), 1));
        all.putAll(answers(questions(none, 3), 0));
        all.putAll(answers(questions(twoThirds, 3), 2));
        submit(all);

        assertThat(learningProfileRepository.findWeakConcepts(userId, 3, 5))
                .extracting(ConceptStat::conceptId, ConceptStat::conceptName, ConceptStat::materialId, ConceptStat::materialTitle,
                        ConceptStat::answeredCount, ConceptStat::correctAnswerCount)
                .containsExactly(
                        tuple(none, "SXSSF", excel.id(), "파일처리-엑셀", 3, 0),
                        tuple(third, "셀 병합", excel.id(), "파일처리-엑셀", 3, 1),
                        tuple(twoThirds, "Cell Style", excel.id(), "파일처리-엑셀", 3, 2));
    }

    // 몇 문제만 풀고 틀린 개념이 약한 개념 맨 위를 차지하지 않게 한다
    @Test
    void 최소_문제_수보다_적게_푼_개념은_빠진다() {
        long fewWrong = concept(excel, "SXSSF");
        long enough = concept(excel, "셀 병합");
        Map<Long, Boolean> all = new LinkedHashMap<>();
        all.putAll(answers(questions(fewWrong, 2), 0));
        all.putAll(answers(questions(enough, 3), 2));
        submit(all);

        assertThat(learningProfileRepository.findWeakConcepts(userId, 3, 5))
                .extracting(ConceptStat::conceptId)
                .containsExactly(enough);
    }

    // 둘 다 1/3 이어도 많이 풀고 틀린 쪽이 더 확실하게 약하다
    @Test
    void 정답률이_같으면_많이_푼_개념이_먼저다() {
        long fewer = concept(excel, "셀 병합");
        long more = concept(excel, "SXSSF");
        Map<Long, Boolean> all = new LinkedHashMap<>();
        all.putAll(answers(questions(fewer, 3), 1));
        all.putAll(answers(questions(more, 6), 2));
        submit(all);

        assertThat(learningProfileRepository.findWeakConcepts(userId, 3, 5))
                .extracting(ConceptStat::conceptId)
                .containsExactly(more, fewer);
    }

    @Test
    void 보여_줄_개수만큼만_준다() {
        long weakest = concept(excel, "SXSSF");
        long weaker = concept(excel, "셀 병합");
        Map<Long, Boolean> all = new LinkedHashMap<>();
        all.putAll(answers(questions(weakest, 3), 0));
        all.putAll(answers(questions(weaker, 3), 1));
        submit(all);

        assertThat(learningProfileRepository.findWeakConcepts(userId, 3, 1))
                .extracting(ConceptStat::conceptId)
                .containsExactly(weakest);
    }

    // 개념은 자료마다 따로 만들어져서, 이름이 같아도 다른 자료의 개념과 합쳐 세지 않는다
    @Test
    void 이름이_같아도_자료가_다르면_다른_개념으로_센다() {
        Material other = createMaterial(userId, "파일처리-PDF");
        long otherQuizId = createQuiz(userId, other);
        long excelConcept = concept(excel, "Apache POI");
        long otherConcept = concept(other, "Apache POI");
        submit(answers(questions(excelConcept, 3), 0));
        List<Long> otherQuestions = java.util.stream.IntStream.range(0, 3)
                .mapToObj(i -> question(otherQuizId, other, otherConcept, CognitiveType.REMEMBER))
                .toList();
        submit(userId, otherQuizId, answers(otherQuestions, 1));

        assertThat(learningProfileRepository.findWeakConcepts(userId, 3, 5))
                .extracting(ConceptStat::conceptId, ConceptStat::materialId, ConceptStat::materialTitle, ConceptStat::answeredCount)
                .containsExactly(
                        tuple(excelConcept, excel.id(), "파일처리-엑셀", 3),
                        tuple(otherConcept, other.id(), "파일처리-PDF", 3));
    }
}
