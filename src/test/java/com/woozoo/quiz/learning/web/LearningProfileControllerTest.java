package com.woozoo.quiz.learning.web;

import com.woozoo.quiz.TestcontainersConfiguration;
import com.woozoo.quiz.auth.persistence.UserRepository;
import com.woozoo.quiz.concept.persistence.MaterialConceptRepository;
import com.woozoo.quiz.global.security.JwtProvider;
import com.woozoo.quiz.learning.QuizFixture;
import com.woozoo.quiz.material.persistence.MaterialRepository;
import com.woozoo.quiz.quiz.persistence.QuizRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 제출 API 로 푼 기록이 유형별 정답률과 약한 개념에 어떻게 나오는지 API 로만 이어서 본다.
// QuizFixture 의 문제는 모두 "네트워크 5강" 자료의 TCP 개념, REMEMBER 유형이다
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LearningProfileControllerTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JwtProvider jwtProvider;
    @Autowired
    UserRepository userRepository;
    @Autowired
    MaterialRepository materialRepository;
    @Autowired
    MaterialConceptRepository materialConceptRepository;
    @Autowired
    QuizRepository quizRepository;

    QuizFixture fixture;
    long userId;
    String token;

    @BeforeEach
    void setup() {
        fixture = new QuizFixture(userRepository, materialRepository, materialConceptRepository, quizRepository);
        userId = fixture.createUser("profile@test.com");
        token = jwtProvider.createAccessToken(userId);
    }

    private ResultActions getWithToken(String path, String token) throws Exception {
        return mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    // 정답을 고를 문제만 보내고, 보내지 않은 문제는 서버가 틀린 것으로 채점한다
    private void submitCorrect(String token, QuizFixture.Created quiz, List<Long> correctQuestionIds) throws Exception {
        submit(token, quiz, correctQuestionIds.stream()
                .map(questionId -> "{\"questionId\": %d, \"optionId\": %d}"
                        .formatted(questionId, quiz.correctOptionOf(questionId)))
                .collect(Collectors.joining(", ")));
    }

    // 빈 답안은 제출 API 가 받지 않아, 다 틀린 제출은 첫 문제만 틀린 보기로 보낸다
    private void submitAllWrong(String token, QuizFixture.Created quiz) throws Exception {
        long first = quiz.questionIds().getFirst();
        submit(token, quiz, "{\"questionId\": %d, \"optionId\": %d}".formatted(first, quiz.wrongOptionOf(first)));
    }

    private void submit(String token, QuizFixture.Created quiz, String answers) throws Exception {
        mockMvc.perform(post("/quizzes/{quizId}/submissions", quiz.quizId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\": [" + answers + "]}"))
                .andExpect(status().isCreated());
    }

    @Test
    void 유형별_정답률은_푼_적_없는_유형까지_다섯_유형을_블룸_순서로_준다() throws Exception {
        QuizFixture.Created quiz = fixture.createQuiz(userId, 3);
        submitCorrect(token, quiz, List.of(quiz.questionIds().getFirst()));

        getWithToken("/users/me/cognitive-type-scores", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].cognitiveType").value("REMEMBER"))
                .andExpect(jsonPath("$[0].answeredCount").value(3))
                .andExpect(jsonPath("$[0].correctAnswerCount").value(1))
                .andExpect(jsonPath("$[0].accuracy").value(closeTo(1.0 / 3, 0.0001)))
                // 기준(5문제)보다 적게 풀었다
                .andExpect(jsonPath("$[0].isEnoughData").value(false))
                .andExpect(jsonPath("$[1].cognitiveType").value("UNDERSTAND"))
                .andExpect(jsonPath("$[2].cognitiveType").value("APPLY"))
                .andExpect(jsonPath("$[3].cognitiveType").value("ANALYZE"))
                .andExpect(jsonPath("$[4].cognitiveType").value("EVALUATE"))
                .andExpect(jsonPath("$[4].answeredCount").value(0))
                // 규약대로 값이 없어도 필드는 빼지 않고 null 로 보낸다
                .andExpect(jsonPath("$[4].accuracy").value(nullValue()));
    }

    @Test
    void 같은_퀴즈를_다시_풀면_가장_최근_답으로_센다() throws Exception {
        QuizFixture.Created quiz = fixture.createQuiz(userId, 5);
        submitAllWrong(token, quiz);
        submitCorrect(token, quiz, quiz.questionIds());

        getWithToken("/users/me/cognitive-type-scores", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].answeredCount").value(5))
                .andExpect(jsonPath("$[0].correctAnswerCount").value(5))
                .andExpect(jsonPath("$[0].accuracy").value(1.0))
                .andExpect(jsonPath("$[0].isEnoughData").value(true));
    }

    @Test
    void 약한_개념은_개념과_자료와_정답률을_준다() throws Exception {
        QuizFixture.Created quiz = fixture.createQuiz(userId, 3);
        submitCorrect(token, quiz, List.of(quiz.questionIds().getFirst()));

        getWithToken("/users/me/weak-concepts", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].conceptId").isNumber())
                .andExpect(jsonPath("$[0].conceptName").value("TCP"))
                .andExpect(jsonPath("$[0].materialId").isNumber())
                .andExpect(jsonPath("$[0].materialTitle").value("네트워크 5강"))
                .andExpect(jsonPath("$[0].answeredCount").value(3))
                .andExpect(jsonPath("$[0].correctAnswerCount").value(1))
                .andExpect(jsonPath("$[0].accuracy").value(closeTo(1.0 / 3, 0.0001)));
    }

    @Test
    void 기준보다_적게_푼_개념만_있으면_약한_개념은_빈_목록이다() throws Exception {
        QuizFixture.Created quiz = fixture.createQuiz(userId, 2);
        submitAllWrong(token, quiz);

        getWithToken("/users/me/weak-concepts", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 남의_풀이_기록은_내_정답률과_약한_개념에_없다() throws Exception {
        long otherUserId = fixture.createUser("other@test.com");
        String otherToken = jwtProvider.createAccessToken(otherUserId);
        QuizFixture.Created othersQuiz = fixture.createQuiz(otherUserId, 3);
        submitAllWrong(otherToken, othersQuiz);

        getWithToken("/users/me/cognitive-type-scores", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].answeredCount").value(0));
        getWithToken("/users/me/weak-concepts", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 토큰이_없으면_401_이다() throws Exception {
        mockMvc.perform(get("/users/me/cognitive-type-scores"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/users/me/weak-concepts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
