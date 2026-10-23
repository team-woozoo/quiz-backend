-- 약점 퀴즈 생성 작업은 어느 대표 개념으로 만드는지 들고 있어야 백그라운드 작업이 그 개념의 페이지를 모을 수 있다.
-- 대표 개념은 여러 자료의 개념을 묶을 수 있어(MVP 2), 자료 개념이 아니라 대표 개념을 가리킨다.
-- 유형만 겨냥하는 약점 퀴즈도 둘 수 있게(weakness_target 과 같이) 약점 작업에 반드시 있어야 하지는 않고, 일반 작업에는 없다
ALTER TABLE generation_job
    ADD COLUMN user_concept_id BIGINT REFERENCES user_concept (id) ON DELETE CASCADE,
    ADD CONSTRAINT ck_generation_job_user_concept CHECK (kind = 'WEAKNESS' OR user_concept_id IS NULL);
