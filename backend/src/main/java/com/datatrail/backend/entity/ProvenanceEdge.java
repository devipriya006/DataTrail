package com.datatrail.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "provenance_edges")
public class ProvenanceEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long edgeId;

    @ManyToOne
    @JoinColumn(name = "from_audit_id", nullable = false)
    private AuditLog fromAuditLog;

    @ManyToOne
    @JoinColumn(name = "to_audit_id", nullable = false)
    private AuditLog toAuditLog;

    @Column(name = "relationship_type", length = 50)
    private String relationshipType;

    @Column(name = "edge_weight")
    private Double edgeWeight;

    @Column(name = "inference_method", length = 100)
    private String inferenceMethod;

    @Column(name = "snapshot_id")
    private Long snapshotId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    protected ProvenanceEdge() {
    }

    public ProvenanceEdge(
            AuditLog fromAuditLog,
            AuditLog toAuditLog,
            String relationshipType,
            Double edgeWeight,
            String inferenceMethod,
            Long snapshotId,
            LocalDateTime createdAt) {

        this.fromAuditLog = fromAuditLog;
        this.toAuditLog = toAuditLog;
        this.relationshipType = relationshipType;
        this.edgeWeight = edgeWeight;
        this.inferenceMethod = inferenceMethod;
        this.snapshotId = snapshotId;
        this.createdAt = createdAt;
    }

    public Long getEdgeId() {
        return edgeId;
    }

    public void setEdgeId(Long edgeId) {
        this.edgeId = edgeId;
    }

    public AuditLog getFromAuditLog() {
        return fromAuditLog;
    }

    public void setFromAuditLog(AuditLog fromAuditLog) {
        this.fromAuditLog = fromAuditLog;
    }

    public AuditLog getToAuditLog() {
        return toAuditLog;
    }

    public void setToAuditLog(AuditLog toAuditLog) {
        this.toAuditLog = toAuditLog;
    }

    public String getRelationshipType() {
        return relationshipType;
    }

    public void setRelationshipType(String relationshipType) {
        this.relationshipType = relationshipType;
    }

    public Double getEdgeWeight() {
        return edgeWeight;
    }

    public void setEdgeWeight(Double edgeWeight) {
        this.edgeWeight = edgeWeight;
    }

    public String getInferenceMethod() {
        return inferenceMethod;
    }

    public void setInferenceMethod(String inferenceMethod) {
        this.inferenceMethod = inferenceMethod;
    }

    public Long getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(Long snapshotId) {
        this.snapshotId = snapshotId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}