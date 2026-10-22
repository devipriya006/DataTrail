package com.datatrail.backend.service;

import com.datatrail.backend.entity.DatabaseConnection;
import com.datatrail.backend.entity.MonitoringConfiguration;
import com.datatrail.backend.entity.Project;
import com.datatrail.backend.entity.Role;
import com.datatrail.backend.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class PostgresChangeCaptureInstallerTest {

    @Test
    void buildsTriggerForOnlyEnabledEventsAndQuotesTargetIdentifiers() {
        MonitoringConfiguration configuration = configuration(
                "odd\"schema",
                "table\"; DROP TABLE public.users;--");
        configuration.setMonitorInsert(false);
        configuration.setMonitorUpdate(true);
        configuration.setMonitorDelete(false);

        String sql = PostgresChangeCaptureInstaller.buildTriggerSql(
                "\"",
                configuration);

        assertEquals(
                "CREATE TRIGGER \"dt_capture_5\" AFTER UPDATE ON "
                        + "\"odd\"\"schema\".\"table\"\"; DROP TABLE public.users;--\" "
                        + "FOR EACH ROW EXECUTE FUNCTION "
                        + "\"datatrail_capture_7\".\"capture_row_change\"("
                        + "'5', '2025-01-01T00:00', '', '"
                        + ZoneId.systemDefault().getId() + "')",
                sql);
    }

    @Test
    void doesNotCreateTriggerWhenMonitoringIsDisabled() {
        MonitoringConfiguration configuration = configuration("public", "customers");
        configuration.setMonitoringEnabled(false);

        assertNull(PostgresChangeCaptureInstaller.buildTriggerSql(
                "\"",
                configuration));
    }

    @Test
    void generatesConnectionScopedFunctionAndOutboxDdl() {
        String ddl = PostgresChangeCaptureInstaller.captureFunctionDdl("\"", 7L);

        assertTrue(ddl.contains(
                "CREATE OR REPLACE FUNCTION \"datatrail_capture_7\".\"capture_row_change\"()"));
        assertTrue(ddl.contains(
                "INSERT INTO \"datatrail_capture_7\".\"outbox_events\""));
        assertTrue(ddl.contains("to_jsonb(OLD)::TEXT"));
        assertTrue(ddl.contains("to_jsonb(NEW)::TEXT"));
        assertTrue(ddl.contains("RAISE EXCEPTION 'Unsupported DataTrail capture operation: %'"));
        assertFalse(ddl.contains("datatrail_capture.outbox_events"));
    }

    private MonitoringConfiguration configuration(String schema, String table) {
        Role role = new Role("USER", "Standard user role");
        User owner = new User("owner", "owner@example.com", "hash", role);
        Project project = new Project(owner, "Project A", "Desc");
        DatabaseConnection connection = new DatabaseConnection(
                project,
                "PostgreSQL",
                "localhost",
                5432,
                "target",
                "db_user",
                "secret",
                false,
                "CONNECTED");
        connection.setConnectionId(7L);
        MonitoringConfiguration configuration = new MonitoringConfiguration(
                connection,
                schema,
                table,
                true,
                true,
                true,
                "NORMAL",
                true,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                null);
        configuration.setConfigId(5L);
        return configuration;
    }
}
