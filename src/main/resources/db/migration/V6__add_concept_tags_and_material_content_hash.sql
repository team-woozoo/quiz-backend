-- 업로드할 때 자료를 분석해 개념마다 문제의 재료(비교할 개념 짝, 성립 조건, 규칙, 함께 작용하는 요인, 대안)를 표시해 둔다.
-- 유형별로 문제를 나눠 만들 때 재료가 있는 개념만 그 유형에 배정하려는 것이다. 재료 이름은 인지 유형과 따로 두어,
-- 유형 정의가 바뀌어도 자료에 무엇이 있는지는 그대로 남는다.
-- 분석 전에 문제와 함께 만들어진 개념은 표시가 없어 NULL 로 두고, 분석했지만 재료가 없는 개념은 빈 배열이다
ALTER TABLE material_concept ADD COLUMN tags TEXT[];

-- 같은 사용자가 같은 파일을 다시 올리면 분석을 다시 돌리지 않고, 개념이 자료 둘로 갈라지지 않게 막는다.
-- 파일 바이트의 SHA-256 을 16진수 64자로 둔다. 다시 저장한 PDF 는 바이트가 달라 다른 자료로 본다.
-- 다른 사용자는 같은 파일을 각자 올릴 수 있어야 해서 사용자 안에서만 겹치지 않게 한다.
-- 원본 파일을 저장하지 않아 이미 올린 자료는 해시를 채울 수 없어 NULL 로 두고, NULL 끼리는 겹치지 않는다
ALTER TABLE material ADD COLUMN content_hash VARCHAR(64);
CREATE UNIQUE INDEX uq_material_owner_id_content_hash ON material (owner_id, content_hash);
