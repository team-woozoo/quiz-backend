-- 오답 노트에서 다시 푼 답도 제출로 남긴다. 그래야 문제당 최근 답으로 세는 분석에 복습 결과가 들어가고,
-- 언제 틀리고 언제 고쳤는지가 남는다.
-- 복습은 여러 퀴즈의 문제를 한 번에 보내서 퀴즈가 없다. 퀴즈별 기록(푼 횟수, 최근 점수)은 퀴즈로 찾으므로
-- 복습 제출은 거기에 섞이지 않고, 그날 퀴즈를 푼 점수가 바뀌지 않는다
ALTER TABLE submission
    ADD COLUMN kind VARCHAR(10) NOT NULL DEFAULT 'QUIZ' CHECK (kind IN ('QUIZ', 'REVIEW')),
    ALTER COLUMN quiz_id DROP NOT NULL,
    ADD CONSTRAINT ck_submission_kind_quiz
        CHECK ((kind = 'QUIZ' AND quiz_id IS NOT NULL) OR (kind = 'REVIEW' AND quiz_id IS NULL));
