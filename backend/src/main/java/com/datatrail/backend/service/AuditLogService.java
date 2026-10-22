package com.datatrail.backend.service;

import com.datatrail.backend.dto.AuditLogResponse;
import com.datatrail.backend.dto.AuditEventIngestion;
import com.datatrail.backend.entity.AuditLog;
import com.datatrail.backend.entity.DatabaseConnection;
import com.datatrail.backend.entity.MonitoringConfiguration;
import com.datatrail.backend.entity.Project;
import com.datatrail.backend.repository.AuditLogRepository;
import com.datatrail.backend.repository.DatabaseConnectionRepository;
import com.datatrail.backend.repository.MonitoringConfigurationRepository;
import com.datatrail.backend.repository.ProjectRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class AuditLogService {

    private static final Set<String> SENSITIVE_FIELD_NAMES = Set.of(
            "password",
            "password_hash",
            "passwordhash",
            "encrypted_password",
            "encryptedpassword",
            "jwt",
            "token",
            "database_password",
            "databasepassword",
            "db_password",
            "dbpassword",
            "secret",
            "credentials",
            "api_key",
            "apikey",
            "access_token",
            "accesstoken",
            "refresh_token",
            "refreshtoken"
    );

    private final AuditLogRepository auditLogRepository;
    private final MonitoringConfigurationRepository monitoringConfigurationRepository;
    private final ProjectRepository projectRepository;
    private final DatabaseConnectionRepository databaseConnectionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuditLogService(
            AuditLogRepository auditLogRepository,
            MonitoringConfigurationRepository monitoringConfigurationRepository,
            ProjectRepository projectRepository,
            DatabaseConnectionRepository databaseConnectionRepository) {

        this.auditLogRepository = auditLogRepository;
        this.monitoringConfigurationRepository =
                monitoringConfigurationRepository;
        this.projectRepository = projectRepository;
        this.databaseConnectionRepository =
                databaseConnectionRepository;
    }

    public AuditLog createAuditLog(
            Long configId,
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

        MonitoringConfiguration configuration =
                monitoringConfigurationRepository.findById(configId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Monitoring configuration not found."
                                ));

        AuditLog auditLog = new AuditLog(
                configuration,
                operation,
                transactionId,
                beforeState,
                afterState,
                changedBy,
                databaseUser,
                actorSource,
                eventTimestamp,
                logHash,
                prevHash,
                chainSequence
        );

        return auditLogRepository.save(auditLog);
    }

    @Transactional
    public AuditLog ingestEvent(Long configId, AuditEventIngestion event) {
        validateIngestionEvent(event);

        MonitoringConfiguration configuration =
                monitoringConfigurationRepository.findByIdForAuditIngestion(configId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Monitoring configuration not found."));
        String operation = event.operation().trim().toUpperCase(Locale.ROOT);
        validateEventFieldLengths(event);

        AuditLog existing = auditLogRepository
                .findByConfigurationConfigIdAndSourceEventId(
                        configId,
                        event.sourceEventId())
                .orElse(null);
        if (existing != null) {
            if (!sameEvent(existing, event, operation)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "The source event ID has already been ingested with different event data.");
            }
            return existing;
        }

        validateSnapshots(event, operation);
        List<AuditLog> chain = auditLogRepository.findByConfigurationConfigId(configId)
                .stream()
                .sorted(Comparator.comparing(
                        AuditLog::getChainSequence,
                        Comparator.nullsLast(Long::compareTo)))
                .toList();
        String previousHash = null;
        long sequence = 1L;
        for (AuditLog previous : chain) {
            if (previous.getChainSequence() == null
                    || previous.getChainSequence() < 1
                    || !Objects.equals(previous.getPrevHash(), previousHash)
                    || !Objects.equals(previous.getLogHash(), computeHash(previous, previousHash))) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "The existing audit hash chain is invalid; the event was not ingested.");
            }
            if (previous.getChainSequence() == Long.MAX_VALUE) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "The audit chain has reached its maximum sequence.");
            }
            sequence = previous.getChainSequence() + 1;
            previousHash = previous.getLogHash();
        }

        AuditLog auditLog = new AuditLog(
                configuration,
                operation,
                event.transactionId(),
                event.beforeState(),
                event.afterState(),
                event.changedBy(),
                event.databaseUser(),
                event.actorSource(),
                event.eventTimestamp(),
                null,
                previousHash,
                sequence);
        auditLog.setSourceEventId(event.sourceEventId());
        auditLog.setLogHash(computeHash(auditLog, previousHash));

        return auditLogRepository.saveAndFlush(auditLog);
    }

    private void validateIngestionEvent(AuditEventIngestion event) {
        if (event == null
                || event.sourceEventId() == null
                || event.sourceEventId().isBlank()
                || event.operation() == null
                || event.eventTimestamp() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Source event ID, operation, and event timestamp are required.");
        }
        String operation = event.operation().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("INSERT", "UPDATE", "DELETE").contains(operation)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Audit operation must be INSERT, UPDATE, or DELETE.");
        }
    }

    private void validateEventFieldLengths(AuditEventIngestion event) {
        requireMaxLength(event.sourceEventId(), 255, "Source event ID");
        requireMaxLength(event.transactionId(), 100, "Transaction ID");
        requireMaxLength(event.changedBy(), 100, "Changed by");
        requireMaxLength(event.databaseUser(), 100, "Database user");
        requireMaxLength(event.actorSource(), 30, "Actor source");
    }

    private void requireMaxLength(String value, int maxLength, String field) {
        if (value != null && value.length() > maxLength) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    field + " exceeds the maximum length of " + maxLength + ".");
        }
    }

    private void validateSnapshots(AuditEventIngestion event, String operation) {
        boolean validShape = switch (operation) {
            case "INSERT" -> event.beforeState() == null && event.afterState() != null;
            case "UPDATE" -> event.beforeState() != null && event.afterState() != null;
            case "DELETE" -> event.beforeState() != null && event.afterState() == null;
            default -> false;
        };
        if (!validShape
                || !isJsonObject(event.beforeState())
                || !isJsonObject(event.afterState())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Audit snapshots do not match the event operation.");
        }
    }

    private boolean isJsonObject(String state) {
        if (state == null) {
            return true;
        }
        try {
            return objectMapper.readTree(state).isObject();
        } catch (JsonProcessingException exception) {
            return false;
        }
    }

    private boolean sameEvent(
            AuditLog existing,
            AuditEventIngestion event,
            String normalizedOperation) {
        return Objects.equals(existing.getOperation(), normalizedOperation)
                && Objects.equals(existing.getTransactionId(), event.transactionId())
                && Objects.equals(existing.getBeforeState(), event.beforeState())
                && Objects.equals(existing.getAfterState(), event.afterState())
                && Objects.equals(existing.getChangedBy(), event.changedBy())
                && Objects.equals(existing.getDatabaseUser(), event.databaseUser())
                && Objects.equals(existing.getActorSource(), event.actorSource())
                && Objects.equals(existing.getEventTimestamp(), event.eventTimestamp());
    }

    public List<AuditLogResponse> getAllAuditLogsForOwner(
            String ownerUsername) {

        return auditLogRepository
                .findByConfigurationConnectionProjectOwnerUsername(
                        ownerUsername
                )
                .stream()
                .sorted(
                        Comparator
                                .comparing(
                                        AuditLog::getEventTimestamp,
                                        Comparator.nullsLast(
                                                Comparator.reverseOrder()
                                        )
                                )
                                .thenComparing(
                                        AuditLog::getAuditId,
                                        Comparator.reverseOrder()
                                )
                )
                .map(this::toAuditLogResponse)
                .toList();
    }

    public AuditLogResponse getAuditLogById(
            Long id,
            String ownerUsername) {

        AuditLog auditLog =
                auditLogRepository.findById(id)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Audit log not found."
                                ));

        requireProjectAccess(
                auditLog
                        .getConfiguration()
                        .getConnection()
                        .getProject()
                        .getProjectId(),
                ownerUsername
        );

        return toAuditLogResponse(auditLog);
    }

    public List<AuditLogResponse> getAuditLogsByProject(
            Long projectId,
            String ownerUsername,
            String operation,
            String table,
            String changedBy,
            LocalDateTime dateFrom,
            LocalDateTime dateTo) {

        requireProjectAccess(projectId, ownerUsername);

        List<AuditLog> logs =
                auditLogRepository
                        .findByConfigurationConnectionProjectProjectId(
                                projectId
                        );

        return applyFilters(
                logs,
                operation,
                table,
                changedBy,
                dateFrom,
                dateTo
        );
    }

    public List<AuditLogResponse> getAuditLogsByConnection(
            Long connectionId,
            Long projectId,
            String ownerUsername,
            String operation,
            String table,
            String changedBy,
            LocalDateTime dateFrom,
            LocalDateTime dateTo) {

        requireOwnedConnection(
                connectionId,
                projectId,
                ownerUsername
        );

        List<AuditLog> logs =
                auditLogRepository
                        .findByConfigurationConnectionConnectionId(
                                connectionId
                        );

        return applyFilters(
                logs,
                operation,
                table,
                changedBy,
                dateFrom,
                dateTo
        );
    }

    public List<AuditLogResponse> getAuditLogsByConfiguration(
            Long configId,
            Long projectId,
            String ownerUsername,
            String operation,
            String table,
            String changedBy,
            LocalDateTime dateFrom,
            LocalDateTime dateTo) {

        requireOwnedConfiguration(
                configId,
                projectId,
                ownerUsername
        );

        List<AuditLog> logs =
                auditLogRepository.findByConfigurationConfigId(
                        configId
                );

        return applyFilters(
                logs,
                operation,
                table,
                changedBy,
                dateFrom,
                dateTo
        );
    }

    public Map<String, Object> verifyHashChain(
            Long configId,
            Long projectId,
            String ownerUsername) {

        MonitoringConfiguration configuration =
                requireOwnedConfiguration(
                        configId,
                        projectId,
                        ownerUsername
                );

        List<AuditLog> logs =
                auditLogRepository
                        .findByConfigurationConfigId(configId)
                        .stream()
                        .sorted(
                                Comparator.comparing(
                                        AuditLog::getChainSequence,
                                        Comparator.nullsLast(
                                                Long::compareTo
                                        )
                                )
                        )
                        .toList();

        String previousHash = null;

        boolean valid = true;

        int checkedEvents = 0;

        List<String> warnings = new ArrayList<>();

        for (AuditLog log : logs) {

            checkedEvents++;

            /*
             * The Java calculation MUST match the PostgreSQL trigger
             * exactly.
             */
            String expectedHash =
                    computeHash(
                            log,
                            previousHash
                    );

            if (!Objects.equals(
                    log.getLogHash(),
                    expectedHash
            )) {
                valid = false;

                warnings.add(
                        "Hash mismatch at auditId="
                                + log.getAuditId()
                );
            }

            if (!Objects.equals(
                    log.getPrevHash(),
                    previousHash
            )) {
                valid = false;

                warnings.add(
                        "Previous hash mismatch at auditId="
                                + log.getAuditId()
                );
            }

            previousHash = log.getLogHash();
        }

        return Map.of(
                "configId",
                configId,

                "projectId",
                projectId,

                "table",
                configuration.getSchemaName()
                        + "."
                        + configuration.getTableName(),

                "valid",
                valid,

                "checkedEvents",
                checkedEvents,

                "warnings",
                warnings
        );
    }

    public void deleteAuditLog(
            Long id,
            String ownerUsername) {

        AuditLog auditLog =
                auditLogRepository.findById(id)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Audit log not found."
                                ));

        requireProjectAccess(
                auditLog
                        .getConfiguration()
                        .getConnection()
                        .getProject()
                        .getProjectId(),
                ownerUsername
        );

        auditLogRepository.delete(auditLog);
    }

    public AuditLogResponse toAuditLogResponse(
            AuditLog auditLog) {

        if (auditLog == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Audit log is required."
            );
        }

        MonitoringConfiguration configuration =
                auditLog.getConfiguration();

        return new AuditLogResponse(
                auditLog.getAuditId(),

                configuration != null
                        ? configuration.getConfigId()
                        : null,

                configuration != null
                        ? configuration.getSchemaName()
                        : null,

                configuration != null
                        ? configuration.getTableName()
                        : null,

                auditLog.getOperation(),

                auditLog.getTransactionId(),

                sanitizeState(
                        auditLog.getBeforeState()
                ),

                sanitizeState(
                        auditLog.getAfterState()
                ),

                auditLog.getChangedBy(),

                auditLog.getDatabaseUser(),

                auditLog.getActorSource(),

                auditLog.getEventTimestamp(),

                auditLog.getLogHash(),

                auditLog.getPrevHash(),

                auditLog.getChainSequence()
        );
    }

    public void requireProjectAccess(
            Long projectId,
            String ownerUsername) {

        Project project =
                projectRepository.findById(projectId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Project not found."
                                ));

        if (
                project.getOwner() == null
                        || project.getOwner().getUsername() == null
                        || !project
                        .getOwner()
                        .getUsername()
                        .equals(ownerUsername)
        ) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have access to this project's audit logs."
            );
        }
    }

    private MonitoringConfiguration requireOwnedConfiguration(
            Long configId,
            Long projectId,
            String ownerUsername) {

        MonitoringConfiguration configuration =
                monitoringConfigurationRepository.findById(configId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Monitoring configuration not found."
                                ));

        DatabaseConnection connection =
                configuration.getConnection();

        if (
                connection == null
                        || connection.getProject() == null
        ) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Monitoring configuration is not linked to a project."
            );
        }

        if (
                !Objects.equals(
                        connection.getProject().getProjectId(),
                        projectId
                )
        ) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Configuration does not belong to this project."
            );
        }

        requireProjectAccess(
                projectId,
                ownerUsername
        );

        return configuration;
    }

    private DatabaseConnection requireOwnedConnection(
            Long connectionId,
            Long projectId,
            String ownerUsername) {

        DatabaseConnection connection =
                databaseConnectionRepository.findById(connectionId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Database connection not found."
                                ));

        if (
                connection.getProject() == null
                        || !Objects.equals(
                                connection
                                        .getProject()
                                        .getProjectId(),
                                projectId
                        )
        ) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Connection does not belong to this project."
            );
        }

        requireProjectAccess(
                projectId,
                ownerUsername
        );

        return connection;
    }

    private List<AuditLogResponse> applyFilters(
            List<AuditLog> logs,
            String operation,
            String table,
            String changedBy,
            LocalDateTime dateFrom,
            LocalDateTime dateTo) {

        List<AuditLog> filtered =
                logs.stream()
                        .filter(log ->
                                operation == null
                                        || operation.isBlank()
                                        || operation.equalsIgnoreCase(
                                                log.getOperation()
                                        )
                        )
                        .filter(log ->
                                table == null
                                        || table.isBlank()
                                        || table.equalsIgnoreCase(
                                                log.getConfiguration() == null
                                                        ? ""
                                                        : log
                                                        .getConfiguration()
                                                        .getTableName()
                                        )
                        )
                        .filter(log ->
                                changedBy == null
                                        || changedBy.isBlank()
                                        || (
                                        log.getChangedBy() != null
                                                && log
                                                .getChangedBy()
                                                .toLowerCase()
                                                .contains(
                                                        changedBy
                                                                .toLowerCase()
                                                )
                                )
                        )
                        .filter(log ->
                                dateFrom == null
                                        || log.getEventTimestamp() == null
                                        || !log
                                        .getEventTimestamp()
                                        .isBefore(dateFrom)
                        )
                        .filter(log ->
                                dateTo == null
                                        || log.getEventTimestamp() == null
                                        || !log
                                        .getEventTimestamp()
                                        .isAfter(dateTo)
                        )
                        .sorted(
                                Comparator
                                        .comparing(
                                                AuditLog::getEventTimestamp,
                                                Comparator.nullsLast(
                                                        Comparator.reverseOrder()
                                                )
                                        )
                                        .thenComparing(
                                                AuditLog::getAuditId,
                                                Comparator.reverseOrder()
                                        )
                        )
                        .toList();

        return filtered
                .stream()
                .map(this::toAuditLogResponse)
                .toList();
    }

    private String sanitizeState(String state) {

        if (state == null || state.isBlank()) {
            return state;
        }

        try {
            JsonNode root =
                    objectMapper.readTree(state);

            sanitizeNode(root);

            return objectMapper.writeValueAsString(root);

        } catch (JsonProcessingException ignored) {
            return state;
        }
    }

    private void sanitizeNode(JsonNode node) {

        if (node == null) {
            return;
        }

        if (node.isObject()) {

            com.fasterxml.jackson.databind.node.ObjectNode objectNode =
                    (com.fasterxml.jackson.databind.node.ObjectNode) node;

            Set<String> keys = new LinkedHashSet<>();

            objectNode
                    .fieldNames()
                    .forEachRemaining(keys::add);

            for (String key : keys) {

                if (isSensitiveKey(key)) {
                    objectNode.remove(key);
                    continue;
                }

                sanitizeNode(
                        objectNode.get(key)
                );
            }

            return;
        }

        if (node.isArray()) {

            for (JsonNode child : node) {
                sanitizeNode(child);
            }
        }
    }

    private boolean isSensitiveKey(String key) {

        if (key == null) {
            return false;
        }

        String normalized =
                key.trim().toLowerCase();

        return SENSITIVE_FIELD_NAMES.contains(
                normalized.replace("-", "_")
        );
    }

    /**
     * Must match the PostgreSQL trigger exactly.
     *
     * PostgreSQL trigger payload:
     *
     * prev_hash
     * |
     * sequence
     * |
     * operation
     * |
     * transaction_id
     * |
     * before_state
     * |
     * after_state
     * |
     * changed_by
     * |
     * database_user
     * |
     * actor_source
     * |
     * event_timestamp
     */
    private String computeHash(
            AuditLog log,
            String previousHash) {

        String payload =
                String.join(
                        "|",

                        Objects.toString(
                                previousHash,
                                ""
                        ),

                        Objects.toString(
                                log.getChainSequence(),
                                ""
                        ),

                        Objects.toString(
                                log.getOperation(),
                                ""
                        ),

                        Objects.toString(
                                log.getTransactionId(),
                                ""
                        ),

                        Objects.toString(
                                log.getBeforeState(),
                                ""
                        ),

                        Objects.toString(
                                log.getAfterState(),
                                ""
                        ),

                        Objects.toString(
                                log.getChangedBy(),
                                ""
                        ),

                        Objects.toString(
                                log.getDatabaseUser(),
                                ""
                        ),

                        Objects.toString(
                                log.getActorSource(),
                                ""
                        ),

                        Objects.toString(
                                log.getEventTimestamp(),
                                ""
                        )
                );

        try {

            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash =
                    digest.digest(
                            payload.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            StringBuilder builder =
                    new StringBuilder();

            for (byte value : hash) {
                builder.append(
                        String.format(
                                "%02x",
                                value
                        )
                );
            }

            return builder.toString();

        } catch (NoSuchAlgorithmException exception) {

            throw new IllegalStateException(
                    "SHA-256 is not available on this JVM.",
                    exception
            );
        }
    }
}