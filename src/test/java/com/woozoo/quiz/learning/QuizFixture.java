package com.woozoo.quiz.learning;

import com.woozoo.quiz.auth.domain.NewUser;
import com.woozoo.quiz.auth.persistence.UserRepository;
import com.woozoo.quiz.concept.persistence.MaterialConceptRepository;
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

import java.util.ArrayList;
import java.util.List;

// 제출과 오답 노트는 외래 키로 사용자, 퀴즈, 문제, 보기에 묶여 있어 그 행들을 한 번에 만든다.
// 생성기를 거치지 않고 저장소로 바로 넣어, 문제 수와 정답 위치를 테스트가 정한다
public class QuizFixture {

    private final UserRepository userRepository;
    private final MaterialRepository materialRepository;
    private final MaterialConceptRepository materialConceptRepository;
    private final QuizRepository quizRepository;

    public QuizFixture(UserRepository userRepository,
                       MaterialRepository materialRepository,
                       MaterialConceptRepository materialConceptRepository,
                       QuizRepository quizRepository) {
        this.userRepository = userRepository;
        this.materialRepository = materialRepository;
        this.materialConceptRepository = materialConceptRepository;
        this.quizRepository = quizRepository;
    }

    public record Created(long userId, long quizId, List<Long> questionIds, List<StoredOption> options) {

        // 문제의 정답 보기 id
        public long correctOptionOf(long questionId) {
            return options.stream()
                    .filter(option -> option.questionId() == questionId && option.isCorrect())
                    .findFirst().orElseThrow().id();
        }

        // 문제의 오답 보기 id
        public long wrongOptionOf(long questionId) {
            return options.stream()
                    .filter(option -> option.questionId() == questionId && !option.isCorrect())
                    .findFirst().orElseThrow().id();
        }
    }

    public long createUser(String email) {
        return userRepository.save(new NewUser(email, "hash", "tester"));
    }

    // OX 문제 questionCount 개짜리 퀴즈. 문제마다 보기 O 가 정답이다
    public Created createQuiz(long userId, int questionCount) {
        long materialId = materialRepository.save(new NewMaterial(userId, "네트워크 5강", "05.pdf", FileType.PDF, 30));
        materialRepository.saveChunks(materialId, List.of(new MaterialChunk(1, 1, "TCP는 연결 지향 프로토콜이다.")));
        long chunkId = materialRepository.findChunks(materialId).getFirst().id();
        long conceptId = materialConceptRepository.findOrCreate(materialId, "TCP", "연결 지향 전송");

        long quizId = quizRepository.save(new NewQuiz(userId, materialId, QuizKind.NORMAL, QuizFormat.OX, Difficulty.NORMAL));
        List<Long> questionIds = new ArrayList<>();
        for (int orderNo = 1; orderNo <= questionCount; orderNo++) {
            long questionId = quizRepository.saveQuestion(new NewQuestion(quizId, orderNo, conceptId, chunkId,
                    "TCP는 연결 지향 프로토콜이다.", CognitiveType.REMEMBER, "문제 " + orderNo, "해설"));
            quizRepository.saveOption(questionId, 1, "O", true);
            quizRepository.saveOption(questionId, 2, "X", false);
            questionIds.add(questionId);
        }
        return new Created(userId, quizId, List.copyOf(questionIds), quizRepository.findOptions(quizId));
    }

    public Created createQuiz(String email, int questionCount) {
        return createQuiz(createUser(email), questionCount);
    }
}
