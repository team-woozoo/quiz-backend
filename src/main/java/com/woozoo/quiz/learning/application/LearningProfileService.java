package com.woozoo.quiz.learning.application;

import com.woozoo.quiz.learning.domain.CognitiveTypeStat;
import com.woozoo.quiz.learning.domain.ConceptStat;
import com.woozoo.quiz.learning.persistence.LearningProfileRepository;
import com.woozoo.quiz.quiz.domain.CognitiveType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class LearningProfileService {

    private final LearningProfileRepository learningProfileRepository;
    private final int minAnsweredPerType;
    private final int minAnsweredPerConcept;
    private final int weakConceptLimit;

    public LearningProfileService(LearningProfileRepository learningProfileRepository,
                                  @Value("${learning.profile.min-answered-per-type}") int minAnsweredPerType,
                                  @Value("${learning.profile.min-answered-per-concept}") int minAnsweredPerConcept,
                                  @Value("${learning.profile.weak-concept-limit}") int weakConceptLimit) {
        this.learningProfileRepository = learningProfileRepository;
        this.minAnsweredPerType = minAnsweredPerType;
        this.minAnsweredPerConcept = minAnsweredPerConcept;
        this.weakConceptLimit = weakConceptLimit;
    }

    public List<CognitiveTypeScore> getMyCognitiveTypeScores(long userId) {
        List<CognitiveTypeStat> cognitiveTypeStats = learningProfileRepository.countByCognitiveType(userId);

        Map<CognitiveType, CognitiveTypeStat> statsByType = cognitiveTypeStats.stream()
                .collect(Collectors.toMap(CognitiveTypeStat::cognitiveType, stat -> stat));

        List<CognitiveTypeScore> cognitiveTypeScores = new ArrayList<>();

        for (CognitiveType type : CognitiveType.values()) {
            CognitiveTypeStat stat = statsByType.get(type);
            int correctAnswerCount = stat == null ? 0 : stat.correctAnswerCount();
            int answeredCount = stat == null ? 0 : stat.answeredCount();
            Double accuracy = answeredCount == 0 ? null : (double) correctAnswerCount / answeredCount;
            cognitiveTypeScores.add(new CognitiveTypeScore(type, answeredCount, correctAnswerCount,
                    accuracy, answeredCount >= minAnsweredPerType));
        }
        return cognitiveTypeScores;
    }

    public List<WeakConceptScore> getMyWeakConcepts(long userId) {
        List<ConceptStat> conceptStats = learningProfileRepository
                .findWeakConcepts(userId, minAnsweredPerConcept, weakConceptLimit);

        List<WeakConceptScore> weakConceptScores = new ArrayList<>();

        for (ConceptStat stat : conceptStats) {
            double accuracy = (double) stat.correctAnswerCount() / stat.answeredCount();
            weakConceptScores.add(new WeakConceptScore(stat.conceptId(), stat.conceptName(),
                    stat.materialId(), stat.materialTitle(), stat.answeredCount(), stat.correctAnswerCount(), accuracy));
        }

        return weakConceptScores;
    }
}