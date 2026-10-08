package com.datatrail.backend.service;

import com.datatrail.backend.entity.ChangeAnalysis;
import com.datatrail.backend.entity.EventContext;
import com.datatrail.backend.repository.ChangeAnalysisRepository;
import com.datatrail.backend.repository.EventContextRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChangeAnalysisService {

    private final ChangeAnalysisRepository changeAnalysisRepository;
    private final EventContextRepository eventContextRepository;

    public ChangeAnalysisService(
            ChangeAnalysisRepository changeAnalysisRepository,
            EventContextRepository eventContextRepository) {

        this.changeAnalysisRepository = changeAnalysisRepository;
        this.eventContextRepository = eventContextRepository;
    }

    public ChangeAnalysis createChangeAnalysis(
            Long contextId,
            String scoringModelVersion,
            Double dependencyScore,
            Double sensitivityScore,
            Double abnormalityScore,
            Double recoveryComplexity,
            Double importanceScore,
            String featureVector,
            String explanationSummary,
            java.time.LocalDateTime analyzedAt) {

        EventContext eventContext = eventContextRepository.findById(contextId)
                .orElseThrow(() ->
                        new RuntimeException("Event context not found"));

        ChangeAnalysis changeAnalysis = new ChangeAnalysis(
                eventContext,
                scoringModelVersion,
                dependencyScore,
                sensitivityScore,
                abnormalityScore,
                recoveryComplexity,
                importanceScore,
                featureVector,
                explanationSummary,
                analyzedAt
        );

        return changeAnalysisRepository.save(changeAnalysis);
    }

    public List<ChangeAnalysis> getAllChangeAnalyses() {
        return changeAnalysisRepository.findAll();
    }

    public ChangeAnalysis getChangeAnalysisById(Long id) {
        return changeAnalysisRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Change analysis not found"));
    }

    public void deleteChangeAnalysis(Long id) {

        if (!changeAnalysisRepository.existsById(id)) {
            throw new RuntimeException("Change analysis not found");
        }

        changeAnalysisRepository.deleteById(id);
    }
}