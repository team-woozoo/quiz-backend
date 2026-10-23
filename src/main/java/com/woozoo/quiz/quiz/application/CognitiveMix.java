package com.woozoo.quiz.quiz.application;

import com.woozoo.quiz.quiz.domain.CognitiveType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

// 퀴즈 하나에 넣을 인지 유형별 문제 수를 설정 문자열("REMEMBER:2,APPLY:3,...")에서 읽는다.
// 약점 분석이 다섯 유형별로 이뤄져, 모델에 맡기면 쉬운 유형으로 몰려 빈 축이 생긴다. 그래서 개수를 정해 넘긴다
final class CognitiveMix {

    private CognitiveMix() {
    }

    // 합이 문제 수와 다르면 모델이 어느 쪽을 따를지 알 수 없다. 설정 실수는 앱이 뜰 때 바로 드러나게 예외로 막는다
    static Map<CognitiveType, Integer> parse(String spec, int questionCount) {
        Map<CognitiveType, Integer> counts = new EnumMap<>(CognitiveType.class);
        for (String entry : spec.split(",")) {
            String[] pair = entry.strip().split(":");
            if (pair.length != 2) {
                throw new IllegalArgumentException("유형별 문제 수는 TYPE:개수 모양이어야 한다: " + entry);
            }
            CognitiveType type = CognitiveType.valueOf(pair[0].strip());
            int count = Integer.parseInt(pair[1].strip());
            if (count <= 0) {
                throw new IllegalArgumentException("유형별 문제 수는 1 이상이어야 한다: " + entry);
            }
            if (counts.put(type, count) != null) {
                throw new IllegalArgumentException("같은 유형이 두 번 있다: " + type);
            }
        }
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        if (total != questionCount) {
            throw new IllegalArgumentException(
                    "유형별 문제 수의 합(" + total + ")이 문제 수(" + questionCount + ")와 다르다");
        }
        return Collections.unmodifiableMap(counts);
    }

    // 문제 수를 줄일 때 높은 유형부터 하나씩 뺀다. 재료가 적을수록 높은 유형의 근거가 먼저 모자라기 때문이다
    static Map<CognitiveType, Integer> limitTo(Map<CognitiveType, Integer> counts, int questionCount) {
        Map<CognitiveType, Integer> limited = new EnumMap<>(CognitiveType.class);
        limited.putAll(counts);
        int excess = limited.values().stream().mapToInt(Integer::intValue).sum() - questionCount;
        CognitiveType[] types = CognitiveType.values();
        for (int i = types.length - 1; i >= 0 && excess > 0; i--) {
            int count = limited.getOrDefault(types[i], 0);
            int removed = Math.min(count, excess);
            if (count - removed == 0) {
                limited.remove(types[i]);
            } else {
                limited.put(types[i], count - removed);
            }
            excess -= removed;
        }
        return Collections.unmodifiableMap(limited);
    }
}
