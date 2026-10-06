package com.datatrail.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "recovery_action")
public class RecoveryAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long recoveryId;

    @ManyToOne
    @JoinColumn(name = "analysis_id", nullable = false)
    private ChangeAnalysis analysis;

    @Column(name = "recommended_sql", columnDefinition = "TEXT")
    private String recommendedSql;

    @Column(name = "recovery_type", length = 50)
    private String recoveryType;

    @Column(name = "approval_status", length = 30)
    private String approvalStatus;

    @ManyToOne
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    protected RecoveryAction() {
    }

    public RecoveryAction(
            ChangeAnalysis analysis,
            String recommendedSql,
            String recoveryType,
            String approvalStatus,
            User approvedBy,
            LocalDateTime approvedAt,
            LocalDateTime createdAt) {

        this.analysis = analysis;
        this.recommendedSql = recommendedSql;
        this.recoveryType = recoveryType;
        this.approvalStatus = approvalStatus;
        this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
        this.createdAt = createdAt;
    }

    public Long getRecoveryId() {
        return recoveryId;
    }

    public void setRecoveryId(Long recoveryId) {
        this.recoveryId = recoveryId;
    }

    public ChangeAnalysis getAnalysis() {
        return analysis;
    }

    public void setAnalysis(ChangeAnalysis analysis) {
        this.analysis = analysis;
    }

    public String getRecommendedSql() {
        return recommendedSql;
    }

    public void setRecommendedSql(String recommendedSql) {
        this.recommendedSql = recommendedSql;
    }

    public String getRecoveryType() {
        return recoveryType;
    }

    public void setRecoveryType(String recoveryType) {
        this.recoveryType = recoveryType;
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public User getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(User approvedBy) {
        this.approvedBy = approvedBy;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}