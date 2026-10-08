package com.datatrail.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "change_analysis")
public class ChangeAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long analysisId;

    @ManyToOne
    @JoinColumn(name = "context_id", nullable = false)
    private EventContext eventContext;

    @Column(name = "scoring_model_version", length = 50)
    private String scoringModelVersion;

    @Column(name = "dependency_score")
    private Double dependencyScore;

    @Column(name = "sensitivity_score")
    private Double sensitivityScore;

    @Column(name = "abnormality_score")
    private Double abnormalityScore;

    @Column(name = "recovery_complexity")
    private Double recoveryComplexity;

    @Column(name = "importance_score")
    private Double importanceScore;

    @Column(name = "feature_vector", columnDefinition = "TEXT")
    private String featureVector;

    @Column(name = "explanation_summary", columnDefinition = "TEXT")
    private String explanationSummary;

    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;

    protected ChangeAnalysis() {
    }

    public ChangeAnalysis(
            EventContext eventContext,
            String scoringModelVersion,
            Double dependencyScore,
            Double sensitivityScore,
            Double abnormalityScore,
            Double recoveryComplexity,
            Double importanceScore,
            String featureVector,
            String explanationSummary,
            LocalDateTime analyzedAt) {

        this.eventContext = eventContext;
        this.scoringModelVersion = scoringModelVersion;
        this.dependencyScore = dependencyScore;
        this.sensitivityScore = sensitivityScore;
        this.abnormalityScore = abnormalityScore;
        this.recoveryComplexity = recoveryComplexity;
        this.importanceScore = importanceScore;
        this.featureVector = featureVector;
        this.explanationSummary = explanationSummary;
        this.analyzedAt = analyzedAt;
    }

    public Long getAnalysisId() {
        return analysisId;
    }

    public void setAnalysisId(Long analysisId) {
        this.analysisId = analysisId;
    }

    public EventContext getEventContext() {
        return eventContext;
    }

    public void setEventContext(EventContext eventContext) {
        this.eventContext = eventContext;
    }

    public String getScoringModelVersion() {
        return scoringModelVersion;
    }

    public void setScoringModelVersion(String scoringModelVersion) {
        this.scoringModelVersion = scoringModelVersion;
    }

    public Double getDependencyScore() {
        return dependencyScore;
    }

    public void setDependencyScore(Double dependencyScore) {
        this.dependencyScore = dependencyScore;
    }

    public Double getSensitivityScore() {
        return sensitivityScore;
    }

    public void setSensitivityScore(Double sensitivityScore) {
        this.sensitivityScore = sensitivityScore;
    }

    public Double getAbnormalityScore() {
        return abnormalityScore;
    }

    public void setAbnormalityScore(Double abnormalityScore) {
        this.abnormalityScore = abnormalityScore;
    }

    public Double getRecoveryComplexity() {
        return recoveryComplexity;
    }

    public void setRecoveryComplexity(Double recoveryComplexity) {
        this.recoveryComplexity = recoveryComplexity;
    }

    public Double getImportanceScore() {
        return importanceScore;
    }

    public void setImportanceScore(Double importanceScore) {
        this.importanceScore = importanceScore;
    }

    public String getFeatureVector() {
        return featureVector;
    }

    public void setFeatureVector(String featureVector) {
        this.featureVector = featureVector;
    }

    public String getExplanationSummary() {
        return explanationSummary;
    }

    public void setExplanationSummary(String explanationSummary) {
        this.explanationSummary = explanationSummary;
    }

    public LocalDateTime getAnalyzedAt() {
        return analyzedAt;
    }

    public void setAnalyzedAt(LocalDateTime analyzedAt) {
        this.analyzedAt = analyzedAt;
    }
}