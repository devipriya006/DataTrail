package com.datatrail.backend.service;

import com.datatrail.backend.entity.AuditLog;
import com.datatrail.backend.entity.ProvenanceEdge;
import com.datatrail.backend.repository.AuditLogRepository;
import com.datatrail.backend.repository.ProvenanceEdgeRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProvenanceEdgeService {

    private final ProvenanceEdgeRepository provenanceEdgeRepository;
    private final AuditLogRepository auditLogRepository;

    public ProvenanceEdgeService(
            ProvenanceEdgeRepository provenanceEdgeRepository,
            AuditLogRepository auditLogRepository) {

        this.provenanceEdgeRepository = provenanceEdgeRepository;
        this.auditLogRepository = auditLogRepository;
    }

    public ProvenanceEdge createProvenanceEdge(
            Long fromAuditId,
            Long toAuditId,
            String relationshipType,
            Double edgeWeight,
            String inferenceMethod,
            Long snapshotId,
            java.time.LocalDateTime createdAt) {

        AuditLog fromAuditLog = auditLogRepository.findById(fromAuditId)
                .orElseThrow(() ->
                        new RuntimeException("From audit log not found"));

        AuditLog toAuditLog = auditLogRepository.findById(toAuditId)
                .orElseThrow(() ->
                        new RuntimeException("To audit log not found"));

        ProvenanceEdge provenanceEdge = new ProvenanceEdge(
                fromAuditLog,
                toAuditLog,
                relationshipType,
                edgeWeight,
                inferenceMethod,
                snapshotId,
                createdAt
        );

        return provenanceEdgeRepository.save(provenanceEdge);
    }

    public List<ProvenanceEdge> getAllProvenanceEdges() {
        return provenanceEdgeRepository.findAll();
    }

    public ProvenanceEdge getProvenanceEdgeById(Long id) {
        return provenanceEdgeRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Provenance edge not found"));
    }

    public void deleteProvenanceEdge(Long id) {

        if (!provenanceEdgeRepository.existsById(id)) {
            throw new RuntimeException("Provenance edge not found");
        }

        provenanceEdgeRepository.deleteById(id);
    }
}