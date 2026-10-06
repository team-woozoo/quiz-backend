--==========================================================
-- 사용자
--==========================================================

CREATE TABLE users (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email             VARCHAR(255) UNIQUE NOT NULL,
    password_hash     VARCHAR(255) NOT NULL,
    nickname          VARCHAR(50) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE refresh_token (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash        VARCHAR(64) NOT NULL UNIQUE,
    expires_at        TIMESTAMPTZ NOT NULL,
    revoked_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_token_user_id ON refresh_token (user_id);

--==========================================================
-- 자료
--==========================================================

CREATE TABLE material (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id          BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title             VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    file_type         VARCHAR(10) NOT NULL CHECK (file_type IN ('PDF', 'TXT')),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_material_owner_id_id ON material (owner_id, id);

CREATE TABLE material_chunk (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    material_id       BIGINT NOT NULL REFERENCES material (id) ON DELETE CASCADE,
    seq               INT NOT NULL,
    page_no           INT,
    content           TEXT NOT NULL,
    UNIQUE (material_id, seq)
);

--==========================================================
-- 개념
--==========================================================

CREATE TABLE material_concept (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    material_id       BIGINT NOT NULL REFERENCES material (id) on DELETE CASCADE,
    name              VARCHAR(255) NOT NULL,
    description       TEXT NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (material_id, name)
);

CREATE TABLE concept_chunk (
    material_concept_id BIGINT NOT NULL REFERENCES material_concept (id) ON DELETE CASCADE,
    material_chunk_id   BIGINT NOT NULL REFERENCES material_chunk (id) ON DELETE CASCADE,
    PRIMARY KEY (material_concept_id, material_chunk_id)
);

CREATE TABLE user_concept (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name              VARCHAR(255) NOT NULL,
    description       TEXT NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_user_concept_user_id ON user_concept (user_id);

CREATE TABLE concept_mapping (
    material_concept_id BIGINT PRIMARY KEY REFERENCES material_concept (id) ON DELETE CASCADE,
    user_concept_id     BIGINT NOT NULL REFERENCES user_concept (id) ON DELETE CASCADE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_concept_mapping_user_concept_id ON concept_mapping (user_concept_id);

--==========================================================
-- 퀴즈
--==========================================================

CREATE TABLE quiz (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    material_id       BIGINT REFERENCES material (id) ON DELETE CASCADE,
    kind              VARCHAR(10) NOT NULL CHECK (kind IN ('NORMAL', 'WEAKNESS')),
    format            VARCHAR(20) NOT NULL CHECK (format IN ('MULTIPLE_CHOICE', 'OX')),
    difficulty        VARCHAR(10) NOT NULL CHECK (difficulty IN ('EASY', 'NORMAL', 'HARD')),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((kind = 'NORMAL' AND material_id IS NOT NULL) OR (kind = 'WEAKNESS' AND material_id IS NULL))
);
CREATE INDEX idx_quiz_user_id_id ON quiz (user_id, id);
CREATE INDEX idx_quiz_material_id ON quiz (material_id);

CREATE TABLE weakness_target (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    quiz_id           BIGINT NOT NULL REFERENCES quiz (id) ON DELETE CASCADE,
    user_concept_id   BIGINT REFERENCES user_concept (id) ON DELETE CASCADE,
    cognitive_type    VARCHAR(20) CHECK (cognitive_type IN ('REMEMBER', 'UNDERSTAND', 'APPLY', 'ANALYZE', 'EVALUATE')),
    CHECK (user_concept_id IS NOT NULL OR cognitive_type IS NOT NULL)
);
CREATE INDEX idx_weakness_target_quiz_id ON weakness_target (quiz_id);

CREATE TABLE generation_job (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    material_id       BIGINT REFERENCES material (id) ON DELETE CASCADE,
    kind              VARCHAR(10) NOT NULL CHECK (kind IN ('NORMAL', 'WEAKNESS')),
    format            VARCHAR(20) NOT NULL CHECK (format IN ('MULTIPLE_CHOICE', 'OX')),
    difficulty        VARCHAR(10) NOT NULL CHECK (difficulty IN ('EASY', 'NORMAL', 'HARD')),
    status            VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'DONE', 'FAILED')),
    quiz_id           BIGINT REFERENCES quiz (id) ON DELETE SET NULL,
    error_code        VARCHAR(50),
    error_message     TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at        TIMESTAMPTZ,
    finished_at       TIMESTAMPTZ,
    CHECK ((kind = 'NORMAL' AND material_id IS NOT NULL) OR (kind = 'WEAKNESS' AND material_id IS NULL))
);
CREATE INDEX idx_generation_job_user_id ON generation_job (user_id);
CREATE INDEX idx_generation_job_unfinished ON generation_job (status) WHERE status IN ('PENDING', 'PROCESSING');

CREATE TABLE question (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    quiz_id             BIGINT NOT NULL REFERENCES quiz (id) ON DELETE CASCADE,
    order_no            INT NOT NULL,
    material_concept_id BIGINT NOT NULL REFERENCES material_concept (id) ON DELETE CASCADE,
    source_chunk_id     BIGINT NOT NULL REFERENCES material_chunk (id) ON DELETE CASCADE,
    source_quote        TEXT NOT NULL,
    cognitive_type      VARCHAR(20) NOT NULL CHECK (cognitive_type IN ('REMEMBER', 'UNDERSTAND', 'APPLY', 'ANALYZE', 'EVALUATE')),
    content             TEXT NOT NULL,
    explanation         TEXT NOT NULL,
    UNIQUE (quiz_id, order_no)
);
CREATE INDEX idx_question_material_concept_id ON question (material_concept_id);

CREATE TABLE question_option (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    question_id       BIGINT NOT NULL REFERENCES question (id) ON DELETE CASCADE,
    order_no          INT NOT NULL,
    content           TEXT NOT NULL,
    is_correct        BOOLEAN NOT NULL,
    UNIQUE (question_id, order_no)
);
CREATE UNIQUE INDEX uq_question_option_one_correct ON question_option (question_id) WHERE is_correct;

--==========================================================
-- 학습
--==========================================================

CREATE TABLE submission (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    quiz_id           BIGINT NOT NULL REFERENCES quiz (id) ON DELETE CASCADE,
    submitted_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_submission_user_id ON submission (user_id);
CREATE INDEX idx_submission_quiz_id ON submission (quiz_id);

CREATE TABLE submission_answer (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    submission_id      BIGINT NOT NULL REFERENCES submission (id) ON DELETE CASCADE,
    question_id        BIGINT NOT NULL REFERENCES question (id) ON DELETE CASCADE,
    selected_option_id BIGINT REFERENCES question_option (id) ON DELETE CASCADE,
    is_correct         BOOLEAN NOT NULL,
    UNIQUE (submission_id, question_id)
);
CREATE INDEX idx_submission_answer_question_id ON submission_answer (question_id);

CREATE TABLE wrong_answer (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    question_id       BIGINT NOT NULL REFERENCES question (id) ON DELETE CASCADE,
    wrong_count       INT NOT NULL DEFAULT 1 CHECK (wrong_count >= 1),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_wrong_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at       TIMESTAMPTZ,
    UNIQUE (user_id, question_id)
);
CREATE INDEX idx_wrong_answer_unresolved ON wrong_answer (user_id, id) WHERE resolved_at IS NULL;
