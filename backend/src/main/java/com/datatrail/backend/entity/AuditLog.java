package com.datatrail.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long auditId;

    @ManyToOne
    @JoinColumn(name = "config_id", nullable = false)
    private MonitoringConfiguration configuration;

    @Column(nullable = false, length = 20)
    private String operation;

    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    @Column(name = "before_state", columnDefinition = "TEXT")
    private String beforeState;

    @Column(name = "after_state", columnDefinition = "TEXT")
    private String afterState;

    @Column(name = "changed_by", length = 100)
    private String changedBy;

    @Column(name = "event_timestamp", nullable = false)
    private LocalDateTime eventTimestamp;

    @Column(name = "log_hash", nullable = false, length = 255)
    private String logHash;

    @Column(name = "prev_hash", length = 255)
    private String prevHash;

    @Column(name = "chain_sequence", nullable = false)
    private Long chainSequence;

    @Column(name = "database_user", length = 100)
    private String databaseUser;

    @Column(name = "actor_source", length = 30)
    private String actorSource;

    protected AuditLog() {
    }

    public AuditLog(
        MonitoringConfiguration configuration,
        String operation,
        String transactionId,
        String beforeState,
        String afterState,
        String changedBy,
        String databaseUser,
        String actorSource,
        LocalDateTime eventTimestamp,
        String logHash,
        String prevHash,
        Long chainSequence) {
    this.configuration = configuration;
    this.operation = operation;
    this.transactionId = transactionId;
    this.beforeState = beforeState;
    this.afterState = afterState;
    this.changedBy = changedBy;
    this.databaseUser = databaseUser;
    this.actorSource = actorSource;
    this.eventTimestamp = eventTimestamp;
    this.logHash = logHash;
    this.prevHash = prevHash;
    this.chainSequence = chainSequence;
}
public AuditLog(
        MonitoringConfiguration configuration,
        String operation,
        String transactionId,
        String beforeState,
        String afterState,
        String changedBy,
        LocalDateTime eventTimestamp,
        String logHash,
        String prevHash,
        Long chainSequence) {

    this(
            configuration,
            operation,
            transactionId,
            beforeState,
            afterState,
            changedBy,
            null,
            null,
            eventTimestamp,
            logHash,
            prevHash,
            chainSequence
    );
}
    public Long getAuditId() {
        return auditId;
    }

    public void setAuditId(Long auditId) {
        this.auditId = auditId;
    }

    public MonitoringConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(MonitoringConfiguration configuration) {
        this.configuration = configuration;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getBeforeState() {
        return beforeState;
    }

    public void setBeforeState(String beforeState) {
        this.beforeState = beforeState;
    }

    public String getAfterState() {
        return afterState;
    }

    public void setAfterState(String afterState) {
        this.afterState = afterState;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }

    public LocalDateTime getEventTimestamp() {
        return eventTimestamp;
    }

    public void setEventTimestamp(LocalDateTime eventTimestamp) {
        this.eventTimestamp = eventTimestamp;
    }

    public String getLogHash() {
        return logHash;
    }

    public void setLogHash(String logHash) {
        this.logHash = logHash;
    }

    public String getPrevHash() {
        return prevHash;
    }

    public void setPrevHash(String prevHash) {
        this.prevHash = prevHash;
    }

    public Long getChainSequence() {
        return chainSequence;
    }

    public void setChainSequence(Long chainSequence) {
        this.chainSequence = chainSequence;
    }

    public String getDatabaseUser() {
    return databaseUser;
}

public void setDatabaseUser(String databaseUser) {
    this.databaseUser = databaseUser;
}

public String getActorSource() {
    return actorSource;
}

public void setActorSource(String actorSource) {
    this.actorSource = actorSource;
}

}