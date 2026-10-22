package com.datatrail.backend;

import com.datatrail.backend.dto.AuditLogResponse;
import com.datatrail.backend.dto.AuditEventIngestion;
import com.datatrail.backend.entity.*;
import com.datatrail.backend.repository.AuditLogRepository;
import com.datatrail.backend.repository.DatabaseConnectionRepository;
import com.datatrail.backend.repository.MonitoringConfigurationRepository;
import com.datatrail.backend.repository.ProjectRepository;
import com.datatrail.backend.service.AuditLogService;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private MonitoringConfigurationRepository monitoringConfigurationRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private DatabaseConnectionRepository databaseConnectionRepository;

    @InjectMocks
    private AuditLogService auditLogService;

    @Test
    void mapsEntityWithoutSensitiveFields() {
        Role role = new Role("USER", "Standard user role");

        User owner = new User("user1", "user1@example.com", "hash", role);
        Project project = new Project(owner, "Project A", "Desc");
        project.setProjectId(42L);

        DatabaseConnection connection = new DatabaseConnection(
                project,
                "PostgreSQL",
                "localhost",
                5432,
                "DataTrail",
                "app_user",
                "secret",
                true,
                "CONNECTED"
        );
        connection.setConnectionId(7L);

        MonitoringConfiguration configuration = new MonitoringConfiguration(
                connection,
                "public",
                "users",
                true,
                true,
                true,
                "NORMAL",
                true,
                LocalDateTime.now(),
                null
        );
        configuration.setConfigId(5L);

        AuditLog auditLog = new AuditLog(
                configuration,
                "UPDATE",
                "tx-1",
                "{\"user_id\":6,\"email\":\"old@example.com\",\"password_hash\":\"abc123\"}",
                "{\"user_id\":6,\"email\":\"new@example.com\",\"password_hash\":\"abc123\"}",
                "postgres",
                LocalDateTime.now(),
                "hash-1",
                "prev-hash",
                1L
        );
        auditLog.setAuditId(99L);

        AuditLogResponse response = auditLogService.toAuditLogResponse(auditLog);

        assertEquals(99L, response.auditId());
        assertEquals("public", response.schemaName());
        assertEquals("users", response.tableName());
        assertFalse(response.beforeState().contains("password_hash"));
        assertFalse(response.afterState().contains("password_hash"));
        assertTrue(response.beforeState().contains("email"));
        assertTrue(response.afterState().contains("new@example.com"));
    }

    @Test
    void deniesAccessToAnotherUsersProject() {
        Role role = new Role("USER", "Standard user role");

        User owner = new User("owner", "owner@example.com", "hash", role);
        Project project = new Project(owner, "Project A", "Desc");
        project.setProjectId(10L);

        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> auditLogService.requireProjectAccess(10L, "other-user")
        );

        assertEquals(403, exception.getStatusCode().value());
    }

    @Test
    void ingestsEventWithNextHashChainSequence() {
        MonitoringConfiguration configuration = createConfiguration();
        LocalDateTime eventTime = LocalDateTime.now();
        AuditEventIngestion event = new AuditEventIngestion(
                "event-1",
                "INSERT",
                "tx-1",
                null,
                "{\"id\":1}",
                "postgres",
                "db_user",
                "trigger",
                eventTime);
        when(monitoringConfigurationRepository.findByIdForAuditIngestion(5L))
                .thenReturn(Optional.of(configuration));
        when(auditLogRepository.findByConfigurationConfigIdAndSourceEventId(
                5L, "event-1"))
                .thenReturn(Optional.empty());
        when(auditLogRepository.findByConfigurationConfigId(5L))
                .thenReturn(List.of());
        when(auditLogRepository.saveAndFlush(any(AuditLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AuditLog ingested = auditLogService.ingestEvent(5L, event);

        assertEquals("event-1", ingested.getSourceEventId());
        assertEquals("INSERT", ingested.getOperation());
        assertEquals(1L, ingested.getChainSequence());
        assertNull(ingested.getPrevHash());
        assertEquals(64, ingested.getLogHash().length());
        assertEquals(event.beforeState(), ingested.getBeforeState());
        assertEquals(event.afterState(), ingested.getAfterState());
        verify(monitoringConfigurationRepository).findByIdForAuditIngestion(5L);

        AuditEventIngestion nextEvent = new AuditEventIngestion(
                "event-1-next",
                "UPDATE",
                "tx-2",
                "{\"id\":1}",
                "{\"id\":1,\"name\":\"updated\"}",
                "postgres",
                "db_user",
                "trigger",
                eventTime.plusSeconds(1));
        when(auditLogRepository.findByConfigurationConfigIdAndSourceEventId(
                5L, "event-1-next"))
                .thenReturn(Optional.empty());
        when(auditLogRepository.findByConfigurationConfigId(5L))
                .thenReturn(List.of(ingested));
        AuditLog secondIngested = auditLogService.ingestEvent(5L, nextEvent);
        assertEquals(2L, secondIngested.getChainSequence());
        assertEquals(ingested.getLogHash(), secondIngested.getPrevHash());

        when(monitoringConfigurationRepository.findById(5L))
                .thenReturn(Optional.of(configuration));
        when(projectRepository.findById(42L))
                .thenReturn(Optional.of(configuration.getConnection().getProject()));
        when(auditLogRepository.findByConfigurationConfigId(5L))
                .thenReturn(List.of(ingested, secondIngested));
        Map<String, Object> verification = auditLogService.verifyHashChain(
                5L, 42L, "owner");
        assertEquals(Boolean.TRUE, verification.get("valid"));
    }

    @Test
    void duplicateDeliveryReturnsExistingAuditLogWithoutAppending() {
        MonitoringConfiguration configuration = createConfiguration();
        configuration.setMonitoringEnabled(false);
        LocalDateTime eventTime = LocalDateTime.now();
        AuditEventIngestion event = new AuditEventIngestion(
                "event-2",
                "UPDATE",
                "tx-2",
                "{\"id\":1,\"name\":\"before\"}",
                "{\"id\":1,\"name\":\"after\"}",
                "postgres",
                "db_user",
                "trigger",
                eventTime);
        AuditLog existing = new AuditLog(
                configuration,
                "UPDATE",
                "tx-2",
                event.beforeState(),
                event.afterState(),
                "postgres",
                "db_user",
                "trigger",
                eventTime,
                "existing-hash",
                null,
                1L);
        existing.setSourceEventId("event-2");
        when(monitoringConfigurationRepository.findByIdForAuditIngestion(5L))
                .thenReturn(Optional.of(configuration));
        when(auditLogRepository.findByConfigurationConfigIdAndSourceEventId(
                5L, "event-2"))
                .thenReturn(Optional.of(existing));

        AuditLog result = auditLogService.ingestEvent(5L, event);

        assertSame(existing, result);
        verify(auditLogRepository, never()).saveAndFlush(any(AuditLog.class));
    }

    @Test
    void rejectsReusedEventIdWithDifferentPayload() {
        MonitoringConfiguration configuration = createConfiguration();
        LocalDateTime eventTime = LocalDateTime.now();
        AuditEventIngestion event = new AuditEventIngestion(
                "event-3", "DELETE", "tx-3", "{\"id\":1}", null,
                "postgres", "db_user", "trigger", eventTime);
        AuditLog existing = new AuditLog(
                configuration,
                "DELETE",
                "tx-3",
                "{\"id\":2}",
                null,
                "postgres",
                "db_user",
                "trigger",
                eventTime,
                "existing-hash",
                null,
                1L);
        when(monitoringConfigurationRepository.findByIdForAuditIngestion(5L))
                .thenReturn(Optional.of(configuration));
        when(auditLogRepository.findByConfigurationConfigIdAndSourceEventId(
                5L, "event-3"))
                .thenReturn(Optional.of(existing));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> auditLogService.ingestEvent(5L, event));

        assertEquals(409, exception.getStatusCode().value());
        verify(auditLogRepository, never()).saveAndFlush(any(AuditLog.class));
    }

    @Test
    void ingestionUsesTransactionalConfigurationLockAndUniqueEventIdentity() throws Exception {
        Method ingestionMethod = AuditLogService.class.getMethod(
                "ingestEvent", Long.class, AuditEventIngestion.class);
        assertNotNull(ingestionMethod.getAnnotation(Transactional.class));

        Method lockMethod = MonitoringConfigurationRepository.class.getMethod(
                "findByIdForAuditIngestion", Long.class);
        assertEquals(
                LockModeType.PESSIMISTIC_WRITE,
                lockMethod.getAnnotation(Lock.class).value());

        Table table = AuditLog.class.getAnnotation(Table.class);
        assertTrue(List.of(table.uniqueConstraints()).stream().anyMatch(constraint ->
                List.of(constraint.columnNames()).containsAll(
                        List.of("config_id", "source_event_id"))));
    }

    private MonitoringConfiguration createConfiguration() {
        Role role = new Role("USER", "Standard user role");
        User owner = new User("owner", "owner@example.com", "hash", role);
        Project project = new Project(owner, "Project A", "Desc");
        project.setProjectId(42L);
        DatabaseConnection connection = new DatabaseConnection(
                project, "PostgreSQL", "localhost", 5432, "target",
                "db_user", "secret", false, "CONNECTED");
        connection.setConnectionId(7L);
        MonitoringConfiguration configuration = new MonitoringConfiguration(
                connection, "public", "customers", true, true, true,
                "NORMAL", true, LocalDateTime.now().minusDays(1), null);
        configuration.setConfigId(5L);
        return configuration;
    }
}
