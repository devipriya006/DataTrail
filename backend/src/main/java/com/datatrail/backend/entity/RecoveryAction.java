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
    @JoinColumn(name = "analysis_id", nullable = true)
    private ChangeAnalysis analysis;

    @ManyToOne
    @JoinColumn(name = "audit_id")
    private AuditLog auditLog;

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

    @ManyToOne
    @JoinColumn(name = "override_by")
    private User overrideBy;

    @Column(name = "override_reason", columnDefinition = "TEXT")
    private String overrideReason;

    @Column(name = "override_at")
    private LocalDateTime overrideAt;

    @Column(name = "override_decision", length = 30)
    private String overrideDecision;

    protected RecoveryAction() {
    }

    public RecoveryAction(
            ChangeAnalysis analysis,
            AuditLog auditLog,
            String recommendedSql,
            String recoveryType,
            String approvalStatus,
            User approvedBy,
            LocalDateTime approvedAt,
            LocalDateTime createdAt) {

        this.analysis = analysis;
        this.auditLog = auditLog;
        this.recommendedSql = recommendedSql;
        this.recoveryType = recoveryType;
        this.approvalStatus = approvalStatus;
        this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
        this.createdAt = createdAt;
    }

    public RecoveryAction(
            ChangeAnalysis analysis,
            String recommendedSql,
            String recoveryType,
            String approvalStatus,
            User approvedBy,
            LocalDateTime approvedAt,
            LocalDateTime createdAt) {
        this(analysis, null, recommendedSql, recoveryType, approvalStatus, approvedBy, approvedAt, createdAt);
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

    public AuditLog getAuditLog() {
        return auditLog;
    }

    public void setAuditLog(AuditLog auditLog) {
        this.auditLog = auditLog;
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

    public User getOverrideBy() {
        return overrideBy;
    }

    public void setOverrideBy(User overrideBy) {
        this.overrideBy = overrideBy;
    }

    public String getOverrideReason() {
        return overrideReason;
    }

    public void setOverrideReason(String overrideReason) {
        this.overrideReason = overrideReason;
    }

    public LocalDateTime getOverrideAt() {
        return overrideAt;
    }

    public void setOverrideAt(LocalDateTime overrideAt) {
        this.overrideAt = overrideAt;
    }

    public String getOverrideDecision() {
        return overrideDecision;
    }

    public void setOverrideDecision(String overrideDecision) {
        this.overrideDecision = overrideDecision;
    }
}