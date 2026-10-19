package com.woozoo.quiz.quiz.application;

import com.woozoo.quiz.quiz.domain.CognitiveType;
import com.woozoo.quiz.quiz.domain.Difficulty;
import com.woozoo.quiz.quiz.domain.QuizFormat;
import com.woozoo.quiz.quiz.port.GeneratedConcept;
import com.woozoo.quiz.quiz.port.GeneratedOption;
import com.woozoo.quiz.quiz.port.GeneratedQuestion;
import com.woozoo.quiz.quiz.port.GeneratedQuiz;
import com.woozoo.quiz.quiz.port.QuizGenerationRequest;
import com.woozoo.quiz.quiz.port.SourceChunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 규칙마다 "그 규칙만 어긴 문제"를 정상 문제와 함께 넣고, 정상 문제만 남는지 본다
class GeneratedQuizValidatorTest {

    private static final String TCP_QUOTE = "TCP는 연결 지향 프로토콜이다.";
    private static final String UDP_QUOTE = "UDP는 비연결형 프로토콜이다.";

    private static final List<SourceChunk> CHUNKS = List.of(
            new SourceChunk(1, TCP_QUOTE + "\n신뢰성 있는 전송을 보장한다."),
            new SourceChunk(2, UDP_QUOTE + "\n순서를 보장하지 않는다."));

    private final GeneratedQuizValidator validator = new GeneratedQuizValidator();

    private static QuizGenerationRequest request(QuizFormat format, int questionCount) {
        return new QuizGenerationRequest(CHUNKS, format, Difficulty.NORMAL, questionCount, List.of());
    }

    private static List<GeneratedOption> oxOptions() {
        return List.of(new GeneratedOption("O", true), new GeneratedOption("X", false));
    }

    private static GeneratedQuestion question(String content, String quote, int seq, List<GeneratedOption> options) {
        return new GeneratedQuestion(content, quote, CognitiveType.REMEMBER, "해설", options, seq);
    }

    // 1번 청크에 근거한, 모든 규칙을 지키는 OX 문제
    private static GeneratedQuestion validOx(String content) {
        return question(content, TCP_QUOTE, 1, oxOptions());
    }

    private static GeneratedConcept concept(String name, List<Integer> chunkSeqs, GeneratedQuestion... questions) {
        return new GeneratedConcept(name, "설명", chunkSeqs, Arrays.asList(questions));
    }

    private static List<String> contentsOf(GeneratedQuiz quiz) {
        return quiz.concepts().stream()
                .flatMap(concept -> concept.questions().stream())
                .map(GeneratedQuestion::content)
                .toList();
    }

    private GeneratedQuiz validateOx(GeneratedConcept... concepts) {
        return validator.validate(request(QuizFormat.OX, 10), new GeneratedQuiz(List.of(concepts)));
    }

    @Test
    void 규칙을_모두_지킨_문제는_그대로_남는다() {
        GeneratedQuestion valid = validOx("정상");

        GeneratedQuiz result = validateOx(concept("TCP", List.of(1), valid));

        assertThat(result.concepts()).singleElement().satisfies(concept -> {
            assertThat(concept.name()).isEqualTo("TCP");
            assertThat(concept.chunkSeqs()).containsExactly(1);
            assertThat(concept.questions()).containsExactly(valid);
        });
    }

    @Test
    void 보기_수가_형식과_다르면_버린다() {
        GeneratedQuestion fourOptions = question("보기 4개", TCP_QUOTE, 1, List.of(
                new GeneratedOption("O", true), new GeneratedOption("X", false),
                new GeneratedOption("A", false), new GeneratedOption("B", false)));

        GeneratedQuiz result = validateOx(concept("TCP", List.of(1), validOx("정상"), fourOptions));

        assertThat(contentsOf(result)).containsExactly("정상");
    }

    @Test
    void 보기가_null_이면_버린다() {
        GeneratedQuestion nullOptions = question("보기 없음", TCP_QUOTE, 1, null);

        GeneratedQuiz result = validateOx(concept("TCP", List.of(1), validOx("정상"), nullOptions));

        assertThat(contentsOf(result)).containsExactly("정상");
    }

    @Test
    void 정답이_없거나_둘이면_버린다() {
        GeneratedQuestion noAnswer = question("정답 없음", TCP_QUOTE, 1,
                List.of(new GeneratedOption("O", false), new GeneratedOption("X", false)));
        GeneratedQuestion twoAnswers = question("정답 둘", TCP_QUOTE, 1,
                List.of(new GeneratedOption("O", true), new GeneratedOption("X", true)));

        GeneratedQuiz result = validateOx(concept("TCP", List.of(1), validOx("정상"), noAnswer, twoAnswers));

        assertThat(contentsOf(result)).containsExactly("정상");
    }

    @Test
    void 보기_내용이_겹치면_버린다() {
        GeneratedQuestion sameOptions = question("보기 중복", TCP_QUOTE, 1,
                List.of(new GeneratedOption("O", true), new GeneratedOption("O", false)));

        GeneratedQuiz result = validateOx(concept("TCP", List.of(1), validOx("정상"), sameOptions));

        assertThat(contentsOf(result)).containsExactly("정상");
    }

    @Test
    void 필수_값이_비어_있으면_버린다() {
        GeneratedQuestion blankContent = question("   ", TCP_QUOTE, 1, oxOptions());
        GeneratedQuestion nullExplanation =
                new GeneratedQuestion("해설 없음", TCP_QUOTE, CognitiveType.REMEMBER, null, oxOptions(), 1);
        GeneratedQuestion nullCognitiveType =
                new GeneratedQuestion("유형 없음", TCP_QUOTE, null, "해설", oxOptions(), 1);
        GeneratedQuestion nullQuote = question("인용문 없음", null, 1, oxOptions());
        GeneratedQuestion blankOption = question("빈 보기", TCP_QUOTE, 1,
                List.of(new GeneratedOption("O", true), new GeneratedOption(" ", false)));
        GeneratedQuestion nullOption = question("null 보기", TCP_QUOTE, 1,
                Arrays.asList(new GeneratedOption("O", true), null));

        GeneratedQuiz result = validateOx(concept("TCP", List.of(1), validOx("정상"),
                blankContent, nullExplanation, nullCognitiveType, nullQuote, blankOption, nullOption));

        assertThat(contentsOf(result)).containsExactly("정상");
    }

    @Test
    void null_문제는_건너뛴다() {
        GeneratedQuiz result = validateOx(concept("TCP", List.of(1), validOx("정상"), null));

        assertThat(contentsOf(result)).containsExactly("정상");
    }

    @Test
    void 근거_청크가_개념의_청크에_없으면_버린다() {
        // 2번 청크는 요청에 있지만 이 개념의 청크가 아니다
        GeneratedQuestion otherConceptChunk = question("다른 개념 청크", UDP_QUOTE, 2, oxOptions());

        GeneratedQuiz result = validateOx(concept("TCP", List.of(1), validOx("정상"), otherConceptChunk));

        assertThat(contentsOf(result)).containsExactly("정상");
    }

    @Test
    void 인용문이_원문에_없으면_버린다() {
        GeneratedQuestion madeUpQuote = question("지어낸 인용문", "TCP는 비연결형이다.", 1, oxOptions());

        GeneratedQuiz result = validateOx(concept("TCP", List.of(1), validOx("정상"), madeUpQuote));

        assertThat(contentsOf(result)).containsExactly("정상");
    }

    // PDF 원문은 문장 중간에 줄바꿈이 있고, AI 는 그걸 공백으로 바꿔 인용하곤 한다
    @Test
    void 줄바꿈과_공백만_다른_인용문은_통과한다() {
        GeneratedQuestion acrossLines = question("줄을 넘는 인용문", "프로토콜이다.   신뢰성 있는 전송을", 1, oxOptions());

        GeneratedQuiz result = validateOx(concept("TCP", List.of(1), acrossLines));

        assertThat(contentsOf(result)).containsExactly("줄을 넘는 인용문");
    }

    @Test
    void 요청하지_않은_청크_번호는_개념에서_뺀다() {
        GeneratedQuiz result = validateOx(concept("TCP", List.of(1, 99), validOx("정상")));

        assertThat(result.concepts()).singleElement()
                .extracting(GeneratedConcept::chunkSeqs)
                .isEqualTo(List.of(1));
    }

    @Test
    void 요청한_청크가_하나도_없는_개념은_버린다() {
        GeneratedQuestion unknownChunk = question("없는 청크", TCP_QUOTE, 99, oxOptions());

        GeneratedQuiz result = validateOx(concept("없는 청크 개념", List.of(99), unknownChunk));

        assertThat(result.concepts()).isEmpty();
    }

    @Test
    void 이름이_같은_개념은_청크와_문제를_합친다() {
        GeneratedQuestion tcp = validOx("앞쪽 문제");
        GeneratedQuestion udp = question("뒤쪽 문제", UDP_QUOTE, 2, oxOptions());

        GeneratedQuiz result = validateOx(
                concept("전송 계층", List.of(1), tcp),
                concept(" 전송 계층 ", List.of(1, 2), udp));

        assertThat(result.concepts()).singleElement().satisfies(concept -> {
            assertThat(concept.name()).isEqualTo("전송 계층");
            assertThat(concept.chunkSeqs()).containsExactly(1, 2);
            assertThat(concept.questions()).containsExactly(tcp, udp);
        });
    }

    @Test
    void 한_개념_안에서_겹친_청크_번호는_하나만_남긴다() {
        GeneratedQuiz result = validateOx(concept("TCP", List.of(1, 1), validOx("정상")));

        assertThat(result.concepts()).singleElement()
                .extracting(GeneratedConcept::chunkSeqs)
                .isEqualTo(List.of(1));
    }

    @Test
    void 이름이나_설명이_비었거나_이름이_255자를_넘으면_개념째_버린다() {
        GeneratedConcept blankName = concept("  ", List.of(1), validOx("이름 없음"));
        GeneratedConcept longName = concept("가".repeat(256), List.of(1), validOx("긴 이름"));
        GeneratedConcept nullDescription = new GeneratedConcept("설명 없음", null, List.of(1), List.of(validOx("설명 없음")));
        GeneratedConcept valid = concept("가".repeat(255), List.of(1), validOx("정상"));

        GeneratedQuiz result = validateOx(blankName, longName, nullDescription, valid);

        assertThat(contentsOf(result)).containsExactly("정상");
    }

    @Test
    void 통과한_문제가_없는_개념은_버린다() {
        GeneratedQuestion madeUpQuote = question("지어낸 인용문", "강의에 없는 문장", 1, oxOptions());

        GeneratedQuiz result = validateOx(
                concept("TCP", List.of(1), madeUpQuote),
                concept("UDP", List.of(2), question("정상", UDP_QUOTE, 2, oxOptions())));

        assertThat(result.concepts()).extracting(GeneratedConcept::name).containsExactly("UDP");
    }

    @Test
    void 요청한_문제_수까지만_개념을_넘어서_남긴다() {
        GeneratedQuiz generated = new GeneratedQuiz(List.of(
                concept("TCP", List.of(1), validOx("1"), validOx("2")),
                concept("UDP", List.of(2),
                        question("3", UDP_QUOTE, 2, oxOptions()),
                        question("4", UDP_QUOTE, 2, oxOptions())),
                concept("IP", List.of(1), validOx("5"))));

        GeneratedQuiz result = validator.validate(request(QuizFormat.OX, 3), generated);

        assertThat(contentsOf(result)).containsExactly("1", "2", "3");
        assertThat(result.concepts()).extracting(GeneratedConcept::name).containsExactly("TCP", "UDP");
    }

    // 잘못된 문제를 먼저 세면, 뒤의 정상 문제가 자리를 잃는다
    @Test
    void 버린_문제는_문제_수에_세지_않는다() {
        GeneratedQuestion madeUpQuote = question("지어낸 인용문", "강의에 없는 문장", 1, oxOptions());

        GeneratedQuiz generated = new GeneratedQuiz(List.of(
                concept("TCP", List.of(1), madeUpQuote, validOx("1"), validOx("2"))));

        GeneratedQuiz result = validator.validate(request(QuizFormat.OX, 2), generated);

        assertThat(contentsOf(result)).containsExactly("1", "2");
    }

    @Test
    void 객관식은_보기_4개를_요구한다() {
        List<GeneratedOption> fourOptions = List.of(
                new GeneratedOption(TCP_QUOTE, true), new GeneratedOption("A", false),
                new GeneratedOption("B", false), new GeneratedOption("C", false));
        GeneratedQuestion multipleChoice = question("객관식", TCP_QUOTE, 1, fourOptions);
        GeneratedQuestion ox = validOx("OX");

        GeneratedQuiz result = validator.validate(request(QuizFormat.MULTIPLE_CHOICE, 10),
                new GeneratedQuiz(List.of(concept("TCP", List.of(1), multipleChoice, ox))));

        assertThat(contentsOf(result)).containsExactly("객관식");
    }

    @Test
    void 개념_목록이_null_이면_빈_결과를_돌려준다() {
        GeneratedQuiz result = validator.validate(request(QuizFormat.OX, 10), new GeneratedQuiz(null));

        assertThat(result.concepts()).isEmpty();
    }

    // 프롬프트를 고칠 때 볼 로그다. 이유는 처음 걸린 규칙 하나로 센다
    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void 버린_문제는_이유별_개수를_로그로_남긴다(CapturedOutput output) {
        GeneratedQuestion wrongQuote = question("인용 다름", "TCP는 연결을 맺는다.", 1, oxOptions());
        GeneratedQuestion otherWrongQuote = question("인용 다름 2", "강의에 없는 문장", 1, oxOptions());
        GeneratedQuestion noAnswer = question("정답 없음", TCP_QUOTE, 1,
                List.of(new GeneratedOption("O", false), new GeneratedOption("X", false)));

        validateOx(concept("TCP", List.of(1), validOx("정상"), wrongQuote, otherWrongQuote, noAnswer),
                concept("모르는 청크", List.of(99), validOx("청크 없음")));

        assertThat(output).contains("문제 4개를 버렸다")
                .contains("NO_KNOWN_CHUNK=1")
                .contains("ANSWER_COUNT=1")
                .contains("QUOTE_NOT_IN_SOURCE=2");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void 버린_문제가_없으면_로그를_남기지_않는다(CapturedOutput output) {
        validateOx(concept("TCP", List.of(1), validOx("정상")));

        assertThat(output).doesNotContain("버렸다");
    }
}
