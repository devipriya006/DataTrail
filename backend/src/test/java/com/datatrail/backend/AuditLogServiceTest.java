package com.datatrail.backend;

import com.datatrail.backend.dto.AuditLogResponse;
import com.datatrail.backend.entity.*;
import com.datatrail.backend.repository.AuditLogRepository;
import com.datatrail.backend.repository.DatabaseConnectionRepository;
import com.datatrail.backend.repository.MonitoringConfigurationRepository;
import com.datatrail.backend.repository.ProjectRepository;
import com.datatrail.backend.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

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
}
