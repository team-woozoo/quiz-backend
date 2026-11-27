-- 업로드 분석 전에 올린 자료는 개념에 재료 표시가 없고 해시도 채울 수 없다. 원본 파일을 저장하지 않아 다시 분석할 수 없고,
-- 생성기가 저장된 개념에만 문제를 붙이게 되면서 이런 자료로는 퀴즈를 만들 수 없어 지운다.
-- 청크, 개념, 일반 퀴즈와 문제, 풀이 기록, 생성 작업은 자료를 따라 함께 지워진다
DELETE FROM material WHERE content_hash IS NULL;

-- 약점 퀴즈는 자료를 가리키지 않아 자료와 함께 지워지지 않는다. 문제만 개념을 따라 지워져 문제가 없는 퀴즈가 남으므로 지운다.
-- 퀴즈는 문제가 하나 이상일 때만 저장되므로, 문제가 없는 약점 퀴즈는 위 삭제로 비워진 것뿐이다
DELETE FROM quiz q WHERE q.kind = 'WEAKNESS' AND NOT EXISTS (SELECT 1 FROM question qq WHERE qq.quiz_id = q.id);

-- 대표 개념도 사용자에만 속해 자료와 함께 지워지지 않고, 자료 개념과의 연결만 사라진다.
-- 이어진 자료 개념이 없는 대표 개념은 낼 페이지가 없어 지운다. 그 대표 개념의 약점 대상과 생성 작업은 함께 지워진다
DELETE FROM user_concept uc WHERE NOT EXISTS (SELECT 1 FROM concept_mapping cm WHERE cm.user_concept_id = uc.id);

-- 남은 자료는 모두 업로드 때 분석을 거쳐 해시가 있고, 개념에는 재료가 없더라도 빈 배열이 있다.
-- 이제 NULL 이 생길 길이 없어, 앱이 NULL 을 따로 다루지 않게 막아 둔다
ALTER TABLE material ALTER COLUMN content_hash SET NOT NULL;
ALTER TABLE material_concept ALTER COLUMN tags SET NOT NULL;
