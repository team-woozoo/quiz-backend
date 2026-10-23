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

import java.util.Map;
import java.util.stream.Collectors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 퀴즈 제출로 생긴 오답을 오답 노트에서 다시 풀 때, 오답 노트와 분석과 퀴즈 기록이 어떻게 바뀌는지 API 로만 이어서 본다.
// 퀴즈는 OX 3문제이고 첫 문제만 맞혀, 2·3번이 오답 노트에 들어간 채로 시작한다
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WrongAnswerControllerTest {

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
    void setup() throws Exception {
        fixture = new QuizFixture(userRepository, materialRepository, materialConceptRepository, quizRepository);
        quiz = fixture.createQuiz("note@test.com", 3);
        token = jwtProvider.createAccessToken(quiz.userId());
        q1 = quiz.questionIds().get(0);
        q2 = quiz.questionIds().get(1);
        q3 = quiz.questionIds().get(2);
        postWithToken("/quizzes/" + quiz.quizId() + "/submissions", answers(Map.of(q1, quiz.correctOptionOf(q1))))
                .andExpect(status().isCreated());
    }

    private static String answers(Map<Long, Long> optionByQuestion) {
        return optionByQuestion.entrySet().stream()
                .map(entry -> "{\"questionId\": %d, \"optionId\": %d}".formatted(entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(", ", "{\"answers\": [", "]}"));
    }

    private ResultActions postWithToken(String path, String body) throws Exception {
        return mockMvc.perform(post(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions getWithToken(String path) throws Exception {
        return mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions review(Map<Long, Long> optionByQuestion) throws Exception {
        return postWithToken("/users/me/wrong-answers/submissions", answers(optionByQuestion));
    }

    @Test
    void 퀴즈에서_틀린_문제가_보기와_정답_해설_근거와_함께_오답_노트에_나온다() throws Exception {
        getWithToken("/users/me/wrong-answers")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].questionId").value(q2))
                .andExpect(jsonPath("$[0].quizId").value(quiz.quizId()))
                .andExpect(jsonPath("$[0].format").value("OX"))
                .andExpect(jsonPath("$[0].content").value("문제 2"))
                .andExpect(jsonPath("$[0].cognitiveType").value("REMEMBER"))
                .andExpect(jsonPath("$[0].conceptName").value("TCP"))
                .andExpect(jsonPath("$[0].materialId").isNumber())
                .andExpect(jsonPath("$[0].materialTitle").value("네트워크 5강"))
                .andExpect(jsonPath("$[0].explanation").value("해설"))
                .andExpect(jsonPath("$[0].sourceQuote").value("TCP는 연결 지향 프로토콜이다."))
                .andExpect(jsonPath("$[0].sourcePageNo").value(1))
                .andExpect(jsonPath("$[0].options.length()").value(2))
                .andExpect(jsonPath("$[0].options[0].optionId").value(quiz.correctOptionOf(q2)))
                .andExpect(jsonPath("$[0].options[0].isCorrect").value(true))
                .andExpect(jsonPath("$[0].options[1].isCorrect").value(false))
                .andExpect(jsonPath("$[0].wrongCount").value(1))
                .andExpect(jsonPath("$[0].lastWrongAt").isString())
                .andExpect(jsonPath("$[1].questionId").value(q3));
    }

    // 화면을 넘길 때마다 푼 문제만 보낸다. 보내지 않은 3번은 그대로 남는다
    @Test
    void 복습에서_맞힌_문제는_빠지고_보내지_않은_문제는_그대로다() throws Exception {
        review(Map.of(q2, quiz.correctOptionOf(q2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.submissionId").isNumber())
                .andExpect(jsonPath("$.correctCount").value(1))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.results[0].questionId").value(q2))
                .andExpect(jsonPath("$.results[0].correct").value(true));

        getWithToken("/users/me/wrong-answers")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].questionId").value(q3))
                .andExpect(jsonPath("$[0].wrongCount").value(1));
    }

    @Test
    void 복습에서_또_틀리면_틀린_횟수가_는다() throws Exception {
        review(Map.of(q3, quiz.wrongOptionOf(q3)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.results[0].correct").value(false));

        getWithToken("/users/me/wrong-answers")
                .andExpect(jsonPath("$[1].questionId").value(q3))
                .andExpect(jsonPath("$[1].wrongCount").value(2));
    }

    // 복습 결과는 지금 실력이라 분석에 들어가지만, 그날 퀴즈를 푼 점수는 바뀌지 않는다
    @Test
    void 복습하면_유형별_정답률은_오르고_퀴즈의_푼_횟수와_최근_점수는_그대로다() throws Exception {
        review(Map.of(q2, quiz.correctOptionOf(q2), q3, quiz.correctOptionOf(q3)))
                .andExpect(status().isCreated());

        getWithToken("/users/me/cognitive-type-scores")
                .andExpect(jsonPath("$[0].cognitiveType").value("REMEMBER"))
                .andExpect(jsonPath("$[0].answeredCount").value(3))
                .andExpect(jsonPath("$[0].correctAnswerCount").value(3));
        getWithToken("/quizzes")
                .andExpect(jsonPath("$[0].submissionCount").value(1))
                .andExpect(jsonPath("$[0].lastSubmission.correctCount").value(1));
    }

    @Test
    void 같은_문제를_두_번_보내면_400_INVALID_SUBMISSION_이고_오답_노트는_그대로다() throws Exception {
        postWithToken("/users/me/wrong-answers/submissions", """
                {"answers": [{"questionId": %d, "optionId": %d}, {"questionId": %d, "optionId": %d}]}
                """.formatted(q2, quiz.correctOptionOf(q2), q2, quiz.wrongOptionOf(q2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SUBMISSION"));

        getWithToken("/users/me/wrong-answers")
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void 남의_문제를_보내면_400_INVALID_SUBMISSION_이다() throws Exception {
        QuizFixture.Created others = fixture.createQuiz("others@test.com", 1);
        long othersQuestion = others.questionIds().getFirst();

        review(Map.of(othersQuestion, others.correctOptionOf(othersQuestion)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SUBMISSION"));
    }

    @Test
    void 답이_없으면_400_INVALID_INPUT_이다() throws Exception {
        postWithToken("/users/me/wrong-answers/submissions", "{\"answers\": []}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 토큰이_없으면_401_이다() throws Exception {
        mockMvc.perform(get("/users/me/wrong-answers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
