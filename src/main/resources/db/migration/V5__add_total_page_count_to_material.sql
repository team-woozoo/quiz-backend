-- 앱이 근거 페이지를 "4 / 30쪽"처럼 보여 주려면 원본 PDF 의 전체 쪽수가 필요하다.
-- 글자 없는 페이지는 청크로 저장하지 않아서 청크 수로는 알 수 없고, 업로드할 때 세어 둬야 한다.
-- 원본 파일은 저장하지 않아 이미 올린 자료는 다시 셀 수 없다. 가장 뒤 청크의 쪽 번호로 채우므로
-- 끝쪽에 글자 없는 페이지가 있던 자료는 실제보다 작을 수 있다
ALTER TABLE material ADD COLUMN total_page_count INT;

UPDATE material m
SET total_page_count = (SELECT GREATEST(COALESCE(MAX(c.page_no), COUNT(*)), 1)
                        FROM material_chunk c
                        WHERE c.material_id = m.id);

ALTER TABLE material
    ALTER COLUMN total_page_count SET NOT NULL,
    ADD CONSTRAINT ck_material_total_page_count CHECK (total_page_count >= 1);
