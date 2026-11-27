package com.woozoo.quiz;

import java.util.concurrent.atomic.AtomicLong;

// 자료의 content_hash 는 NOT NULL 이고 사용자 안에서 겹치면 안 돼서, 테스트가 저장하는 자료마다 다른 해시를 준다.
// 실제 파일 해시와 같은 16진수 64자 모양으로 만든다
public final class TestHashes {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private TestHashes() {
    }

    public static String unique() {
        return String.format("%064x", SEQUENCE.incrementAndGet());
    }
}
