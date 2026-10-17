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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 보안 설정, 요청 검증, 채점, 저장을 거쳐 앱이 받는 제출 결과와 오류 코드를 확인한다
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SubmissionControllerTest {

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
    QuizFixture.Created quiz;
    String token;
    long q1;
    long q2;
    long q3;

    @BeforeEach
    void setup() {
        fixture = new QuizFixture(userRepository, materialRepository, materialConceptRepository, quizRepository);
        quiz = fixture.createQuiz("submit@test.com", 3);
        token = jwtProvider.createAccessToken(quiz.userId());
        q1 = quiz.questionIds().get(0);
        q2 = quiz.questionIds().get(1);
        q3 = quiz.questionIds().get(2);
    }

    private ResultActions submit(String token, long quizId, String body) throws Exception {
        return mockMvc.perform(post("/quizzes/{quizId}/submissions", quizId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String answers(String... answers) {
        return "{\"answers\": [" + String.join(",", answers) + "]}";
    }

    private String answer(long questionId, long optionId) {
        return "{\"questionId\": %d, \"optionId\": %d}".formatted(questionId, optionId);
    }

    @Test
    void 제출하면_201_과_점수와_문제별_결과를_문제_순서대로_받는다() throws Exception {
        // 문제 3 은 보내지 않아 안 푼 것으로 채점된다
        submit(token, quiz.quizId(), answers(
                answer(q2, quiz.wrongOptionOf(q2)),
                answer(q1, quiz.correctOptionOf(q1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.submissionId").isNumber())
                .andExpect(jsonPath("$.correctCount").value(1))
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.results.length()").value(3))
                .andExpect(jsonPath("$.results[0].questionId").value(q1))
                .andExpect(jsonPath("$.results[0].correct").value(true))
                .andExpect(jsonPath("$.results[1].questionId").value(q2))
                .andExpect(jsonPath("$.results[1].correct").value(false))
                .andExpect(jsonPath("$.results[2].questionId").value(q3))
                .andExpect(jsonPath("$.results[2].correct").value(false));
    }

    @Test
    void 답이_하나도_없으면_400_INVALID_INPUT_이다() throws Exception {
        submit(token, quiz.quizId(), answers())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 답의_필드가_빠지면_400_INVALID_INPUT_이다() throws Exception {
        submit(token, quiz.quizId(), "{\"answers\": [{\"questionId\": %d}]}".formatted(q1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        submit(token, quiz.quizId(), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 다른_문제의_보기를_보내면_400_INVALID_SUBMISSION_이다() throws Exception {
        submit(token, quiz.quizId(), answers(answer(q1, quiz.correctOptionOf(q2))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SUBMISSION"));
    }

    @Test
    void 같은_문제를_두_번_보내면_400_INVALID_SUBMISSION_이다() throws Exception {
        submit(token, quiz.quizId(), answers(
                answer(q1, quiz.correctOptionOf(q1)),
                answer(q1, quiz.wrongOptionOf(q1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SUBMISSION"));
    }

    @Test
    void 남의_퀴즈에_제출하면_404_QUIZ_NOT_FOUND_다() throws Exception {
        String otherToken = jwtProvider.createAccessToken(fixture.createUser("other@test.com"));

        submit(otherToken, quiz.quizId(), answers(answer(q1, quiz.correctOptionOf(q1))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUIZ_NOT_FOUND"));
    }

    @Test
    void 토큰이_없으면_401_이다() throws Exception {
        mockMvc.perform(post("/quizzes/{quizId}/submissions", quiz.quizId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answers(answer(q1, quiz.correctOptionOf(q1)))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
