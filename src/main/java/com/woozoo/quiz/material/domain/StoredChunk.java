package com.woozoo.quiz.material.domain;

// 저장된 청크다. AI 는 seq 로 청크를 가리키고 DB 는 id 로 잇기 때문에 둘을 같이 들고 다닌다
public record StoredChunk(long id, int seq, String content) {
}
