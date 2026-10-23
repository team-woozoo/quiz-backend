package com.woozoo.quiz.concept.application;

import com.woozoo.quiz.concept.persistence.MaterialConceptRepository;
import com.woozoo.quiz.concept.persistence.UserConceptRepository;
import com.woozoo.quiz.global.error.BusinessException;
import com.woozoo.quiz.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 자료 개념을 사용자 대표 개념에 잇는다. 지금(MVP 1)은 비슷한 개념을 찾지 않고 자료 개념마다 대표 개념을 하나씩 만든다.
// 생성 때 바로 잇지 않고, 대표 개념이 필요한 약점 퀴즈를 요청할 때 아직 없으면 만든다
@Service
public class ConceptMappingService {

    private final MaterialConceptRepository materialConceptRepository;
    private final UserConceptRepository userConceptRepository;

    public ConceptMappingService(MaterialConceptRepository materialConceptRepository,
                                 UserConceptRepository userConceptRepository) {
        this.materialConceptRepository = materialConceptRepository;
        this.userConceptRepository = userConceptRepository;
    }

    // 남의 개념도 없는 개념과 같은 응답을 줘서, 다른 사용자의 개념 id 가 있는지 알 수 없게 한다
    @Transactional
    public long mapToUserConcept(long materialConceptId, long userId) {
        if (!materialConceptRepository.lockOwnedBy(materialConceptId, userId)) {
            throw new BusinessException(ErrorCode.CONCEPT_NOT_FOUND);
        }
        return userConceptRepository.findMappedUserConceptId(materialConceptId)
                .orElseGet(() -> userConceptRepository.createMappedUserConcept(materialConceptId, userId));
    }
}
