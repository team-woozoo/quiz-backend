package com.woozoo.quiz.infrastructure.generation;

import com.woozoo.quiz.quiz.domain.Difficulty;
import com.woozoo.quiz.quiz.domain.QuizFormat;
import com.woozoo.quiz.quiz.port.GeneratedConcept;
import com.woozoo.quiz.quiz.port.GeneratedOption;
import com.woozoo.quiz.quiz.port.GeneratedQuestion;
import com.woozoo.quiz.quiz.port.GeneratedQuiz;
import com.woozoo.quiz.quiz.port.QuizGenerationRequest;
import com.woozoo.quiz.quiz.port.SourceChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// 가짜 생성기의 결과가 서비스 검증 규칙(보기 수, 정답 하나, 근거가 원문에 있음)을 늘 지키는지 본다
class FakeQuizGenerationAdapterTest {

    private static final List<SourceChunk> CHUNKS = List.of(
            new SourceChunk(1, "TCP는 연결 지향 프로토콜이다.\n신뢰성 있는 전송을 보장한다."),
            new SourceChunk(2, "UDP는 비연결형 프로토콜이다.\n순서를 보장하지 않는다."),
            new SourceChunk(3, "IP는 패킷을 목적지까지 전달한다.\n경로를 고른다."),
            new SourceChunk(4, "포트 번호로 프로세스를 구분한다.\n0~65535 범위를 쓴다."));

    private final FakeQuizGenerationAdapter adapter = new FakeQuizGenerationAdapter();

    private static QuizGenerationRequest request(QuizFormat format, int questionCount, List<SourceChunk> chunks) {
        return new QuizGenerationRequest(chunks, format, Difficulty.NORMAL, questionCount, List.of());
    }

    private static List<GeneratedQuestion> questionsOf(GeneratedQuiz quiz) {
        return quiz.concepts().stream().flatMap(concept -> concept.questions().stream()).toList();
    }

    private static Map<Integer, String> contentBySeq(List<SourceChunk> chunks) {
        return chunks.stream().collect(Collectors.toMap(SourceChunk::seq, SourceChunk::content));
    }

    private static void assertGroundedInChunks(GeneratedQuiz quiz, List<SourceChunk> chunks) {
        Map<Integer, String> contents = contentBySeq(chunks);
        for (GeneratedConcept concept : quiz.concepts()) {
            for (GeneratedQuestion question : concept.questions()) {
                assertThat(concept.chunkSeqs()).contains(question.sourceChunkSeq());
                assertThat(contents.get(question.sourceChunkSeq())).contains(question.sourceQuote());
            }
        }
    }

    @Test
    void OX_문제는_O_X_두_보기에_정답이_O_하나이고_근거가_청크_원문에_있다() {
        GeneratedQuiz quiz = adapter.generateQuiz(request(QuizFormat.OX, 10, CHUNKS));

        assertThat(questionsOf(quiz)).allSatisfy(question -> {
            assertThat(question.options()).extracting(GeneratedOption::content).containsExactly("O", "X");
            assertThat(question.options()).filteredOn(GeneratedOption::isCorrect)
                    .extracting(GeneratedOption::content).containsExactly("O");
        });
        assertGroundedInChunks(quiz, CHUNKS);
    }

    @Test
    void 객관식_문제는_서로_다른_보기_4개에_정답이_근거_문장_하나다() {
        GeneratedQuiz quiz = adapter.generateQuiz(request(QuizFormat.MULTIPLE_CHOICE, 10, CHUNKS));

        assertThat(questionsOf(quiz)).allSatisfy(question -> {
            assertThat(question.options()).hasSize(4);
            assertThat(question.options()).extracting(GeneratedOption::content).doesNotHaveDuplicates();
            assertThat(question.options()).filteredOn(GeneratedOption::isCorrect)
                    .extracting(GeneratedOption::content).containsExactly(question.sourceQuote());
        });
        assertGroundedInChunks(quiz, CHUNKS);
    }

    @Test
    void 인용문은_청크의_첫_줄이다() {
        GeneratedQuiz quiz = adapter.generateQuiz(request(QuizFormat.OX, 1, CHUNKS));

        assertThat(questionsOf(quiz)).extracting(GeneratedQuestion::sourceQuote)
                .containsExactly("TCP는 연결 지향 프로토콜이다.");
    }

    @Test
    void 청크가_문제_수보다_적으면_청크_수만큼만_만들고_개념_이름이_겹치지_않는다() {
        GeneratedQuiz quiz = adapter.generateQuiz(request(QuizFormat.OX, 10, CHUNKS));

        assertThat(quiz.concepts()).hasSize(4);
        assertThat(quiz.concepts()).extracting(GeneratedConcept::name).doesNotHaveDuplicates();
        assertThat(quiz.concepts()).flatExtracting(GeneratedConcept::chunkSeqs).containsExactly(1, 2, 3, 4);
    }

    @Test
    void 요청한_문제_수가_청크보다_적으면_그만큼만_만든다() {
        GeneratedQuiz quiz = adapter.generateQuiz(request(QuizFormat.OX, 2, CHUNKS));

        assertThat(questionsOf(quiz)).hasSize(2);
    }

    @Test
    void 긴_첫_줄은_100자로_자르고_원문의_일부로_남긴다() {
        String longLine = "가".repeat(150);
        List<SourceChunk> chunks = List.of(new SourceChunk(1, longLine + "\n둘째 줄"));

        GeneratedQuiz quiz = adapter.generateQuiz(request(QuizFormat.OX, 1, chunks));

        assertThat(questionsOf(quiz)).singleElement()
                .extracting(GeneratedQuestion::sourceQuote)
                .isEqualTo("가".repeat(100));
        assertGroundedInChunks(quiz, chunks);
    }

    @Test
    void 청크가_적어_오답이_모자라면_고정_문구로_채워_보기_4개를_만든다() {
        List<SourceChunk> chunks = CHUNKS.subList(0, 2);

        GeneratedQuiz quiz = adapter.generateQuiz(request(QuizFormat.MULTIPLE_CHOICE, 10, chunks));

        assertThat(questionsOf(quiz)).allSatisfy(question -> {
            assertThat(question.options()).hasSize(4);
            assertThat(question.options()).extracting(GeneratedOption::content).doesNotHaveDuplicates();
            assertThat(question.options()).filteredOn(GeneratedOption::isCorrect).hasSize(1);
        });
    }

    // 슬라이드마다 제목이 반복되면 다른 청크의 인용문이 정답과 같을 수 있다. 그러면 정답이 둘이 된다
    @Test
    void 정답과_같은_문장은_오답으로_쓰지_않는다() {
        List<SourceChunk> chunks = List.of(
                new SourceChunk(1, "운영체제 5강\n프로세스 정의"),
                new SourceChunk(2, "운영체제 5강\n스레드 정의"),
                new SourceChunk(3, "문맥 교환은 비용이 든다.\n레지스터를 저장한다."),
                new SourceChunk(4, "스케줄러는 다음 프로세스를 고른다.\n선점형이 있다."));

        GeneratedQuiz quiz = adapter.generateQuiz(request(QuizFormat.MULTIPLE_CHOICE, 1, chunks));

        GeneratedQuestion question = questionsOf(quiz).getFirst();
        assertThat(question.options()).extracting(GeneratedOption::content)
                .filteredOn("운영체제 5강"::equals).hasSize(1);
        assertThat(question.options()).extracting(GeneratedOption::content).doesNotHaveDuplicates();
    }

    @Test
    void 객관식_정답_위치가_한_곳에_몰리지_않는다() {
        GeneratedQuiz quiz = adapter.generateQuiz(request(QuizFormat.MULTIPLE_CHOICE, 10, CHUNKS));

        List<Integer> answerPositions = questionsOf(quiz).stream()
                .map(question -> question.options().indexOf(
                        question.options().stream().filter(GeneratedOption::isCorrect).findFirst().orElseThrow()))
                .toList();
        assertThat(answerPositions.stream().distinct().count()).isGreaterThan(1);
    }

    @Test
    void 같은_요청이면_항상_같은_결과를_낸다() {
        QuizGenerationRequest request = request(QuizFormat.MULTIPLE_CHOICE, 10, CHUNKS);

        assertThat(adapter.generateQuiz(request)).isEqualTo(adapter.generateQuiz(request));
    }
}
