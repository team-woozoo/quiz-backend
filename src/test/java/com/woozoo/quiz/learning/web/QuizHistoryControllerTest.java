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

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 제출 API 로 푼 기록이 내 퀴즈 목록에 어떻게 나오는지 API 로만 이어서 본다
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class QuizHistoryControllerTest {

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
        userId = fixture.createUser("history@test.com");
        token = jwtProvider.createAccessToken(userId);
    }

    private ResultActions getMyQuizzes(String token) throws Exception {
        return mockMvc.perform(get("/quizzes").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    // 첫 문제만 맞히고 나머지는 보내지 않는다
    private void submitFirstCorrect(QuizFixture.Created quiz) throws Exception {
        long q1 = quiz.questionIds().getFirst();
        mockMvc.perform(post("/quizzes/{quizId}/submissions", quiz.quizId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\": [{\"questionId\": %d, \"optionId\": %d}]}"
                                .formatted(q1, quiz.correctOptionOf(q1))))
                .andExpect(status().isCreated());
    }

    @Test
    void 안_푼_퀴즈는_최근_제출이_null_로_온다() throws Exception {
        QuizFixture.Created quiz = fixture.createQuiz(userId, 3);

        getMyQuizzes(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].quizId").value(quiz.quizId()))
                .andExpect(jsonPath("$[0].materialTitle").value("네트워크 5강"))
                .andExpect(jsonPath("$[0].format").value("OX"))
                .andExpect(jsonPath("$[0].difficulty").value("NORMAL"))
                .andExpect(jsonPath("$[0].questionCount").value(3))
                .andExpect(jsonPath("$[0].createdAt").isString())
                .andExpect(jsonPath("$[0].submissionCount").value(0))
                // 규약대로 값이 없어도 필드는 빼지 않고 null 로 보낸다
                .andExpect(jsonPath("$[0].lastSubmission").value(nullValue()));
    }

    @Test
    void 제출하면_푼_횟수와_가장_최근_결과가_목록에_나온다() throws Exception {
        QuizFixture.Created quiz = fixture.createQuiz(userId, 3);

        submitFirstCorrect(quiz);
        submitFirstCorrect(quiz);

        getMyQuizzes(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].submissionCount").value(2))
                .andExpect(jsonPath("$[0].lastSubmission.correctCount").value(1))
                .andExpect(jsonPath("$[0].lastSubmission.submittedAt").isString());
    }

    @Test
    void 최근에_만든_퀴즈부터_오고_남의_퀴즈는_없다() throws Exception {
        QuizFixture.Created older = fixture.createQuiz(userId, 1);
        QuizFixture.Created newer = fixture.createQuiz(userId, 2);
        fixture.createQuiz("other@test.com", 1);

        getMyQuizzes(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].quizId").value(newer.quizId()))
                .andExpect(jsonPath("$[1].quizId").value(older.quizId()));
    }

    @Test
    void 토큰이_없으면_401_이다() throws Exception {
        mockMvc.perform(get("/quizzes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
