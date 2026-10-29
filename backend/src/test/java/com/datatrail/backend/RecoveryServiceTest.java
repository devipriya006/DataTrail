
package com.datatrail.backend;

import com.datatrail.backend.dto.RecoveryPreviewResponse;
import com.datatrail.backend.entity.*;
import com.datatrail.backend.repository.*;
import com.datatrail.backend.service.DatabaseConnectionService;
import com.datatrail.backend.service.RecoveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecoveryServiceTest {

    @Mock private AuditLogRepository auditLogRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private RecoveryActionRepository recoveryActionRepository;
    @Mock private RecoveryExecutionRepository recoveryExecutionRepository;
    @Mock private UserRepository userRepository;
    @Mock private DatabaseConnectionService databaseConnectionService;
    @Mock private PlatformTransactionManager transactionManager;

    private RecoveryService recoveryService;
    private Fixture fixture;

    @BeforeEach
    void setUp() throws Exception {
        recoveryService = new RecoveryService(
                auditLogRepository,
                projectRepository,
                recoveryActionRepository,
                recoveryExecutionRepository,
                userRepository,
                databaseConnectionService,
                transactionManager);
    }

    @Test
    void previewAllowsMatchingSnapshotWithNumericAndTimestampNormalization() throws Exception {
        fixture = newFixture();
        stubTarget(fixture.target, "new@example.com", timestamp("2025-01-02T03:04:05"), null);

        RecoveryPreviewResponse preview = recoveryService.previewRecovery(99L, "alice");

        assertTrue(preview.allowed());
        assertFalse(preview.overrideAllowed());
        assertEquals("public.users", preview.tableName());
        assertEquals(List.of("email", "updated_at"), preview.changedFields());
        assertFalse(preview.currentState().containsKey("password_hash"));
    }

    @Test
    void previewBlocksGenuineDriftAndMarksOnlyThatBlockOverrideEligible() throws Exception {
        fixture = newFixture();
        stubTarget(fixture.target, "later-change@example.com", timestamp("2025-01-02T03:04:05"), null);

        RecoveryPreviewResponse preview = recoveryService.previewRecovery(99L, "alice");

        assertFalse(preview.allowed());
        assertTrue(preview.overrideAllowed());
        assertTrue(preview.message().contains("differ"));
    }

    @Test
    void previewRejectsInvalidProjectAccessBeforeOpeningTargetDatabase() throws Exception {
        fixture = newFixture();
        when(auditLogRepository.findById(99L)).thenReturn(Optional.of(fixture.auditLog));
        User other = user("other", "USER");
        Project otherProject = new Project(other, "other", "");
        otherProject.setProjectId(10L);
        when(projectRepository.findById(10L)).thenReturn(Optional.of(otherProject));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> recoveryService.previewRecovery(99L, "mallory"));

        assertEquals(403, exception.getStatusCode().value());
        verifyNoInteractions(databaseConnectionService);
    }

    @Test
    void adminOverrideRejectsMissingReasonAndIncorrectConfirmation() {
        ResponseStatusException missingReason = assertThrows(
                ResponseStatusException.class,
                () -> recoveryService.restoreAnyway(99L, "alice", "  ", "RESTORE ANYWAY"));
        ResponseStatusException wrongConfirmation = assertThrows(
                ResponseStatusException.class,
                () -> recoveryService.restoreAnyway(99L, "alice", "approved for test", "restore anyway"));

        assertEquals(400, missingReason.getStatusCode().value());
        assertEquals(400, wrongConfirmation.getStatusCode().value());
        verifyNoInteractions(auditLogRepository, databaseConnectionService);
    }

    @Test
    void adminOverrideRequiresDatabaseAdminRole() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user("alice", "USER")));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> recoveryService.restoreAnyway(99L, "alice", "approved", "RESTORE ANYWAY"));

        assertEquals(403, exception.getStatusCode().value());
        verifyNoInteractions(auditLogRepository, databaseConnectionService);
    }

    @Test
    void duplicateExecutionIsRejectedBeforeOpeningTargetDatabase() throws Exception {
        fixture = newFixture();
        RecoveryAction action = action("EXECUTED");
        when(recoveryActionRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(action));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> recoveryService.executeRecovery(12L, "alice"));

        assertEquals(409, exception.getStatusCode().value());
        verifyNoInteractions(databaseConnectionService);
    }

    @Test
    void approvalTransitionsExistingPendingActionAndPersistsApproverAndTime() throws Exception {
        fixture = newFixture();
        stubTarget(fixture.target, "new@example.com", timestamp("2025-01-02T03:04:05"), null);
        RecoveryAction existing = action("PENDING");
        User approver = user("alice", "USER");
        when(recoveryActionRepository.findFirstByAuditLogAuditIdAndRecoveryTypeOrderByRecoveryIdDesc(
                99L, "UPDATE_RESTORE")).thenReturn(Optional.of(existing));
        when(recoveryExecutionRepository.hasSuccessfulExecution(12L)).thenReturn(false);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(approver));
        when(recoveryActionRepository.save(existing)).thenReturn(existing);

        RecoveryAction approved = recoveryService.approveRecovery(99L, "alice");

        assertSame(existing, approved);
        assertEquals("APPROVED", approved.getApprovalStatus());
        assertEquals("alice", approved.getApprovedBy().getUsername());
        assertNotNull(approved.getApprovedAt());
        verify(recoveryActionRepository).save(existing);
    }

    @Test
    void repeatedApprovalOfAlreadyApprovedActionIsIdempotent() throws Exception {
        fixture = newFixture();
        stubTarget(fixture.target, "new@example.com", timestamp("2025-01-02T03:04:05"), null);
        RecoveryAction existing = action("APPROVED");
        when(recoveryActionRepository.findFirstByAuditLogAuditIdAndRecoveryTypeOrderByRecoveryIdDesc(
                99L, "UPDATE_RESTORE")).thenReturn(Optional.of(existing));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user("alice", "USER")));

        RecoveryAction approved = recoveryService.approveRecovery(99L, "alice");

        assertSame(existing, approved);
        verify(recoveryActionRepository, never()).save(any(RecoveryAction.class));
    }

    @Test
    void approvalReusesFailedActionInsteadOfCreatingDuplicate() throws Exception {
        fixture = newFixture();
        stubTarget(fixture.target, "new@example.com", timestamp("2025-01-02T03:04:05"), null);
        RecoveryAction existing = action("FAILED");
        User approver = user("alice", "USER");
        when(recoveryActionRepository.findFirstByAuditLogAuditIdAndRecoveryTypeOrderByRecoveryIdDesc(
                99L, "UPDATE_RESTORE")).thenReturn(Optional.of(existing));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(approver));
        when(recoveryExecutionRepository.hasSuccessfulExecution(12L)).thenReturn(false);
        when(recoveryActionRepository.save(existing)).thenReturn(existing);

        RecoveryAction approved = recoveryService.approveRecovery(99L, "alice");

        assertSame(existing, approved);
        assertEquals("APPROVED", approved.getApprovalStatus());
        verify(recoveryActionRepository).save(existing);
        verify(recoveryActionRepository, never()).save(argThat(other -> other != existing));
    }

    @Test
    void approvalRejectsAlreadyExecutedAction() throws Exception {
        fixture = newFixture();
        stubTarget(fixture.target, "new@example.com", timestamp("2025-01-02T03:04:05"), null);
        when(recoveryActionRepository.findFirstByAuditLogAuditIdAndRecoveryTypeOrderByRecoveryIdDesc(
                99L, "UPDATE_RESTORE")).thenReturn(Optional.of(action("EXECUTED")));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user("alice", "USER")));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> recoveryService.approveRecovery(99L, "alice"));

        assertEquals(409, exception.getStatusCode().value());
    }

    @Test
    void newlyCreatedRecoveryActionIsApprovedImmediately() throws Exception {
        fixture = newFixture();
        stubTarget(fixture.target, "new@example.com", timestamp("2025-01-02T03:04:05"), null);
        User approver = user("alice", "USER");
        when(recoveryActionRepository.findFirstByAuditLogAuditIdAndRecoveryTypeOrderByRecoveryIdDesc(
                99L, "UPDATE_RESTORE")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(approver));
        when(recoveryActionRepository.save(any(RecoveryAction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RecoveryAction created = recoveryService.approveRecovery(99L, "alice");

        assertEquals("APPROVED", created.getApprovalStatus());
        assertEquals("alice", created.getApprovedBy().getUsername());
        assertNotNull(created.getApprovedAt());
    }

    @Test
    void approvalAndExecutionRemainSeparateStages() throws Exception {
        fixture = newFixture();
        stubTarget(fixture.target, "new@example.com", timestamp("2025-01-02T03:04:05"), null);
        when(recoveryActionRepository.findFirstByAuditLogAuditIdAndRecoveryTypeOrderByRecoveryIdDesc(
                99L, "UPDATE_RESTORE")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user("alice", "USER")));
        when(recoveryActionRepository.save(any(RecoveryAction.class))).thenAnswer(invocation -> {
            RecoveryAction saved = invocation.getArgument(0);
            saved.setRecoveryId(12L);
            return saved;
        });

        RecoveryAction approved = recoveryService.approveRecovery(99L, "alice");

        assertEquals("APPROVED", approved.getApprovalStatus());
        assertEquals(12L, approved.getRecoveryId());
        verify(fixture.target, never()).commit();
        verify(recoveryExecutionRepository, never()).saveAndFlush(any());
    }

    @Test
    void executionRejectsPendingAction() throws Exception {
        fixture = newFixture();
        RecoveryAction pending = action("PENDING");
        when(recoveryActionRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(pending));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> recoveryService.executeRecovery(12L, "alice"));

        assertEquals(409, exception.getStatusCode().value());
        verifyNoInteractions(databaseConnectionService);
    }

    @Test
    void executionRejectsWhenSuccessfulExecutionAlreadyExists() throws Exception {
        fixture = newFixture();
        RecoveryAction approved = action("APPROVED");
        when(recoveryActionRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(approved));
        when(recoveryExecutionRepository.hasSuccessfulExecution(12L)).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> recoveryService.executeRecovery(12L, "alice"));

        assertEquals(409, exception.getStatusCode().value());
        verifyNoInteractions(databaseConnectionService);
    }

    @Test
    void executionRejectsUnauthorizedProjectAccess() throws Exception {
        fixture = newFixture();
        RecoveryAction approved = action("APPROVED");
        when(recoveryActionRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(approved));
        when(recoveryExecutionRepository.hasSuccessfulExecution(12L)).thenReturn(false);
        when(recoveryExecutionRepository.findRunningExecutions(12L)).thenReturn(List.of());
        when(auditLogRepository.findById(99L)).thenReturn(Optional.of(fixture.auditLog));
        when(projectRepository.findById(10L)).thenReturn(Optional.of(
                fixture.auditLog.getConfiguration().getConnection().getProject()));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> recoveryService.executeRecovery(12L, "mallory"));

        assertEquals(403, exception.getStatusCode().value());
        verifyNoInteractions(databaseConnectionService);
    }

    @Test
    void previewRejectsInsertOperations() throws Exception {
        fixture = newFixture();
        fixture.auditLog.setOperation("INSERT");
        when(auditLogRepository.findById(99L)).thenReturn(Optional.of(fixture.auditLog));
        when(projectRepository.findById(10L)).thenReturn(Optional.of(
                fixture.auditLog.getConfiguration().getConnection().getProject()));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> recoveryService.previewRecovery(99L, "alice"));

        assertEquals(400, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("UPDATE"));
        verifyNoInteractions(databaseConnectionService);
    }

    @Test
    void previewRejectsMissingHistoricalValues() throws Exception {
        fixture = newFixture();
        fixture.auditLog.setBeforeState(null);
        when(auditLogRepository.findById(99L)).thenReturn(Optional.of(fixture.auditLog));
        when(projectRepository.findById(10L)).thenReturn(Optional.of(
                fixture.auditLog.getConfiguration().getConnection().getProject()));
        when(databaseConnectionService.openTargetConnection(fixture.connection)).thenReturn(fixture.target);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(fixture.target.getMetaData()).thenReturn(metadata);

        PreparedStatement timeout = mock(PreparedStatement.class);
        when(timeout.execute()).thenReturn(true);
        when(fixture.target.prepareStatement(anyString())).thenReturn(timeout);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> recoveryService.previewRecovery(99L, "alice"));

        assertEquals(400, exception.getStatusCode().value());
        assertTrue(exception.getReason().toLowerCase().contains("historical"));
    }

    @Test
    void previewRejectsMissingTargetRecord() throws Exception {
        fixture = newFixture();
        stubTargetMissingRow();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> recoveryService.previewRecovery(99L, "alice"));

        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void approveRejectsWhenTargetHasDriftConflict() throws Exception {
        fixture = newFixture();
        stubTarget(fixture.target, "later-change@example.com", timestamp("2025-01-02T03:04:05"), null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> recoveryService.approveRecovery(99L, "alice"));

        assertEquals(409, exception.getStatusCode().value());
        verify(recoveryActionRepository, never()).save(any(RecoveryAction.class));
    }

    @Test
    void missingTriggerAuditEventRollsBackAndDoesNotCreateApplicationAuditRow() throws Exception {
        fixture = newFixture();
        stubTarget(fixture.target, "new@example.com", timestamp("2025-01-02T03:04:05"), null);
        RecoveryAction action = action("APPROVED");
        stubExecutionPersistence(action);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> recoveryService.executeRecovery(12L, "alice"));

        assertEquals(503, exception.getStatusCode().value());
        verify(fixture.target).rollback();
        verify(auditLogRepository, never()).save(any(AuditLog.class));
        assertEquals("FAILED", action.getApprovalStatus());
    }

    @Test
    void successRequiresSameTransactionTriggerEventWithVerifiedHashChain() throws Exception {
        fixture = newFixture();
        AuditEvent event = new AuditEvent();
        event.exists = true;
        event.hash = "a".repeat(64);
        event.verifiedHash = event.hash;
        event.sequence = 1L;
        event.beforeState = "{\"user_id\":6,\"email\":\"new@example.com\",\"updated_at\":\"2025-01-02T03:04:05\"}";
        event.afterState = "{\"user_id\":6,\"email\":\"old@example.com\",\"updated_at\":\"2025-01-01T03:04:05\"}";
        event.previousHash = null;
        event.auditId = 501L;
        stubTarget(fixture.target, "new@example.com", timestamp("2025-01-02T03:04:05"), event);
        RecoveryAction action = action("APPROVED");
        stubExecutionPersistence(action);

        RecoveryExecution result = recoveryService.executeRecovery(12L, "alice");

        assertEquals("SUCCESS", result.getExecutionStatus());
        assertEquals(501L, result.getAuditEventId());
        assertEquals("EXECUTED", action.getApprovalStatus());
        verify(fixture.target).commit();
        verify(auditLogRepository, never()).save(any(AuditLog.class));
    }

    @Test
    void executionMarksInterruptedRunningRowsFailedBeforeRetry() throws Exception {
        fixture = newFixture();
        AuditEvent event = new AuditEvent();
        event.exists = true;
        event.hash = "a".repeat(64);
        event.verifiedHash = event.hash;
        event.sequence = 1L;
        event.beforeState = "{\"user_id\":6,\"email\":\"new@example.com\",\"updated_at\":\"2025-01-02T03:04:05\"}";
        event.afterState = "{\"user_id\":6,\"email\":\"old@example.com\",\"updated_at\":\"2025-01-01T03:04:05\"}";
        event.previousHash = null;
        event.auditId = 502L;
        stubTarget(fixture.target, "new@example.com", timestamp("2025-01-02T03:04:05"), event);
        RecoveryAction action = action("APPROVED");
        RecoveryExecution stale = new RecoveryExecution(
                action, "UPDATE_RESTORE", "stale", 1.0, "RUNNING", LocalDateTime.now().minusMinutes(5), null, null);
        stubExecutionPersistence(action);
        when(recoveryExecutionRepository.findRunningExecutions(12L)).thenReturn(List.of(stale));

        RecoveryExecution result = recoveryService.executeRecovery(12L, "alice");

        assertEquals("SUCCESS", result.getExecutionStatus());
        assertEquals("FAILED", stale.getExecutionStatus());
        assertNotNull(stale.getCompletedAt());
    }

    @Test
    void previewAllowsDifferentChangedFieldSetDrivenByAuditMetadata() throws Exception {
        fixture = newFixture();
        fixture.auditLog.setBeforeState("{\"user_id\":6,\"email\":\"keep@example.com\",\"updated_at\":\"2025-01-01T03:04:05\"}");
        fixture.auditLog.setAfterState("{\"user_id\":6,\"email\":\"keep@example.com\",\"updated_at\":\"2025-01-02T03:04:05\"}");
        stubTarget(fixture.target, "keep@example.com", timestamp("2025-01-02T03:04:05"), null);

        RecoveryPreviewResponse preview = recoveryService.previewRecovery(99L, "alice");

        assertTrue(preview.allowed());
        assertEquals(List.of("updated_at"), preview.changedFields());
        assertNull(preview.recoveryId());
    }

    private Fixture newFixture() throws Exception {
        User owner = user("alice", "USER");
        Project project = new Project(owner, "Project A", "desc");
        project.setProjectId(10L);
        DatabaseConnection connection = new DatabaseConnection(
                project, "PostgreSQL", "localhost", 5432, "target", "target_user", "not-used-in-test", false, "CONNECTED");
        connection.setConnectionId(7L);
        MonitoringConfiguration configuration = new MonitoringConfiguration(
                connection, "public", "users", true, true, true, "NORMAL", true, LocalDateTime.now().minusDays(1), null);
        configuration.setConfigId(5L);
        AuditLog log = new AuditLog(
                configuration,
                "UPDATE",
                "tx-1",
                "{\"user_id\":6,\"email\":\"old@example.com\",\"updated_at\":\"2025-01-01T03:04:05\"}",
                "{\"user_id\":6,\"email\":\"new@example.com\",\"updated_at\":\"2025-01-02T03:04:05\"}",
                "alice",
                "postgres",
                "APPLICATION",
                LocalDateTime.now(),
                "real-hash-from-target",
                null,
                4L);
        log.setAuditId(99L);
        Connection target = mock(Connection.class);
        return new Fixture(log, connection, target);
    }

    private void stubExecutionPersistence(RecoveryAction action) {
        when(recoveryActionRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(action));
        when(recoveryExecutionRepository.hasSuccessfulExecution(12L)).thenReturn(false);
        when(recoveryExecutionRepository.findRunningExecutions(12L)).thenReturn(List.of());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user("alice", "USER")));

        when(recoveryExecutionRepository.saveAndFlush(any(RecoveryExecution.class)))
                .thenAnswer(invocation -> {
                    RecoveryExecution execution = invocation.getArgument(0);
                    if (execution.getExecutionId() == null) {
                        execution.setExecutionId(100L);
                    }
                    when(recoveryExecutionRepository.findById(execution.getExecutionId()))
                            .thenReturn(Optional.of(execution));
                    return execution;
                });

        when(recoveryExecutionRepository.save(any(RecoveryExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(recoveryActionRepository.save(any(RecoveryAction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void stubTargetMissingRow() throws Exception {
        when(auditLogRepository.findById(99L)).thenReturn(Optional.of(fixture.auditLog));
        when(projectRepository.findById(10L)).thenReturn(Optional.of(
                fixture.auditLog.getConfiguration().getConnection().getProject()));
        when(databaseConnectionService.openTargetConnection(fixture.connection)).thenReturn(fixture.target);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(fixture.target.getMetaData()).thenReturn(metadata);
        when(fixture.target.getCatalog()).thenReturn("target");
        when(metadata.getIdentifierQuoteString()).thenReturn("\"");

        ResultSet tables = mock(ResultSet.class);
        when(tables.next()).thenReturn(true, false);
        when(tables.getString("TABLE_SCHEM")).thenReturn("public");
        when(tables.getString("TABLE_NAME")).thenReturn("users");
        when(metadata.getTables(any(), eq("public"), eq("users"), any())).thenReturn(tables);

        ResultSet columns = mock(ResultSet.class);
        when(columns.next()).thenReturn(true, true, true, true, false);
        when(columns.getString("COLUMN_NAME")).thenReturn("user_id", "email", "updated_at", "password_hash");
        when(metadata.getColumns(any(), eq("public"), eq("users"), isNull())).thenReturn(columns);

        ResultSet keys = mock(ResultSet.class);
        when(keys.next()).thenReturn(true, false);
        when(keys.getString("COLUMN_NAME")).thenReturn("user_id");
        when(metadata.getPrimaryKeys(any(), eq("public"), eq("users"))).thenReturn(keys);

        PreparedStatement trigger = mock(PreparedStatement.class);
        ResultSet triggerResult = singleRow();
        stubValidCaptureTrigger(triggerResult);
        when(trigger.executeQuery()).thenReturn(triggerResult);

        PreparedStatement config = mock(PreparedStatement.class);
        ResultSet configResult = singleRow();
        when(configResult.getLong(1)).thenReturn(5L);
        when(config.executeQuery()).thenReturn(configResult);

        PreparedStatement timeout = mock(PreparedStatement.class);
        when(timeout.execute()).thenReturn(true);

        PreparedStatement row = mock(PreparedStatement.class);
        ResultSet rowResult = mock(ResultSet.class);
        when(rowResult.next()).thenReturn(false);
        when(row.executeQuery()).thenReturn(rowResult);

        when(fixture.target.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.startsWith("SELECT set_config(")) return timeout;
            if (sql.contains("pg_trigger")) return trigger;
            if (sql.startsWith("SELECT config_id FROM monitoring_configurations")) return config;
            if (sql.startsWith("SELECT * FROM")) return row;
            throw new AssertionError("Unexpected recovery SQL category: "
                    + sql.substring(0, Math.min(30, sql.length())));
        });
    }

    private void stubTarget(Connection target, String currentEmail, Timestamp updatedAt, AuditEvent auditEvent) throws Exception {
        when(auditLogRepository.findById(99L)).thenReturn(Optional.of(fixture.auditLog));
        when(projectRepository.findById(10L)).thenReturn(Optional.of(
                fixture.auditLog.getConfiguration().getConnection().getProject()));
        when(databaseConnectionService.openTargetConnection(fixture.connection)).thenReturn(target);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(target.getMetaData()).thenReturn(metadata);
        when(target.getCatalog()).thenReturn("target");
        when(metadata.getIdentifierQuoteString()).thenReturn("\"");

        ResultSet tables = mock(ResultSet.class);
        when(tables.next()).thenReturn(true, false);
        when(tables.getString("TABLE_SCHEM")).thenReturn("public");
        when(tables.getString("TABLE_NAME")).thenReturn("users");
        when(metadata.getTables(any(), eq("public"), eq("users"), any())).thenReturn(tables);

        ResultSet columns = mock(ResultSet.class);
        when(columns.next()).thenReturn(true, true, true, true, false);
        when(columns.getString("COLUMN_NAME")).thenReturn("user_id", "email", "updated_at", "password_hash");
        when(metadata.getColumns(any(), eq("public"), eq("users"), isNull())).thenReturn(columns);

        ResultSet keys = mock(ResultSet.class);
        when(keys.next()).thenReturn(true, false);
        when(keys.getString("COLUMN_NAME")).thenReturn("user_id");
        when(metadata.getPrimaryKeys(any(), eq("public"), eq("users"))).thenReturn(keys);

        PreparedStatement trigger = mock(PreparedStatement.class);
        ResultSet triggerResult = singleRow();
        stubValidCaptureTrigger(triggerResult);
        when(trigger.executeQuery()).thenReturn(triggerResult);

        PreparedStatement config = mock(PreparedStatement.class);
        ResultSet configResult = singleRow();
        when(configResult.getLong(1)).thenReturn(5L);
        when(config.executeQuery()).thenReturn(configResult);

        PreparedStatement row = mock(PreparedStatement.class);
        ResultSet rowResult = singleRow();
        ResultSetMetaData rowMetadata = mock(ResultSetMetaData.class);
        when(rowResult.getMetaData()).thenReturn(rowMetadata);
        when(rowMetadata.getColumnCount()).thenReturn(4);
        when(rowMetadata.getColumnLabel(1)).thenReturn("user_id");
        when(rowMetadata.getColumnLabel(2)).thenReturn("email");
        when(rowMetadata.getColumnLabel(3)).thenReturn("updated_at");
        when(rowMetadata.getColumnLabel(4)).thenReturn("password_hash");
        when(rowResult.getObject(1)).thenReturn(6L);
        when(rowResult.getObject(2)).thenReturn(currentEmail);
        when(rowResult.getObject(3)).thenReturn(updatedAt);
        when(rowResult.getObject(4)).thenReturn("redacted-value");
        when(row.executeQuery()).thenReturn(rowResult);

        PreparedStatement lock = mock(PreparedStatement.class);
        PreparedStatement txid = mock(PreparedStatement.class);
        ResultSet txidResult = singleRow();
        when(txidResult.getString(1)).thenReturn("42");
        when(txid.executeQuery()).thenReturn(txidResult);
        PreparedStatement actor = mock(PreparedStatement.class);
        PreparedStatement previous = mock(PreparedStatement.class);
        ResultSet previousResult = mock(ResultSet.class);
        when(previousResult.next()).thenReturn(false);
        when(previous.executeQuery()).thenReturn(previousResult);
        PreparedStatement update = mock(PreparedStatement.class);
        when(update.executeUpdate()).thenReturn(1);

        PreparedStatement outbox = mock(PreparedStatement.class);
        ResultSet outboxResult = mock(ResultSet.class);
        if (auditEvent != null && auditEvent.exists) {
            when(outboxResult.next()).thenReturn(true, false);
            when(outboxResult.getLong("event_id")).thenReturn(auditEvent.auditId);
            when(outboxResult.getLong("config_id")).thenReturn(5L);
            when(outboxResult.getString("operation")).thenReturn("UPDATE");
            when(outboxResult.getString("transaction_id")).thenReturn("42");
            when(outboxResult.getString("before_state")).thenReturn(auditEvent.beforeState);
            when(outboxResult.getString("after_state")).thenReturn(auditEvent.afterState);
        } else {
            when(outboxResult.next()).thenReturn(false);
        }
        when(outbox.executeQuery()).thenReturn(outboxResult);

        when(target.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("pg_trigger")) return trigger;
            if (sql.startsWith("SELECT config_id FROM monitoring_configurations")) return config;
            if (sql.startsWith("SELECT * FROM")) return row;
            if (sql.contains("pg_advisory_xact_lock")) return lock;
            if (sql.startsWith("SELECT chain_sequence, log_hash")) return previous;
            if (sql.startsWith("SELECT txid_current")) return txid;
            if (sql.startsWith("SELECT set_config")) return actor;
            if (sql.startsWith("UPDATE")) return update;
            if (sql.contains("outbox_events")) return outbox;
            throw new AssertionError("Unexpected recovery SQL category: "
                    + sql.substring(0, Math.min(30, sql.length())));
        });
    }

    private RecoveryAction action(String status) {
        RecoveryAction action = new RecoveryAction(
                null, fixture.auditLog, "restore", "UPDATE_RESTORE", status, null, null, LocalDateTime.now());
        action.setRecoveryId(12L);
        return action;
    }

    private User user(String name, String roleName) {
        return new User(name, name + "@example.com", "hash", new Role(roleName, roleName));
    }

    private ResultSet singleRow() throws Exception {
        ResultSet result = mock(ResultSet.class);
        when(result.next()).thenReturn(true, false);
        return result;
    }

    private void stubValidCaptureTrigger(ResultSet triggerResult) throws Exception {
        when(triggerResult.getString("tgenabled")).thenReturn("O");
        when(triggerResult.getString("trigger_name")).thenReturn("dt_capture_5");
        when(triggerResult.getString("function_schema")).thenReturn("datatrail_capture_7");
        when(triggerResult.getString("function_name")).thenReturn("capture_row_change");
        when(triggerResult.getBoolean("returns_trigger")).thenReturn(true);
    }

    private Timestamp timestamp(String value) {
        return Timestamp.valueOf(LocalDateTime.parse(value));
    }

    private record Fixture(AuditLog auditLog, DatabaseConnection connection, Connection target) {
    }

    private static final class AuditEvent {
        boolean exists;
        long auditId;
        long sequence;
        String previousHash;
        String hash;
        String verifiedHash;
        String beforeState;
        String afterState;
    }
}
