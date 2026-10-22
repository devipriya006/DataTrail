package com.datatrail.backend.service;

import com.datatrail.backend.dto.ProvenanceTraceResponse;
import com.datatrail.backend.dto.AuditLogResponse;
import com.datatrail.backend.dto.ProvenanceCandidateResponse;
import com.datatrail.backend.entity.AuditLog;
import com.datatrail.backend.entity.DatabaseConnection;
import com.datatrail.backend.entity.ProvenanceEdge;
import com.datatrail.backend.repository.AuditLogRepository;
import com.datatrail.backend.repository.ProvenanceEdgeRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
@Service
public class ProvenanceEdgeService {

    private final ProvenanceEdgeRepository provenanceEdgeRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogService auditLogService;
    private final DatabaseConnectionService databaseConnectionService;
        private final ObjectMapper objectMapper = new ObjectMapper();

    public ProvenanceEdgeService(
            ProvenanceEdgeRepository provenanceEdgeRepository,
            AuditLogRepository auditLogRepository,
            AuditLogService auditLogService,
            DatabaseConnectionService databaseConnectionService) {

        this.provenanceEdgeRepository = provenanceEdgeRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditLogService = auditLogService;
        this.databaseConnectionService = databaseConnectionService;
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

        public List<ProvenanceCandidateResponse> discoverCandidates(
                Long projectId,
                Long auditId,
                String ownerUsername) {

            auditLogService.requireProjectAccess(projectId, ownerUsername);
            AuditLog selectedAudit = auditLogRepository.findById(auditId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Audit log not found."));

            DatabaseConnection connection = selectedAudit.getConfiguration()
                    .getConnection();
            if (connection == null || connection.getProject() == null
                    || !projectId.equals(connection.getProject().getProjectId())) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Audit log not found in this project.");
            }
            if (!"PostgreSQL".equalsIgnoreCase(connection.getDbType())) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_IMPLEMENTED,
                        "Provenance candidate discovery currently supports PostgreSQL only.");
            }
            if (connection.getConnectionId() == null
                    || !"CONNECTED".equalsIgnoreCase(connection.getConnectionStatus())) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Test the saved database connection before discovering candidates.");
            }

            String schemaName = selectedAudit.getConfiguration().getSchemaName();
            String tableName = selectedAudit.getConfiguration().getTableName();
            if (schemaName == null || schemaName.isBlank()
                    || tableName == null || tableName.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Audit event schema and table metadata are unavailable.");
            }

            List<String> primaryKeyColumns;
            try (Connection target =
                         databaseConnectionService.openTargetConnection(connection)) {
                primaryKeyColumns = loadPrimaryKeyColumns(
                        target.getMetaData(),
                        target.getCatalog(),
                        schemaName,
                        tableName);
            } catch (SQLException exception) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Unable to discover primary-key metadata for provenance candidates.");
            }
            if (primaryKeyColumns.isEmpty()) {
                return List.of();
            }

            Map<String, JsonNode> selectedIdentity =
                    readPrimaryKeyIdentity(selectedAudit, primaryKeyColumns);
            if (selectedIdentity == null) {
                return List.of();
            }

            Set<AuditPair> recordedPairs = new HashSet<>();
            for (ProvenanceEdge edge : provenanceEdgeRepository.findAllForProject(projectId)) {
                recordedPairs.add(new AuditPair(
                        edge.getFromAuditLog().getAuditId(),
                        edge.getToAuditLog().getAuditId()));
            }

            List<AuditLog> sameTableEvents =
                    auditLogRepository.findForProvenanceCandidates(
                            projectId,
                            connection.getConnectionId(),
                            schemaName,
                            tableName);

            List<ProvenanceCandidateResponse> candidates = new ArrayList<>();
            Set<AuditPair> suggestedPairs = new HashSet<>();
            Comparator<AuditLog> chronological = Comparator
                    .comparing(AuditLog::getEventTimestamp)
                    .thenComparing(AuditLog::getAuditId);

            for (AuditLog event : sameTableEvents) {
                if (event.getAuditId().equals(auditId)) {
                    continue;
                }
                Map<String, JsonNode> eventIdentity =
                        readPrimaryKeyIdentity(event, primaryKeyColumns);
                if (eventIdentity == null || !selectedIdentity.equals(eventIdentity)) {
                    continue;
                }

                AuditLog from = chronological.compare(event, selectedAudit) < 0
                        ? event : selectedAudit;
                AuditLog to = from == event ? selectedAudit : event;
                AuditPair pair = new AuditPair(from.getAuditId(), to.getAuditId());
                AuditPair reversePair = new AuditPair(to.getAuditId(), from.getAuditId());
                if (recordedPairs.contains(pair) || recordedPairs.contains(reversePair)
                        || !suggestedPairs.add(pair)) {
                    continue;
                }

                candidates.add(new ProvenanceCandidateResponse(
                        pair.fromAuditId(),
                        pair.toAuditId(),
                        "Same record identity in " + schemaName + "." + tableName
                                + " by primary-key column(s) "
                                + String.join(", ", primaryKeyColumns)
                                + "; this suggests record continuity, not causation."));
            }

            return candidates;
        }

        private List<String> loadPrimaryKeyColumns(
                DatabaseMetaData metadata,
                String catalog,
                String schema,
                String table) throws SQLException {
            List<PrimaryKeyColumn> keys = new ArrayList<>();
            try (ResultSet result = metadata.getPrimaryKeys(catalog, schema, table)) {
                while (result.next()) {
                    keys.add(new PrimaryKeyColumn(
                            result.getShort("KEY_SEQ"),
                            result.getString("COLUMN_NAME")));
                }
            }
            return keys.stream()
                    .sorted(Comparator.comparingInt(PrimaryKeyColumn::sequence))
                    .map(PrimaryKeyColumn::name)
                    .toList();
        }

        private Map<String, JsonNode> readPrimaryKeyIdentity(
                AuditLog auditLog,
                List<String> primaryKeyColumns) {
            AuditLogResponse snapshot = auditLogService.toAuditLogResponse(auditLog);
            if (snapshot == null) {
                return null;
            }

            Optional<JsonNode> before = parseSnapshot(snapshot.beforeState());
            Optional<JsonNode> after = parseSnapshot(snapshot.afterState());
            if (before.isEmpty() || after.isEmpty()) {
                return null;
            }

            Map<String, JsonNode> identity = new java.util.LinkedHashMap<>();
            for (String column : primaryKeyColumns) {
                JsonNode beforeValue = before.get().get(column);
                JsonNode afterValue = after.get().get(column);
                if (beforeValue != null && !beforeValue.isNull()
                        && afterValue != null && !afterValue.isNull()
                        && !beforeValue.equals(afterValue)) {
                    return null;
                }

                JsonNode value = afterValue != null && !afterValue.isNull()
                        ? afterValue : beforeValue;
                if (value == null || value.isNull() || !value.isValueNode()) {
                    return null;
                }
                identity.put(column, value);
            }
            return identity;
        }

        private record PrimaryKeyColumn(short sequence, String name) {
        }

        private record AuditPair(Long fromAuditId, Long toAuditId) {
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
