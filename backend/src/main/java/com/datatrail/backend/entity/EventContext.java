package com.datatrail.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "event_context")
public class EventContext {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long contextId;

    @OneToOne
    @JoinColumn(name = "audit_id", nullable = false, unique = true)
    private AuditLog auditLog;

    @Column(name = "record_key_json", columnDefinition = "TEXT")
    private String recordKeyJson;

    @Column(name = "record_key_hash", length = 255)
    private String recordKeyHash;

    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    @Column(name = "edge_count_in")
    private Long edgeCountIn;

    @Column(name = "edge_count_out")
    private Long edgeCountOut;

    @Column(name = "built_at")
    private LocalDateTime builtAt;

    @Column(name = "build_status", length = 30)
    private String buildStatus;

    protected EventContext() {
    }

    public EventContext(
            AuditLog auditLog,
            String recordKeyJson,
            String recordKeyHash,
            String transactionId,
            Long edgeCountIn,
            Long edgeCountOut,
            LocalDateTime builtAt,
            String buildStatus) {

        this.auditLog = auditLog;
        this.recordKeyJson = recordKeyJson;
        this.recordKeyHash = recordKeyHash;
        this.transactionId = transactionId;
        this.edgeCountIn = edgeCountIn;
        this.edgeCountOut = edgeCountOut;
        this.builtAt = builtAt;
        this.buildStatus = buildStatus;
    }

    public Long getContextId() {
        return contextId;
    }

    public void setContextId(Long contextId) {
        this.contextId = contextId;
    }

    public AuditLog getAuditLog() {
        return auditLog;
    }

    public void setAuditLog(AuditLog auditLog) {
        this.auditLog = auditLog;
    }

    public String getRecordKeyJson() {
        return recordKeyJson;
    }

    public void setRecordKeyJson(String recordKeyJson) {
        this.recordKeyJson = recordKeyJson;
    }

    public String getRecordKeyHash() {
        return recordKeyHash;
    }

    public void setRecordKeyHash(String recordKeyHash) {
        this.recordKeyHash = recordKeyHash;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public Long getEdgeCountIn() {
        return edgeCountIn;
    }

    public void setEdgeCountIn(Long edgeCountIn) {
        this.edgeCountIn = edgeCountIn;
    }

    public Long getEdgeCountOut() {
        return edgeCountOut;
    }

    public void setEdgeCountOut(Long edgeCountOut) {
        this.edgeCountOut = edgeCountOut;
    }

    public LocalDateTime getBuiltAt() {
        return builtAt;
    }

    public void setBuiltAt(LocalDateTime builtAt) {
        this.builtAt = builtAt;
    }

    public String getBuildStatus() {
        return buildStatus;
    }

    public void setBuildStatus(String buildStatus) {
        this.buildStatus = buildStatus;
    }
}