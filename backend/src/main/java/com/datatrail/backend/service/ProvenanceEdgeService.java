package com.datatrail.backend.service;

import com.datatrail.backend.dto.ProvenanceTraceResponse;
import com.datatrail.backend.dto.AuditLogResponse;
import com.datatrail.backend.entity.AuditLog;
import com.datatrail.backend.entity.ProvenanceEdge;
import com.datatrail.backend.repository.AuditLogRepository;
import com.datatrail.backend.repository.ProvenanceEdgeRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
@Service
public class ProvenanceEdgeService {

    private final ProvenanceEdgeRepository provenanceEdgeRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogService auditLogService;
        private final ObjectMapper objectMapper = new ObjectMapper();

    public ProvenanceEdgeService(
            ProvenanceEdgeRepository provenanceEdgeRepository,
            AuditLogRepository auditLogRepository,
            AuditLogService auditLogService) {

        this.provenanceEdgeRepository = provenanceEdgeRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditLogService = auditLogService;
    }

    public ProvenanceEdge createProvenanceEdge(
        Long projectId,
        String ownerUsername,
        Long fromAuditId,
        Long toAuditId,
        String relationshipType,
        Double edgeWeight,
        String inferenceMethod,
        Long snapshotId,
        java.time.LocalDateTime createdAt) {

    auditLogService.requireProjectAccess(projectId, ownerUsername);

    AuditLog fromAuditLog = auditLogRepository.findById(fromAuditId)
            .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Source audit log not found"));

    AuditLog toAuditLog = auditLogRepository.findById(toAuditId)
            .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Target audit log not found"));

    Long fromProjectId = fromAuditLog.getConfiguration()
            .getConnection().getProject().getProjectId();

    Long toProjectId = toAuditLog.getConfiguration()
            .getConnection().getProject().getProjectId();

    if (!projectId.equals(fromProjectId)
            || !projectId.equals(toProjectId)) {
        throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Audit logs must belong to the requested project");
    }

    if (fromAuditId.equals(toAuditId)) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Source and target audit logs must be different");
    }

    if (relationshipType == null || relationshipType.isBlank()
            || relationshipType.length() > 50) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "A relationship type of 1–50 characters is required");
    }

    if (inferenceMethod != null && inferenceMethod.length() > 100) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Explanation must not exceed 100 characters");
    }

    ProvenanceEdge edge = new ProvenanceEdge(
            fromAuditLog,
            toAuditLog,
            relationshipType,
            edgeWeight,
            inferenceMethod,
            snapshotId,
            createdAt != null ? createdAt : java.time.LocalDateTime.now()
    );

    return provenanceEdgeRepository.save(edge);
}

   public List<ProvenanceEdge> getAllProvenanceEdges(
        Long projectId,
        String ownerUsername) {

    auditLogService.requireProjectAccess(projectId, ownerUsername);
    return provenanceEdgeRepository.findAllForProject(projectId);
}

public ProvenanceEdge getProvenanceEdgeById(
        Long projectId,
        Long id,
        String ownerUsername) {

    auditLogService.requireProjectAccess(projectId, ownerUsername);
    return provenanceEdgeRepository.findByIdForProject(id, projectId)
            .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Provenance edge not found"));
}

public void deleteProvenanceEdge(
        Long projectId,
        Long id,
        String ownerUsername) {

    auditLogService.requireProjectAccess(projectId, ownerUsername);

    ProvenanceEdge edge = provenanceEdgeRepository
            .findByIdForProject(id, projectId)
            .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Provenance edge not found"));

    provenanceEdgeRepository.delete(edge);
}



        public List<ProvenanceTraceResponse> traceAuditEvent(
                Long projectId,
                Long auditId,
                String ownerUsername) {

            // Confirm the authenticated user owns the requested project.
            auditLogService.requireProjectAccess(projectId, ownerUsername);

            // Confirm the selected audit event exists.
            AuditLog selectedAudit = auditLogRepository.findById(auditId)
                    .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.NOT_FOUND,
                            "Audit log not found."));

            // Do not allow an audit ID from another project to be traced.
            Long selectedProjectId = selectedAudit.getConfiguration()
                    .getConnection()
                    .getProject()
                    .getProjectId();

            if (!projectId.equals(selectedProjectId)) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "Audit log not found in this project.");
            }

            return provenanceEdgeRepository
                    .findConnectedEdgesForProject(projectId, auditId)
                    .stream()
                    .map(edge -> {
                        AuditLog from = edge.getFromAuditLog();
                        AuditLog to = edge.getToAuditLog();
                        AuditLogResponse fromSnapshot =
                                auditLogService.toAuditLogResponse(from);
                        AuditLogResponse toSnapshot =
                                auditLogService.toAuditLogResponse(to);
                        ChangedFields fromChanges =
                                summarizeChangedFields(fromSnapshot);
                        ChangedFields toChanges =
                                summarizeChangedFields(toSnapshot);

                        return new ProvenanceTraceResponse(
                                edge.getEdgeId(),
                                from.getAuditId(),
                                to.getAuditId(),
                                edge.getRelationshipType(),
                                edge.getEdgeWeight(),
                                edge.getInferenceMethod(),
                                edge.getSnapshotId(),
                                edge.getCreatedAt(),
                                from.getOperation(),
                                from.getConfiguration().getSchemaName(),
                                from.getConfiguration().getTableName(),
                                from.getEventTimestamp(),
                                to.getOperation(),
                                to.getConfiguration().getSchemaName(),
                                to.getConfiguration().getTableName(),
                                                                to.getEventTimestamp(),
                                                                fromChanges.names(),
                                                                fromChanges.count(),
                                                                toChanges.names(),
                                                                toChanges.count(),
                                                                fromSnapshot != null
                                                                                ? fromSnapshot.beforeState()
                                                                                : null,
                                                                fromSnapshot != null
                                                                                ? fromSnapshot.afterState()
                                                                                : null,
                                                                toSnapshot != null
                                                                                ? toSnapshot.beforeState()
                                                                                : null,
                                                                toSnapshot != null
                                                                                ? toSnapshot.afterState()
                                                                                : null);
                    })
                    .toList();
        }

        private ChangedFields summarizeChangedFields(AuditLogResponse sanitized) {
                if (sanitized == null) {
                        return ChangedFields.unavailable();
                }

                boolean beforeMissing = isBlank(sanitized.beforeState());
                boolean afterMissing = isBlank(sanitized.afterState());
                if ((beforeMissing && afterMissing)
                                || (beforeMissing && !"INSERT".equalsIgnoreCase(sanitized.operation()))
                                || (afterMissing && !"DELETE".equalsIgnoreCase(sanitized.operation()))) {
                        return ChangedFields.unavailable();
                }

                Optional<JsonNode> before = parseSnapshot(sanitized.beforeState());
                Optional<JsonNode> after = parseSnapshot(sanitized.afterState());
                if (before.isEmpty() || after.isEmpty()) {
                        return ChangedFields.unavailable();
                }

                TreeSet<String> fieldNames = new TreeSet<>();
                before.get().fieldNames().forEachRemaining(fieldNames::add);
                after.get().fieldNames().forEachRemaining(fieldNames::add);

                List<String> changedFields = fieldNames.stream()
                                .filter(field -> !Objects.equals(
                                                before.get().get(field), after.get().get(field)))
                                .toList();

                return new ChangedFields(changedFields, changedFields.size());
        }

        private Optional<JsonNode> parseSnapshot(String snapshot) {
                if (isBlank(snapshot)) {
                        return Optional.of(objectMapper.createObjectNode());
                }

                try {
                        JsonNode parsed = objectMapper.readTree(snapshot);
                        return parsed != null && parsed.isObject()
                                        ? Optional.of(parsed)
                                        : Optional.empty();
                } catch (JsonProcessingException exception) {
                        return Optional.empty();
                }
        }

        private boolean isBlank(String value) {
                return value == null || value.isBlank();
        }

        private record ChangedFields(List<String> names, Integer count) {
                private static ChangedFields unavailable() {
                        return new ChangedFields(List.of(), null);
                }
        }

}
