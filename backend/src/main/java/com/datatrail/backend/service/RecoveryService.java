package com.datatrail.backend.service;

import com.datatrail.backend.dto.RecoveryPreviewResponse;
import com.datatrail.backend.entity.AuditLog;
import com.datatrail.backend.entity.DatabaseConnection;
import com.datatrail.backend.entity.MonitoringConfiguration;
import com.datatrail.backend.entity.Project;
import com.datatrail.backend.entity.RecoveryAction;
import com.datatrail.backend.entity.RecoveryExecution;
import com.datatrail.backend.entity.User;
import com.datatrail.backend.repository.AuditLogRepository;
import com.datatrail.backend.repository.ProjectRepository;
import com.datatrail.backend.repository.RecoveryActionRepository;
import com.datatrail.backend.repository.RecoveryExecutionRepository;
import com.datatrail.backend.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;


import java.sql.ResultSetMetaData;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class RecoveryService {

    private static final Logger log = LoggerFactory.getLogger(RecoveryService.class);
    private static final String NORMAL_RECOVERY = "UPDATE_RESTORE";
    private static final String ADMIN_RECOVERY = "ADMIN_RESTORE_OVERRIDE";
    private static final String ADMIN_CONFIRMATION = "RESTORE ANYWAY";
    private static final Set<String> SENSITIVE_NAMES = Set.of(
            "password", "password_hash", "encrypted_password", "token", "jwt", "secret",
            "credential", "api_key", "access_token", "refresh_token");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AuditLogRepository auditLogRepository;
    private final ProjectRepository projectRepository;
    private final RecoveryActionRepository recoveryActionRepository;
    private final RecoveryExecutionRepository recoveryExecutionRepository;
    private final UserRepository userRepository;
    private final DatabaseConnectionService databaseConnectionService;
    private final TransactionTemplate transactionTemplate;

    public RecoveryService(
            AuditLogRepository auditLogRepository,
            ProjectRepository projectRepository,
            RecoveryActionRepository recoveryActionRepository,
            RecoveryExecutionRepository recoveryExecutionRepository,
            UserRepository userRepository,
            DatabaseConnectionService databaseConnectionService,
            PlatformTransactionManager transactionManager) {
        this.auditLogRepository = auditLogRepository;
        this.projectRepository = projectRepository;
        this.recoveryActionRepository = recoveryActionRepository;
        this.recoveryExecutionRepository = recoveryExecutionRepository;
        this.userRepository = userRepository;
        this.databaseConnectionService = databaseConnectionService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public RecoveryPreviewResponse previewRecovery(Long auditId, String ownerUsername) {
        AuditLog auditLog = loadAuditLog(auditId);
        requireProjectAccess(auditLog, ownerUsername);
        requireUpdateAudit(auditLog);

        try (Connection target = openTarget(auditLog)) {
            Evaluation evaluation = inspect(target, auditLog, false);
            String status = evaluation.drift
                    ? "Recovery blocked because audited fields differ from the current record."
                    : "READY TO REVIEW";
            return new RecoveryPreviewResponse(
                    auditId,
                    evaluation.schemaName + "." + evaluation.tableName,
                    evaluation.primaryKey + " = " + evaluation.primaryKeyValue,
                    visibleState(evaluation.currentState),
                    visibleState(evaluation.beforeState),
                    evaluation.changedFields,
                    !evaluation.drift,
                    evaluation.drift,
                    status,
                    1.0,
                    null,
                    null);
        } catch (SQLException exception) {
            log.warn("Recovery preview database failure: auditId={}, sqlState={}", auditId, exception.getSQLState());
            throw targetUnavailable();
        }
    }

    @Transactional
    public RecoveryAction approveRecovery(Long auditId, String ownerUsername) {
        AuditLog auditLog = loadAuditLog(auditId);
        requireProjectAccess(auditLog, ownerUsername);
        requireUpdateAudit(auditLog);
        RecoveryPreviewResponse preview = previewRecovery(auditId, ownerUsername);
        if (!preview.allowed()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, preview.message());
        }

        User approver = requireUser(ownerUsername);
        LocalDateTime now = LocalDateTime.now();
        RecoveryAction existing = recoveryActionRepository
                .findFirstByAuditLogAuditIdAndRecoveryTypeOrderByRecoveryIdDesc(auditId, NORMAL_RECOVERY)
                .orElse(null);

        if (existing != null) {
            String status = existing.getApprovalStatus();
            if ("APPROVED".equalsIgnoreCase(status)) {
                return existing;
            }
            if ("EXECUTED".equalsIgnoreCase(status)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This recovery action has already been executed.");
            }
            if ("PENDING".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status)) {
                if (recoveryExecutionRepository.hasSuccessfulExecution(existing.getRecoveryId())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "A previous recovery execution already succeeded for this action.");
                }
                existing.setApprovalStatus("APPROVED");
                existing.setApprovedBy(approver);
                existing.setApprovedAt(now);
                return recoveryActionRepository.save(existing);
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Recovery action status '" + status + "' cannot be approved.");
        }

        return recoveryActionRepository.save(new RecoveryAction(
                null,
                auditLog,
                "Restore changed fields from audit event " + auditId,
                NORMAL_RECOVERY,
                "APPROVED",
                approver,
                now,
                now));
    }

    /**
     * Target restore uses a separate JDBC connection to the same PostgreSQL database that stores
     * DataTrail metadata. Running it inside the JPA transaction that inserted {@code recovery_execution}
     * (FK lock on {@code users}) deadlocks with {@code SELECT ... FROM users ... FOR UPDATE} on the
     * recovered row when the executor is that same user. Commit the RUNNING row first, then restore.
     */
    public RecoveryExecution executeRecovery(Long recoveryId, String ownerUsername) {
        StartedExecution started = transactionTemplate.execute(status -> startApprovedExecution(recoveryId, ownerUsername));
        if (started == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to start recovery execution.");
        }

        log.info("Recovery execution started: recoveryId={}, executionId={}, auditId={}",
                started.recoveryId(), started.executionId(), started.auditId());

        try {
            long auditEventId = restoreTarget(started.auditLog(), ownerUsername, false);
            RecoveryExecution completed = transactionTemplate.execute(status ->
                    completeSuccessfulExecution(started.executionId(), started.recoveryId(), auditEventId));
            if (completed == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to record recovery success.");
            }
            log.info("Recovery execution succeeded: recoveryId={}, executionId={}, triggerAuditId={}",
                    started.recoveryId(), started.executionId(), auditEventId);
            return completed;
        } catch (Exception exception) {
            transactionTemplate.executeWithoutResult(status ->
                    markFailedExecution(started.executionId(), started.recoveryId(), exception));
            log.warn("Recovery execution failed: recoveryId={}, executionId={}, status={}",
                    started.recoveryId(),
                    started.executionId(),
                    exception instanceof ResponseStatusException responseStatusException
                            ? responseStatusException.getStatusCode().value()
                            : "error");
            if (exception instanceof ResponseStatusException responseStatusException) {
                throw responseStatusException;
            }
            throw targetUnavailable();
        }
    }

    private StartedExecution startApprovedExecution(Long recoveryId, String ownerUsername) {
        RecoveryAction action = recoveryActionRepository.findByIdForUpdate(recoveryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recovery action not found."));
        if (!NORMAL_RECOVERY.equals(action.getRecoveryType())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin overrides must use the separate override endpoint.");
        }
        if (!"APPROVED".equalsIgnoreCase(action.getApprovalStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Recovery action must be approved before execution.");
        }
        if (recoveryExecutionRepository.hasSuccessfulExecution(recoveryId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This recovery action already has a successful execution.");
        }
        for (RecoveryExecution stale : recoveryExecutionRepository.findRunningExecutions(recoveryId)) {
            stale.setExecutionStatus("FAILED");
            stale.setCompletedAt(LocalDateTime.now());
            stale.setDryRunResult("Previous execution was interrupted before a final status was recorded.");
            recoveryExecutionRepository.save(stale);
        }

        AuditLog auditLog = action.getAuditLog();
        if (auditLog == null || auditLog.getAuditId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recovery action is not linked to an audit event.");
        }
        // Re-load outside the locked graph so configuration/connection are available after commit.
        AuditLog persistentAudit = loadAuditLog(auditLog.getAuditId());
        requireUpdateAudit(persistentAudit);
        requireProjectAccess(persistentAudit, ownerUsername);
        User executor = requireUser(ownerUsername);
        RecoveryExecution execution = recoveryExecutionRepository.saveAndFlush(new RecoveryExecution(
                action, "UPDATE_RESTORE", "Recovery is running.", 1.0, "RUNNING", LocalDateTime.now(), null, executor));
        return new StartedExecution(
                recoveryId,
                execution.getExecutionId(),
                persistentAudit.getAuditId(),
                persistentAudit);
    }

    private RecoveryExecution completeSuccessfulExecution(Long executionId, Long recoveryId, long auditEventId) {
        RecoveryExecution execution = recoveryExecutionRepository.findById(executionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Recovery execution disappeared."));
        RecoveryAction action = recoveryActionRepository.findByIdForUpdate(recoveryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recovery action not found."));
        execution.setAuditEventId(auditEventId);
        execution.setExecutionStatus("SUCCESS");
        execution.setCompletedAt(LocalDateTime.now());
        execution.setDryRunResult("Restored the audited fields. PostgreSQL trigger audit event #" + auditEventId + " was verified.");
        action.setApprovalStatus("EXECUTED");
        recoveryActionRepository.save(action);
        return recoveryExecutionRepository.save(execution);
    }

    private void markFailedExecution(Long executionId, Long recoveryId, Exception exception) {
        recoveryExecutionRepository.findById(executionId).ifPresent(execution -> {
            execution.setExecutionStatus("FAILED");
            execution.setCompletedAt(LocalDateTime.now());
            execution.setDryRunResult(safeFailureMessage(exception));
            recoveryExecutionRepository.save(execution);
        });
        recoveryActionRepository.findByIdForUpdate(recoveryId).ifPresent(action -> {
            if (!"EXECUTED".equalsIgnoreCase(action.getApprovalStatus())) {
                action.setApprovalStatus("FAILED");
                recoveryActionRepository.save(action);
            }
        });
    }

    public RecoveryExecution restoreAnyway(
            Long auditId,
            String adminUsername,
            String reason,
            String confirmation) {

        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An administrator reason is required.");
        }
        if (reason.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The administrator reason is too long.");
        }
        if (!ADMIN_CONFIRMATION.equals(confirmation)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type RESTORE ANYWAY to confirm this override.");
        }

        User admin = requireAdmin(adminUsername);
        AuditLog auditLog = loadAuditLog(auditId);
        requireProjectAccess(auditLog, adminUsername);
        RecoveryPreviewResponse preview = previewRecovery(auditId, adminUsername);
        if (!preview.overrideAllowed()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This recovery is not eligible for drift override.");
        }

        AdminAttempt attempt = transactionTemplate.execute(status -> {
            AuditLog lockedAudit = auditLogRepository.findByIdForRecoveryUpdate(auditId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Audit log not found."));
            requireProjectAccess(lockedAudit, adminUsername);
            recoveryActionRepository
                    .findFirstByAuditLogAuditIdAndRecoveryTypeOrderByRecoveryIdDesc(auditId, ADMIN_RECOVERY)
                    .filter(existing -> "RUNNING".equalsIgnoreCase(existing.getOverrideDecision())
                            || "RESTORED".equalsIgnoreCase(existing.getOverrideDecision()))
                    .ifPresent(existing -> {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "An override for this audit event is already complete or running.");
                    });

            LocalDateTime now = LocalDateTime.now();
            RecoveryAction action = new RecoveryAction(
                    null,
                    lockedAudit,
                    "Administrator-authorized restore of audit event " + auditId,
                    ADMIN_RECOVERY,
                    "RUNNING",
                    admin,
                    now,
                    now);
            action.setOverrideBy(admin);
            action.setOverrideReason(reason.trim());
            action.setOverrideAt(now);
            action.setOverrideDecision("RUNNING");
            action = recoveryActionRepository.saveAndFlush(action);

            RecoveryExecution execution = recoveryExecutionRepository.saveAndFlush(new RecoveryExecution(
                    action,
                    "ADMIN_RESTORE_OVERRIDE",
                    "Administrator override is running.",
                    1.0,
                    "RUNNING",
                    now,
                    null,
                    admin));
            return new AdminAttempt(action, execution);
        });

        if (attempt == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to record the administrator decision.");
        }

        try {
            long auditEventId = restoreTarget(auditLog, adminUsername, true);
            RecoveryAction action = attempt.action;
            action.setApprovalStatus("EXECUTED");
            action.setOverrideDecision("RESTORED");
            recoveryActionRepository.save(action);

            RecoveryExecution execution = attempt.execution;
            execution.setAuditEventId(auditEventId);
            execution.setExecutionStatus("SUCCESS");
            execution.setCompletedAt(LocalDateTime.now());
            execution.setDryRunResult("Administrator override restored the audited fields. PostgreSQL trigger audit event #" + auditEventId + " was verified.");
            return recoveryExecutionRepository.save(execution);
        } catch (Exception exception) {
            attempt.action.setApprovalStatus("FAILED");
            attempt.action.setOverrideDecision("FAILED");
            recoveryActionRepository.save(attempt.action);
            attempt.execution.setExecutionStatus("FAILED");
            attempt.execution.setCompletedAt(LocalDateTime.now());
            attempt.execution.setDryRunResult(safeFailureMessage(exception));
            recoveryExecutionRepository.save(attempt.execution);
            if (exception instanceof ResponseStatusException responseStatusException) {
                throw responseStatusException;
            }
            throw targetUnavailable();
        }
    }

    private long restoreTarget(AuditLog auditLog, String actor, boolean allowDrift) {
        DatabaseConnection savedConnection = auditLog.getConfiguration().getConnection();
        try (Connection target = openTarget(auditLog)) {
            target.setAutoCommit(false);
            try {
                acquireAuditChainLock(target, auditLog.getConfiguration().getConfigId());
                Evaluation evaluation = inspect(target, auditLog, true);
                if (evaluation.drift && !allowDrift) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Recovery blocked because audited fields differ from the current record.");
                }
                if (allowDrift && !evaluation.drift) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "The record no longer requires an administrator drift override.");
                }

                
                
                String transactionId = currentTransactionId(target);
                setActor(target, actor);
                executeRestoreUpdate(target, evaluation);

                long outboxEventId = verifyTriggerOutbox(
                        target, auditLog, evaluation, transactionId);

                target.commit();
                return outboxEventId;

            } catch (Exception exception) {
                try {
                    target.rollback();
                } catch (SQLException rollbackFailure) {
                    log.error("Recovery rollback failed for auditId={}", auditLog.getAuditId());
                }
                if (exception instanceof ResponseStatusException responseStatusException) {
                    throw responseStatusException;
                }
                
if (exception instanceof SQLException sqlException) {
    log.error(
            "Target recovery SQL failure: auditId={}, sqlState={}, message={}",
            auditLog.getAuditId(),
            sqlException.getSQLState(),
            sqlException.getMessage(),
            sqlException
    );
    throw targetUnavailable();
}

                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Target recovery failed; no change was committed.");
            }
        } catch (SQLException exception) {
            log.warn("Target recovery connection failed: connectionId={}, sqlState={}", savedConnection.getConnectionId(), exception.getSQLState());
            throw targetUnavailable();
        }
    }

    private Evaluation inspect(Connection target, AuditLog auditLog, boolean lockRow) throws SQLException {
        MonitoringConfiguration configuration = auditLog.getConfiguration();
        if (configuration == null || configuration.getConnection() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Audit event has no saved database connection.");
        }
        DatabaseConnection savedConnection = configuration.getConnection();
        if (savedConnection.getDbType() == null || !savedConnection.getDbType().equalsIgnoreCase("PostgreSQL")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recovery currently supports PostgreSQL only.");
        }
        if (!"CONNECTED".equalsIgnoreCase(savedConnection.getConnectionStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Test the target database connection before recovery.");
        }

        String schemaName = configuration.getSchemaName();
        String tableName = configuration.getTableName();
        if (schemaName == null || tableName == null || schemaName.isBlank() || tableName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Audit event table metadata is invalid.");
        }
        requireSafeIdentifier(schemaName, "Schema name");
        requireSafeIdentifier(tableName, "Table name");

        Map<String, Object> before = parseJsonMap(auditLog.getBeforeState());
        Map<String, Object> after = parseJsonMap(auditLog.getAfterState());
        if (before == null || after == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Audit event historical values are missing or invalid; recovery cannot invent them.");
        }

        DatabaseMetaData metadata = target.getMetaData();
        Set<String> columns = loadColumns(metadata, target.getCatalog(), schemaName, tableName);
        String primaryKey = loadSinglePrimaryKey(metadata, target.getCatalog(), schemaName, tableName, columns);
        requireSafeIdentifier(primaryKey, "Primary key column");
        if (!before.containsKey(primaryKey) || !after.containsKey(primaryKey)
                || before.get(primaryKey) == null || after.get(primaryKey) == null
                || !valuesEqual(before.get(primaryKey), after.get(primaryKey))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Audit snapshot does not contain a stable primary key.");
        }

        List<String> changedFields = calculateChangedFields(before, after);
        if (changedFields.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No recoverable field changes were detected.");
        }
        for (String field : changedFields) {
            requireSafeIdentifier(field, "Changed column");
            if (isSensitive(field)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sensitive fields cannot be restored from an audit snapshot.");
            }
            if (!before.containsKey(field) || !after.containsKey(field)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A changed field is missing from an audit snapshot; recovery cannot invent historical values.");
            }
            if (!columns.contains(field) || field.equals(primaryKey)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A changed field cannot be safely validated for this table.");
            }
        }

        verifyAuditTrigger(
        target,
        schemaName,
        tableName,
        auditLog.getConfiguration()
);
        verifyActiveMonitoringConfiguration(configuration);

        String quote = metadata.getIdentifierQuoteString();
        if (quote == null || quote.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Database metadata does not support safe identifier quoting.");
        }
        String tableSql = quoteIdentifier(quote, schemaName) + "." + quoteIdentifier(quote, tableName);
        String selectSql = "SELECT * FROM " + tableSql + " WHERE " + quoteIdentifier(quote, primaryKey) + " = ?"
                + (lockRow ? " FOR UPDATE" : "");
        Object primaryKeyValue = after.get(primaryKey);
        Map<String, Object> current;
        try (PreparedStatement statement = target.prepareStatement(selectSql)) {
            statement.setObject(1, primaryKeyValue);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The audited record no longer exists.");
                }
                current = readCurrentRow(result);
                if (result.next()) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "The primary key did not identify a single record.");
                }
            }
        }

        int comparableFields = 0;
        boolean drift = false;
        for (Map.Entry<String, Object> entry : after.entrySet()) {
            String field = entry.getKey();
            if (isSensitive(field) || !columns.contains(field) || !current.containsKey(field)) {
                continue;
            }
            comparableFields++;
            if (!valuesEqual(entry.getValue(), current.get(field))) {
                drift = true;
            }
        }
        if (comparableFields == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No audited fields can be reliably compared with the live record.");
        }

        return new Evaluation(
                schemaName,
                tableName,
                primaryKey,
                primaryKeyValue,
                quote,
                columns,
                before,
                after,
                current,
                changedFields,
                drift);
    }

    private Set<String> loadColumns(DatabaseMetaData metadata, String catalog, String schema, String table) throws SQLException {
        boolean foundTable = false;
        try (ResultSet tables = metadata.getTables(catalog, schema, table, new String[]{"TABLE"})) {
            while (tables.next()) {
                if (schema.equals(tables.getString("TABLE_SCHEM")) && table.equals(tables.getString("TABLE_NAME"))) {
                    foundTable = true;
                }
            }
        }
        if (!foundTable) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The audited table does not exist in the target database.");
        }

        Set<String> columns = new LinkedHashSet<>();
        try (ResultSet result = metadata.getColumns(catalog, schema, table, null)) {
            while (result.next()) {
                columns.add(result.getString("COLUMN_NAME"));
            }
        }
        if (columns.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target table metadata is unavailable.");
        }
        return columns;
    }

    private String loadSinglePrimaryKey(
            DatabaseMetaData metadata,
            String catalog,
            String schema,
            String table,
            Set<String> columns) throws SQLException {
        List<String> keys = new ArrayList<>();
        try (ResultSet result = metadata.getPrimaryKeys(catalog, schema, table)) {
            while (result.next()) {
                keys.add(result.getString("COLUMN_NAME"));
            }
        }
        if (keys.size() != 1 || !columns.contains(keys.get(0))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recovery requires one verifiable primary-key column.");
        }
        return keys.get(0);
    }

  private void verifyAuditTrigger(
        Connection target,
        String schemaName,
        String tableName,
        MonitoringConfiguration configuration) {

    if (configuration == null
            || configuration.getConfigId() == null
            || configuration.getConnection() == null
            || configuration.getConnection().getConnectionId() == null) {
        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "The monitoring configuration is incomplete.");
    }

    String expectedTrigger = "dt_capture_"
            + configuration.getConfigId();

    String expectedFunctionSchema = "datatrail_capture_"
            + configuration.getConnection().getConnectionId();

    String sql = """
            SELECT t.tgenabled,
                   target_schema.nspname AS table_schema,
                   target_table.relname AS table_name,
                   t.tgname AS trigger_name,
                   function_schema.nspname AS function_schema,
                   p.proname AS function_name,
                   p.prorettype::regtype::text AS return_type,
                   p.prorettype = 'pg_catalog.trigger'::regtype AS returns_trigger
            FROM pg_trigger t
            JOIN pg_class target_table
              ON target_table.oid = t.tgrelid
            JOIN pg_namespace target_schema
              ON target_schema.oid = target_table.relnamespace
            JOIN pg_proc p
              ON p.oid = t.tgfoid
            JOIN pg_namespace function_schema
              ON function_schema.oid = p.pronamespace
            WHERE NOT t.tgisinternal
              AND target_schema.nspname = ?
              AND target_table.relname = ?
              AND t.tgname = ?
            """;

    try (PreparedStatement statement =
                 target.prepareStatement(sql)) {

        statement.setString(1, schemaName);
        statement.setString(2, tableName);
        statement.setString(3, expectedTrigger);

        try (ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Recovery requires the configured DataTrail capture trigger.");
            }

            boolean valid =
                    "O".equals(result.getString("tgenabled"))
                    && expectedTrigger.equals(
                            result.getString("trigger_name"))
                    && expectedFunctionSchema.equals(
                            result.getString("function_schema"))
                    && "capture_row_change".equals(
                            result.getString("function_name"))
                    && result.getBoolean("returns_trigger")
                    && !result.next();

            if (!valid) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "The DataTrail capture trigger is missing, disabled, or incorrectly configured.");
            }
        }

    } catch (SQLException exception) {
        throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Could not verify the DataTrail capture trigger.",
                exception);
    }
}
    
private void verifyActiveMonitoringConfiguration(
        MonitoringConfiguration configuration) {

    if (configuration == null
            || configuration.getConfigId() == null
            || !Boolean.TRUE.equals(configuration.getMonitoringEnabled())
            || !Boolean.TRUE.equals(configuration.getMonitorUpdate())) {
        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "The saved monitoring configuration is not enabled for UPDATE recovery.");
    }

    LocalDateTime now = LocalDateTime.now();

    if (configuration.getEffectiveFrom() != null
            && now.isBefore(configuration.getEffectiveFrom())) {
        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "The monitoring configuration is not effective yet.");
    }

    if (configuration.getEffectiveTo() != null
            && now.isAfter(configuration.getEffectiveTo())) {
        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "The monitoring configuration has expired.");
    }
}


    private void acquireAuditChainLock(Connection target, Long configId) throws SQLException {
        try (PreparedStatement statement = target.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
            statement.setLong(1, configId);
            statement.execute();
        }
    }

    private String currentTransactionId(Connection target) throws SQLException {
        try (PreparedStatement statement = target.prepareStatement("SELECT txid_current()::text");
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new SQLException("Unable to identify recovery transaction.");
            }
            return result.getString(1);
        }
    }

    private void setActor(Connection target, String actor) throws SQLException {
        try (PreparedStatement statement = target.prepareStatement("SELECT set_config('datatrail.actor', ?, true)")) {
            statement.setString(1, actor);
            statement.execute();
        }
    }

    private void executeRestoreUpdate(Connection target, Evaluation evaluation) throws SQLException {
        String table = quoteIdentifier(evaluation.quote, evaluation.schemaName) + "."
                + quoteIdentifier(evaluation.quote, evaluation.tableName);
        List<String> updateFields = evaluation.changedFields;
        StringBuilder sql = new StringBuilder("UPDATE ").append(table).append(" SET ");
        for (int index = 0; index < updateFields.size(); index++) {
            if (index > 0) {
                sql.append(", ");
            }
            String field = updateFields.get(index);
            sql.append(quoteIdentifier(evaluation.quote, field)).append(" = ?");
        }
        sql.append(" WHERE ").append(quoteIdentifier(evaluation.quote, evaluation.primaryKey)).append(" = ?");

        try (PreparedStatement statement = target.prepareStatement(sql.toString())) {
            int index = 1;
            for (String field : updateFields) {
                statement.setObject(index++, evaluation.beforeState.get(field));
            }
            statement.setObject(index, evaluation.primaryKeyValue);
            if (statement.executeUpdate() != 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "The target record changed or disappeared during recovery.");
            }
        }
    }

   
private long verifyTriggerOutbox(
        Connection target,
        AuditLog original,
        Evaluation evaluation,
        String transactionId) throws SQLException {

    String quote = target.getMetaData().getIdentifierQuoteString();
    if (quote == null || quote.isBlank()) {
        throw new SQLException("PostgreSQL identifier quoting is unavailable.");
    }

    Long connectionId = original.getConfiguration()
            .getConnection().getConnectionId();

    String outboxTable = PostgresChangeCaptureInstaller
            .qualifiedOutboxTable(quote, connectionId);

    String sql = """
            SELECT event_id, config_id, operation, transaction_id,
                   before_state, after_state, changed_by, database_user,
                   actor_source, event_timestamp
            FROM %s
            WHERE config_id = ?
              AND transaction_id = ?
              AND operation = 'UPDATE'
            ORDER BY event_id DESC
            LIMIT 2
            """.formatted(outboxTable);

    long eventId;
    Map<String, Object> eventBefore;
    Map<String, Object> eventAfter;

    try (PreparedStatement statement = target.prepareStatement(sql)) {
        statement.setLong(1, original.getConfiguration().getConfigId());
        statement.setString(2, transactionId);

        try (ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "The PostgreSQL capture trigger did not create a recovery event; recovery was rolled back.");
            }

            eventId = result.getLong("event_id");

            if (result.getLong("config_id")
                    != original.getConfiguration().getConfigId()
                    || !"UPDATE".equals(result.getString("operation"))
                    || !transactionId.equals(result.getString("transaction_id"))) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "The captured recovery event does not match the recovery transaction.");
            }

            eventBefore = parseJsonMap(result.getString("before_state"));
            eventAfter = parseJsonMap(result.getString("after_state"));

            if (result.next()) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Multiple captured UPDATE events matched this recovery transaction; recovery was rolled back.");
            }
        }
    }

    if (eventBefore == null || eventAfter == null) {
        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "The captured recovery event has invalid before/after state.");
    }

    for (String field : evaluation.changedFields) {
        if (!eventBefore.containsKey(field)
                || !eventAfter.containsKey(field)
                || !valuesEqual(
                        eventBefore.get(field),
                        evaluation.currentState.get(field))
                || !valuesEqual(
                        eventAfter.get(field),
                        evaluation.beforeState.get(field))) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "The captured event does not match the restored row; recovery was rolled back.");
        }
    }

    return eventId;
}

    private Connection openTarget(AuditLog auditLog) throws SQLException {
        DatabaseConnection connection = auditLog.getConfiguration().getConnection();
        Connection target = databaseConnectionService.openTargetConnection(connection);
        try (PreparedStatement lockTimeout = target.prepareStatement("SELECT set_config('lock_timeout', '15s', false)");
             PreparedStatement statementTimeout = target.prepareStatement("SELECT set_config('statement_timeout', '60s', false)")) {
            lockTimeout.execute();
            statementTimeout.execute();
        } catch (SQLException exception) {
            try {
                target.close();
            } catch (SQLException ignored) {
                // prefer the original configuration failure
            }
            throw exception;
        }
        return target;
    }

    private AuditLog loadAuditLog(Long auditId) {
        return auditLogRepository.findById(auditId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Audit log not found."));
    }

    private void requireUpdateAudit(AuditLog auditLog) {
        String operation = auditLog.getOperation();
        if (!"UPDATE".equalsIgnoreCase(operation)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Recovery currently supports UPDATE events only. "
                            + (operation == null ? "Unknown" : operation.toUpperCase())
                            + " events are rejected because safe historical restoration is not implemented for them.");
        }
    }

    private void requireSafeIdentifier(String identifier, String label) {
        if (identifier == null || !identifier.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " is not a safe SQL identifier.");
        }
    }

    private User requireUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found."));
    }

    private User requireAdmin(String username) {
        User user = requireUser(username);
        if (user.getRole() == null || !"ADMIN".equalsIgnoreCase(user.getRole().getRoleName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator role is required for restore override.");
        }
        return user;
    }

    private void requireProjectAccess(AuditLog auditLog, String username) {
        if (auditLog.getConfiguration() == null
                || auditLog.getConfiguration().getConnection() == null
                || auditLog.getConfiguration().getConnection().getProject() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Audit event is not linked to an accessible project.");
        }
        requireProjectAccess(auditLog.getConfiguration().getConnection().getProject(), username);
    }

    private void requireProjectAccess(Project project, String username) {
        Project persisted = projectRepository.findById(project.getProjectId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found."));
        if (persisted.getOwner() == null || !Objects.equals(persisted.getOwner().getUsername(), username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this project's recovery actions.");
        }
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
            return parsed == null ? null : new LinkedHashMap<>(parsed);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Audit event state is invalid.");
        }
    }

    private List<String> calculateChangedFields(Map<String, Object> before, Map<String, Object> after) {
        Set<String> keys = new LinkedHashSet<>(before.keySet());
        keys.addAll(after.keySet());
        List<String> changed = new ArrayList<>();
        for (String key : keys) {
            if (!isSensitive(key) && (!before.containsKey(key) || !after.containsKey(key)
                    || !valuesEqual(before.get(key), after.get(key)))) {
                changed.add(key);
            }
        }
        return changed;
    }

    private boolean valuesEqual(Object left, Object right) {
        return Objects.equals(normalizeValue(left), normalizeValue(right));
    }

    private Object normalizeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            try {
                return new BigDecimal(number.toString()).stripTrailingZeros();
            } catch (NumberFormatException ignored) {
                return number.toString();
            }
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toString();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().toString();
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toString();
        }
        if (value instanceof LocalDate date) {
            return date.toString();
        }
        if (value instanceof UUID uuid) {
            return uuid.toString().toLowerCase();
        }
        if (value instanceof String string) {
            String candidate = string.trim();
            try {
                return OffsetDateTime.parse(candidate).toLocalDateTime().toString();
            } catch (DateTimeParseException ignored) {
                try {
                    return LocalDateTime.parse(candidate.replace(' ', 'T')).toString();
                } catch (DateTimeParseException ignoredAgain) {
                    try {
                        return LocalDate.parse(candidate).toString();
                    } catch (DateTimeParseException ignoredDate) {
                        return string;
                    }
                }
            }
        }
        return value;
    }

    private Map<String, Object> readCurrentRow(ResultSet result) throws SQLException {
        ResultSetMetaData metadata = result.getMetaData();
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 1; index <= metadata.getColumnCount(); index++) {
            row.put(metadata.getColumnLabel(index), result.getObject(index));
        }
        return row;
    }

    private Map<String, Object> visibleState(Map<String, Object> state) {
        Map<String, Object> visible = new LinkedHashMap<>();
        state.forEach((key, value) -> {
            if (!isSensitive(key)) {
                visible.put(key, value);
            }
        });
        return visible;
    }

    private boolean isSensitive(String name) {
        String normalized = name == null ? "" : name.trim().toLowerCase().replace('-', '_');
        return SENSITIVE_NAMES.stream().anyMatch(normalized::contains);
    }

    private String quoteIdentifier(String quote, String identifier) {
        return quote + identifier.replace(quote, quote + quote) + quote;
    }

    private ResponseStatusException targetUnavailable() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Target PostgreSQL recovery failed; no success was recorded.");
    }

    private String safeFailureMessage(Exception exception) {
        if (exception instanceof ResponseStatusException responseStatusException
                && responseStatusException.getReason() != null) {
            return responseStatusException.getReason();
        }
        return "Recovery failed. The target transaction was rolled back when possible.";
    }

    private record Evaluation(
            String schemaName,
            String tableName,
            String primaryKey,
            Object primaryKeyValue,
            String quote,
            Set<String> columns,
            Map<String, Object> beforeState,
            Map<String, Object> afterState,
            Map<String, Object> currentState,
            List<String> changedFields,
            boolean drift) {
    }

    

    private record AdminAttempt(RecoveryAction action, RecoveryExecution execution) {
    }

    private record StartedExecution(
            Long recoveryId,
            Long executionId,
            Long auditId,
            AuditLog auditLog) {
    }
}