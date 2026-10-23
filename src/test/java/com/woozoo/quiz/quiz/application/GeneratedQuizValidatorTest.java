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
import java.util.Map;

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
        return new QuizGenerationRequest(CHUNKS, format, Difficulty.NORMAL, questionCount, Map.of(), List.of());
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

    private static GeneratedQuestion typedOx(String content, CognitiveType type) {
        return new GeneratedQuestion(content, TCP_QUOTE, type, "해설", oxOptions(), 1);
    }

    private static QuizGenerationRequest mixRequest(int questionCount, Map<CognitiveType, Integer> mix) {
        return new QuizGenerationRequest(CHUNKS, QuizFormat.OX, Difficulty.NORMAL, questionCount, mix, List.of());
    }

    // 개념 순서대로 자르면 자료 뒤쪽 개념의 평가 문제가 잘린다
    @Test
    void 넘치면_목표보다_많이_나온_유형에서_뒤쪽부터_뺀다() {
        GeneratedQuiz generated = new GeneratedQuiz(List.of(
                concept("앞쪽 개념", List.of(1),
                        typedOx("기억", CognitiveType.REMEMBER),
                        typedOx("이해 1", CognitiveType.UNDERSTAND),
                        typedOx("이해 2", CognitiveType.UNDERSTAND)),
                concept("뒤쪽 개념", List.of(1), typedOx("평가", CognitiveType.EVALUATE))));
        Map<CognitiveType, Integer> mix = Map.of(
                CognitiveType.REMEMBER, 1, CognitiveType.UNDERSTAND, 1, CognitiveType.EVALUATE, 1);

        GeneratedQuiz result = validator.validate(mixRequest(3, mix), generated);

        assertThat(contentsOf(result)).containsExactly("기억", "이해 1", "평가");
    }

    @Test
    void 목표에_없는_유형부터_뺀다() {
        GeneratedQuiz generated = new GeneratedQuiz(List.of(
                concept("TCP", List.of(1),
                        typedOx("분석", CognitiveType.ANALYZE),
                        typedOx("기억 1", CognitiveType.REMEMBER),
                        typedOx("기억 2", CognitiveType.REMEMBER))));

        GeneratedQuiz result = validator.validate(mixRequest(2, Map.of(CognitiveType.REMEMBER, 2)), generated);

        assertThat(contentsOf(result)).containsExactly("기억 1", "기억 2");
    }

    // 뺀 문제는 검증에서 떨어진 것이 아니라서 버린 문제와 따로 센다
    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void 넘쳐서_뺀_문제는_유형별로_따로_로그에_남긴다(CapturedOutput output) {
        GeneratedQuiz generated = new GeneratedQuiz(List.of(
                concept("TCP", List.of(1),
                        typedOx("기억", CognitiveType.REMEMBER),
                        typedOx("이해 1", CognitiveType.UNDERSTAND),
                        typedOx("이해 2", CognitiveType.UNDERSTAND))));
        Map<CognitiveType, Integer> mix = Map.of(CognitiveType.REMEMBER, 1, CognitiveType.UNDERSTAND, 1);

        validator.validate(mixRequest(2, mix), generated);

        assertThat(output).contains("받은 문제 3개, 남긴 문제 2개, 버린 문제 0개")
                .contains("넘쳐서 뺀 문제 1개 {UNDERSTAND=1}");
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

        // 받은 5개 중 정상 1개만 남는다
        assertThat(output).contains("받은 문제 5개, 남긴 문제 1개, 버린 문제 4개")
                .contains("NO_KNOWN_CHUNK=1")
                .contains("ANSWER_COUNT=1")
                .contains("QUOTE_NOT_IN_SOURCE=2")
                .contains("버린 유형 {REMEMBER=4}");
    }

    // 10개를 요청했는데 1개만 남았을 때, 버린 것 없이 받은 것도 1개면 AI 가 적게 만든 것이다
    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void 버린_문제가_없어도_받은_수와_남긴_수를_로그로_남긴다(CapturedOutput output) {
        validateOx(concept("TCP", List.of(1), validOx("정상")));

        assertThat(output).contains("받은 문제 1개, 남긴 문제 1개, 버린 문제 0개");
    }

    // 슬라이드에서 뽑은 원문 모양 그대로다. 목록, 단어 중간에 끼인 공백, 머리글과 데이터 행이 따로 나온 표가 있다
    private static final SourceChunk SLIDE = new SourceChunk(3, """
            엑셀 문서 구성

            • 통합문서(W orkbook)
            • 하나의 엑셀 문서(엑셀 파일)

            • 셀(Cell)
            • 엑셀에서 데이터가 기록되는 최소 저장 단위

            버전 97 ~ 2007 2007 ~ 현재(OOXML)

            Excel HSSF(Horrible Spread Sheet Format) XSSF(XML Spread Sheet Format)""");

    private List<String> validateSlideQuotes(String... quotes) {
        GeneratedQuestion[] questions = Arrays.stream(quotes)
                .map(quote -> question(quote, quote, 3, oxOptions()))
                .toArray(GeneratedQuestion[]::new);
        QuizGenerationRequest slideRequest =
                new QuizGenerationRequest(List.of(SLIDE), QuizFormat.OX, Difficulty.NORMAL, 10, Map.of(), List.of());
        return contentsOf(validator.validate(slideRequest,
                new GeneratedQuiz(List.of(concept("엑셀", List.of(3), questions)))));
    }

    // AI 는 목록의 제목 줄과 설명 줄을 이으며 글머리 기호를 다른 기호로 바꾸곤 한다. 글자는 원문 그대로라 통과시킨다
    @Test
    void 목록의_두_줄을_기호로_이은_인용문은_통과한다() {
        assertThat(validateSlideQuotes("셀(Cell) - 엑셀에서 데이터가 기록되는 최소 저장 단위"))
                .containsExactly("셀(Cell) - 엑셀에서 데이터가 기록되는 최소 저장 단위");
    }

    // PDF 추출이 단어 중간에 넣은 공백은 원문에만 있고, AI 는 붙여 쓴다
    @Test
    void 추출이_단어_중간에_넣은_공백과_달라도_통과한다() {
        assertThat(validateSlideQuotes("통합문서(Workbook)")).containsExactly("통합문서(Workbook)");
    }

    // 전각 문자처럼 모양은 같고 코드만 다른 글자는 같은 글자로 본다
    @Test
    void 전각_문자로_쓴_인용문도_통과한다() {
        assertThat(validateSlideQuotes("통합문서（Ｗｏｒｋｂｏｏｋ）")).containsExactly("통합문서（Ｗｏｒｋｂｏｏｋ）");
    }

    // 표의 머리글과 데이터 행에서 칸을 골라 엮으면 원문에 이어진 글자가 없다. 내용이 맞아도 근거로 보여 줄 수 없어 버린다
    @Test
    void 표의_칸을_골라_엮은_인용문은_버린다() {
        assertThat(validateSlideQuotes("Excel 2007 ~ 현재(OOXML) XSSF(XML Spread Sheet Format)")).isEmpty();
    }

    // 기호만 남기면 비교할 글자가 없어 어느 원문에나 들어 있다고 나온다
    @Test
    void 글자_없이_기호만_있는_인용문은_버린다() {
        assertThat(validateSlideQuotes("• - ...")).isEmpty();
    }

    @Test
    void 기호를_무시해도_원문에_없는_글자가_있으면_버린다() {
        assertThat(validateSlideQuotes("셀(Cell) - 엑셀에서 데이터를 저장하는 가장 작은 단위")).isEmpty();
    }

    // 비교 문제는 같은 페이지의 떨어진 두 곳이 근거라, 모델이 사이를 건너뛰고 " / " 로 잇는다
    @Test
    void 떨어진_곳을_슬래시로_이은_인용은_조각마다_원문에_있으면_통과한다() {
        assertThat(validateSlideQuotes("통합문서(W orkbook) / 엑셀에서 데이터가 기록되는 최소 저장 단위"))
                .containsExactly("통합문서(W orkbook) / 엑셀에서 데이터가 기록되는 최소 저장 단위");
    }

    @Test
    void 슬래시로_이은_조각_하나라도_원문에_없으면_버린다() {
        assertThat(validateSlideQuotes("통합문서(Workbook) / 엑셀에서 데이터를 저장하는 가장 작은 단위")).isEmpty();
    }

    // 줄 사이를 건너뛰는 것과 같은 규칙이라 " / " 조각 수는 제한하지 않는다
    @Test
    void 슬래시로_이은_조각이_많아도_원문_순서대로_있으면_통과한다() {
        String quote = "통합문서 / 엑셀 문서 / 셀(Cell) / 최소 저장 단위";

        assertThat(validateSlideQuotes(quote)).containsExactly(quote);
    }

    // 원문에 있는 조각들이라도 순서를 바꿔 이으면 원문과 다른 근거가 된다
    @Test
    void 슬래시로_이은_조각의_순서가_원문과_다르면_버린다() {
        assertThat(validateSlideQuotes("• 셀(Cell) / • 통합문서(W orkbook)")).isEmpty();
    }

    @Test
    void 슬래시로_이은_조각_하나가_기호뿐이면_버린다() {
        assertThat(validateSlideQuotes("• 셀(Cell) / ...")).isEmpty();
    }

    // 모델은 " / " 대신 원문의 덩어리 사이에 있던 빈 줄을 남기고 그 사이 줄들을 건너뛰곤 한다
    @Test
    void 빈_줄로_건너뛴_인용은_덩어리마다_원문에_있으면_통과한다() {
        String skipped = "• 통합문서(W orkbook)\n\n• 엑셀에서 데이터가 기록되는 최소 저장 단위";
        String spacedBlankLine = "• 통합문서(W orkbook)\n   \n• 셀(Cell)";

        assertThat(validateSlideQuotes(skipped, spacedBlankLine)).containsExactly(skipped, spacedBlankLine);
    }

    @Test
    void 빈_줄로_나눈_덩어리_하나라도_원문에_없으면_버린다() {
        assertThat(validateSlideQuotes("• 통합문서(W orkbook)\n\n• 엑셀에서 데이터를 저장하는 가장 작은 단위")).isEmpty();
    }

    // 원문에 빈 줄이 많은 곳을 이어서 인용해도 페이지를 잘라 붙인 것이 아니므로, 개수 제한은 " / " 만 센다
    @Test
    void 빈_줄로_나뉜_덩어리는_조각_개수_제한에_세지_않는다() {
        assertThat(validateSlideQuotes(SLIDE.content())).containsExactly(SLIDE.content());
    }

    // 앞뒤 빈 줄을 그대로 나누면 빈 조각이 생겨 기호만 있는 인용처럼 버려진다
    @Test
    void 앞뒤에_빈_줄이_붙은_인용도_통과한다() {
        String quote = "\n\n• 셀(Cell)\n• 엑셀에서 데이터가 기록되는 최소 저장 단위\n\n";

        assertThat(validateSlideQuotes(quote)).containsExactly(quote);
    }

    // 모델은 건너뛴 자리에 "..." 줄을 넣기도 한다
    @Test
    void 말줄임표_줄로_건너뛴_인용은_줄마다_원문에_있으면_통과한다() {
        String quote = "• 통합문서(W orkbook)\n...\n• 엑셀에서 데이터가 기록되는 최소 저장 단위";

        assertThat(validateSlideQuotes(quote)).containsExactly(quote);
    }

    // 표시 없이 줄만 바꿔 사이 줄을 빼기도 한다. 줄마다 원문 그대로라 근거로 보여 줄 수 있다
    @Test
    void 표시_없이_줄을_건너뛴_인용도_통과한다() {
        String quote = "• 통합문서(W orkbook)\n• 셀(Cell)";

        assertThat(validateSlideQuotes(quote)).containsExactly(quote);
    }

    // 원문에 있는 줄들이라도 순서를 바꾸면 원문과 다른 근거가 된다
    @Test
    void 줄_순서를_바꾼_인용은_버린다() {
        assertThat(validateSlideQuotes("• 셀(Cell)\n• 통합문서(W orkbook)")).isEmpty();
    }

    // 한 줄 안에서는 어디를 건너뛰었는지 알 수 없어, 고쳐 쓴 문장과 구별하지 못하고 버린다
    @Test
    void 한_줄_안에서_건너뛴_인용은_버린다() {
        assertThat(validateSlideQuotes("• 통합문서(W orkbook) • 셀(Cell)")).isEmpty();
    }

    // 원문의 URL 이나 경로처럼 공백 없이 붙은 / 는 조각을 나누지 않는다
    @Test
    void 공백_없이_붙은_슬래시는_조각을_나누지_않는다() {
        SourceChunk path = new SourceChunk(4, "저장 경로는 C:/data/report.xlsx 이다");
        QuizGenerationRequest pathRequest =
                new QuizGenerationRequest(List.of(path), QuizFormat.OX, Difficulty.NORMAL, 10, Map.of(), List.of());
        GeneratedQuestion pathQuote = question("경로", "C:/data/report.xlsx", 4, oxOptions());

        assertThat(contentsOf(validator.validate(pathRequest,
                new GeneratedQuiz(List.of(concept("경로", List.of(4), pathQuote)))))).containsExactly("경로");
    }
}
