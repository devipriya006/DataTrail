package com.datatrail.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "recovery_execution")
public class RecoveryExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long executionId;

    @ManyToOne
    @JoinColumn(name = "recovery_id", nullable = false)
    private RecoveryAction recoveryAction;

    @Column(name = "execution_type", length = 50)
    private String executionType;

    @Column(name = "dry_run_result", columnDefinition = "TEXT")
    private String dryRunResult;

    @Column(name = "blast_radius_estimate")
    private Double blastRadiusEstimate;

    @Column(name = "execution_status", length = 30)
    private String executionStatus;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @ManyToOne
    @JoinColumn(name = "executed_by")
    private User executedBy;

    protected RecoveryExecution() {
    }

    public RecoveryExecution(
            RecoveryAction recoveryAction,
            String executionType,
            String dryRunResult,
            Double blastRadiusEstimate,
            String executionStatus,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            User executedBy) {

        this.recoveryAction = recoveryAction;
        this.executionType = executionType;
        this.dryRunResult = dryRunResult;
        this.blastRadiusEstimate = blastRadiusEstimate;
        this.executionStatus = executionStatus;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.executedBy = executedBy;
    }

    public Long getExecutionId() {
        return executionId;
    }

    public void setExecutionId(Long executionId) {
        this.executionId = executionId;
    }

    public RecoveryAction getRecoveryAction() {
        return recoveryAction;
    }

    public void setRecoveryAction(RecoveryAction recoveryAction) {
        this.recoveryAction = recoveryAction;
    }

    public String getExecutionType() {
        return executionType;
    }

    public void setExecutionType(String executionType) {
        this.executionType = executionType;
    }

    public String getDryRunResult() {
        return dryRunResult;
    }

    public void setDryRunResult(String dryRunResult) {
        this.dryRunResult = dryRunResult;
    }

    public Double getBlastRadiusEstimate() {
        return blastRadiusEstimate;
    }

    public void setBlastRadiusEstimate(Double blastRadiusEstimate) {
        this.blastRadiusEstimate = blastRadiusEstimate;
    }

    public String getExecutionStatus() {
        return executionStatus;
    }

    public void setExecutionStatus(String executionStatus) {
        this.executionStatus = executionStatus;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public User getExecutedBy() {
        return executedBy;
    }

    public void setExecutedBy(User executedBy) {
        this.executedBy = executedBy;
    }
}