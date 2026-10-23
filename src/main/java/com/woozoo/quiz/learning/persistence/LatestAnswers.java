package com.woozoo.quiz.learning.persistence;

// 문제마다 내가 낸 가장 최근 답 하나. 같은 문제를 여러 번 풀어도 한 번만 세고, 지금 실력을 따라가게 한다.
// 정답률을 내는 쿼리가 모두 이 기준을 같이 쓰도록 한 곳에 둔다. :userId 를 받는다
final class LatestAnswers {

    static final String WITH_LATEST = """
            WITH latest AS (
                SELECT DISTINCT ON (sa.question_id) sa.question_id, sa.is_correct
                FROM submission s
                JOIN submission_answer sa ON sa.submission_id = s.id
                WHERE s.user_id = :userId
                ORDER BY sa.question_id, s.submitted_at DESC, s.id DESC
            )
            """;

    private LatestAnswers() {
    }
}
