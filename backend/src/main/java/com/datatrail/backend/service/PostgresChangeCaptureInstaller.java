package com.datatrail.backend.service;

import com.datatrail.backend.entity.MonitoringConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
public class PostgresChangeCaptureInstaller {

    private static final Logger logger =
            LoggerFactory.getLogger(PostgresChangeCaptureInstaller.class);
    private static final String CAPTURE_FUNCTION = "capture_row_change";
    private static final String CAPTURE_FUNCTION_VERSION = "datatrail-capture-v1";

    private final DatabaseConnectionService databaseConnectionService;

    public PostgresChangeCaptureInstaller(
            DatabaseConnectionService databaseConnectionService) {
        this.databaseConnectionService = databaseConnectionService;
    }

    public void install(MonitoringConfiguration configuration) {
        if (configuration.getConfigId() == null
                || configuration.getConnection() == null
                || configuration.getConnection().getConnectionId() == null) {
            throw new IllegalStateException(
                    "Monitoring configuration and its connection must be saved before capture installation.");
        }

        try (Connection connection = databaseConnectionService.openTargetConnection(
                configuration.getConnection())) {
            connection.setAutoCommit(false);
            try {
                acquireInfrastructureLock(connection);
                acquireConfigurationLock(connection, configuration.getConfigId());
                if (!configuredEvents(configuration).isEmpty()) {
                    ensureCaptureObjects(
                            connection,
                            configuration.getConnection().getConnectionId());
                }
                synchronizeTrigger(connection, configuration);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            logger.warn(
                    "PostgreSQL change-capture installation failed: configId={}, connectionId={}, sqlState={}",
                    configuration.getConfigId(),
                    configuration.getConnection().getConnectionId(),
                    exception.getSQLState());
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Could not configure PostgreSQL change capture. The connection user must be able to create the capture schema objects and own or have TRIGGER privilege on the monitored table.",
                    exception);
        }
    }

    public void uninstall(MonitoringConfiguration configuration) {
        try (Connection connection = databaseConnectionService.openTargetConnection(
                configuration.getConnection())) {
            connection.setAutoCommit(false);
            try {
                acquireConfigurationLock(connection, configuration.getConfigId());
                String quote = connection.getMetaData().getIdentifierQuoteString();
                if (quote == null || quote.isBlank()) {
                    throw new SQLException(
                            "The PostgreSQL driver did not provide an identifier quote string.");
                }
                if (captureObjectsExist(
                        connection,
                        configuration.getConnection().getConnectionId())) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT COUNT(*) FROM "
                                    + qualifiedOutboxTable(
                                            quote,
                                            configuration.getConnection().getConnectionId())
                                    + " WHERE config_id = ?")) {
                        statement.setLong(1, configuration.getConfigId());
                        try (ResultSet result = statement.executeQuery()) {
                            if (result.next() && result.getLong(1) > 0) {
                                throw new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Pending captured events must be ingested before deleting this monitoring configuration.");
                            }
                        }
                    }
                }
                String table = quoteIdentifier(quote, configuration.getSchemaName())
                        + "." + quoteIdentifier(quote, configuration.getTableName());
                dropTrigger(
                        connection,
                        quote,
                        triggerName(configuration.getConfigId()),
                        table);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            logger.warn(
                    "PostgreSQL change-capture removal failed: configId={}, connectionId={}, sqlState={}",
                    configuration.getConfigId(),
                    configuration.getConnection().getConnectionId(),
                    exception.getSQLState());
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Could not safely remove PostgreSQL change capture for this configuration.",
                    exception);
        }
    }

    static String lockKey(Long configId) {
        return "datatrail:capture:configuration:" + configId;
    }

    private void acquireInfrastructureLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
            statement.setString(1, "datatrail:capture:infrastructure");
            statement.execute();
        }
    }

    private void acquireConfigurationLock(Connection connection, Long configId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
            statement.setString(1, lockKey(configId));
            statement.execute();
        }
    }

    static boolean captureObjectsExist(Connection connection, Long connectionId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT to_regclass(?) IS NOT NULL")) {
            statement.setString(1, captureSchema(connectionId) + ".outbox_events");
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        }
    }

    private void ensureCaptureObjects(Connection connection, Long connectionId)
            throws SQLException {
        String quote = connection.getMetaData().getIdentifierQuoteString();
        if (quote == null || quote.isBlank()) {
            throw new SQLException(
                    "The PostgreSQL driver did not provide an identifier quote string.");
        }
        String schema = quoteIdentifier(quote, captureSchema(connectionId));
        String outboxTable = qualifiedOutboxTable(quote, connectionId);
        boolean functionCurrent = captureFunctionIsCurrent(connection, connectionId);
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS %s (
                        event_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        config_id BIGINT NOT NULL,
                        operation VARCHAR(20) NOT NULL,
                        transaction_id VARCHAR(100),
                        before_state TEXT,
                        after_state TEXT,
                        changed_by VARCHAR(100),
                        database_user VARCHAR(100),
                        actor_source VARCHAR(30),
                        event_timestamp TIMESTAMP WITHOUT TIME ZONE NOT NULL,
                        CONSTRAINT ck_datatrail_capture_operation
                            CHECK (operation IN ('INSERT', 'UPDATE', 'DELETE'))
                    )
                    """.formatted(outboxTable));
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS ix_datatrail_capture_config_event
                    ON %s (config_id, event_id)
                    """.formatted(outboxTable));
            if (!functionCurrent) {
                statement.execute(captureFunctionDdl(quote, connectionId));
                statement.execute(
                        "COMMENT ON FUNCTION " + schema + "."
                                + quoteIdentifier(quote, CAPTURE_FUNCTION)
                                + "() IS " + sqlLiteral(CAPTURE_FUNCTION_VERSION));
            }
        }
    }

    private boolean captureFunctionIsCurrent(Connection connection, Long connectionId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT obj_description(capture_proc.oid, 'pg_proc')
                FROM pg_proc capture_proc
                JOIN pg_namespace function_schema
                  ON function_schema.oid = capture_proc.pronamespace
                WHERE function_schema.nspname = ?
                  AND capture_proc.proname = ?
                  AND capture_proc.pronargs = 0
                """)) {
            statement.setString(1, captureSchema(connectionId));
            statement.setString(2, CAPTURE_FUNCTION);
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        && CAPTURE_FUNCTION_VERSION.equals(result.getString(1));
            }
        }
    }

    static String captureSchema(Long connectionId) {
        return "datatrail_capture_" + connectionId;
    }

    static String qualifiedOutboxTable(String quote, Long connectionId) {
        return quoteIdentifier(quote, captureSchema(connectionId))
                + "." + quoteIdentifier(quote, "outbox_events");
    }

    private void synchronizeTrigger(
            Connection connection,
            MonitoringConfiguration configuration) throws SQLException {
        String quote = connection.getMetaData().getIdentifierQuoteString();
        if (quote == null || quote.isBlank()) {
            throw new SQLException(
                    "The PostgreSQL driver did not provide an identifier quote string.");
        }

        String triggerName = triggerName(configuration.getConfigId());
        String qualifiedTable = quoteIdentifier(quote, configuration.getSchemaName())
                + "." + quoteIdentifier(quote, configuration.getTableName());
        String triggerSql = buildTriggerSql(quote, configuration);
        if (triggerSql == null) {
            dropTrigger(connection, quote, triggerName, qualifiedTable);
            return;
        }

        if (isTriggerCurrent(
                connection,
                configuration,
                triggerName,
                eventMask(configuredEvents(configuration)))) {
            return;
        }

        dropTrigger(connection, quote, triggerName, qualifiedTable);
        try (Statement statement = connection.createStatement()) {
            statement.execute(triggerSql);
        }
    }

    static String buildTriggerSql(
            String quote,
            MonitoringConfiguration configuration) {
        List<String> events = configuredEvents(configuration);
        if (events.isEmpty()) {
            return null;
        }
        Long connectionId = configuration.getConnection().getConnectionId();
        String qualifiedTable = quoteIdentifier(quote, configuration.getSchemaName())
                + "." + quoteIdentifier(quote, configuration.getTableName());
        String arguments = String.join(
                ", ",
                sqlLiteral(configuration.getConfigId().toString()),
                sqlLiteral(timestampLiteral(configuration.getEffectiveFrom())),
                sqlLiteral(timestampLiteral(configuration.getEffectiveTo())),
                sqlLiteral(ZoneId.systemDefault().getId()));
        return "CREATE TRIGGER "
                + quoteIdentifier(quote, triggerName(configuration.getConfigId()))
                + " AFTER " + String.join(" OR ", events)
                + " ON " + qualifiedTable
                + " FOR EACH ROW EXECUTE FUNCTION "
                + quoteIdentifier(quote, captureSchema(connectionId))
                + "." + quoteIdentifier(quote, CAPTURE_FUNCTION)
                + "(" + arguments + ")";
    }

    private boolean isTriggerCurrent(
            Connection connection,
            MonitoringConfiguration configuration,
            String triggerName,
            int expectedEventMask) throws SQLException {
        String sql = """
                SELECT t.tgenabled, t.tgtype::integer AS trigger_type,
                       t.tgargs, p.proname, function_schema.nspname AS function_schema
                FROM pg_trigger t
                JOIN pg_class target_table ON target_table.oid = t.tgrelid
                JOIN pg_namespace target_schema ON target_schema.oid = target_table.relnamespace
                JOIN pg_proc p ON p.oid = t.tgfoid
                JOIN pg_namespace function_schema ON function_schema.oid = p.pronamespace
                WHERE NOT t.tgisinternal
                  AND target_schema.nspname = ?
                  AND target_table.relname = ?
                  AND t.tgname = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, configuration.getSchemaName());
            statement.setString(2, configuration.getTableName());
            statement.setString(3, triggerName);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return false;
                }
                int triggerType = result.getInt("trigger_type");
                boolean correctType = result.getString("tgenabled").equals("O")
                        && (triggerType & 1) == 1
                        && (triggerType & 2) == 0
                        && (triggerType & 64) == 0
                        && (triggerType & (4 | 8 | 16)) == expectedEventMask;
                boolean correctFunction = captureSchema(
                        configuration.getConnection().getConnectionId()).equals(
                        result.getString("function_schema"))
                        && CAPTURE_FUNCTION.equals(result.getString("proname"));
                boolean correctArguments = triggerArgumentsMatch(
                        result.getBytes("tgargs"),
                        configuration);
                return correctType && correctFunction && correctArguments
                        && !result.next();
            }
        }
    }

    private boolean triggerArgumentsMatch(
            byte[] rawArguments,
            MonitoringConfiguration configuration) {
        if (rawArguments == null) {
            return false;
        }
        String[] arguments = new String(rawArguments, StandardCharsets.UTF_8)
                .split("\u0000", -1);
        return arguments.length == 5
                && arguments[0].equals(configuration.getConfigId().toString())
                && arguments[1].equals(timestampLiteral(configuration.getEffectiveFrom()))
                && arguments[2].equals(timestampLiteral(configuration.getEffectiveTo()))
                && arguments[3].equals(ZoneId.systemDefault().getId())
                && arguments[4].isEmpty();
    }

    private void dropTrigger(
            Connection connection,
            String quote,
            String triggerName,
            String qualifiedTable) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "DROP TRIGGER IF EXISTS "
                            + quoteIdentifier(quote, triggerName)
                            + " ON " + qualifiedTable);
        }
    }

    private static List<String> configuredEvents(MonitoringConfiguration configuration) {
        if (!Boolean.TRUE.equals(configuration.getMonitoringEnabled())) {
            return List.of();
        }
        List<String> events = new ArrayList<>(3);
        if (Boolean.TRUE.equals(configuration.getMonitorInsert())) {
            events.add("INSERT");
        }
        if (Boolean.TRUE.equals(configuration.getMonitorUpdate())) {
            events.add("UPDATE");
        }
        if (Boolean.TRUE.equals(configuration.getMonitorDelete())) {
            events.add("DELETE");
        }
        return events;
    }

    private static int eventMask(List<String> events) {
        int mask = 0;
        for (String event : events) {
            mask |= switch (event) {
                case "INSERT" -> 4;
                case "DELETE" -> 8;
                case "UPDATE" -> 16;
                default -> throw new IllegalArgumentException(
                        "Unsupported PostgreSQL trigger event.");
            };
        }
        return mask;
    }

    private static String triggerName(Long configId) {
        return "dt_capture_" + configId;
    }

    private static String timestampLiteral(LocalDateTime value) {
        return value == null ? "" : value.toString();
    }

    private static String quoteIdentifier(String quote, String identifier) {
        return quote + identifier.replace(quote, quote + quote) + quote;
    }

    private static String sqlLiteral(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    static String captureFunctionDdl(String quote, Long connectionId) {
        String schema = quoteIdentifier(quote, captureSchema(connectionId));
        String outboxTable = qualifiedOutboxTable(quote, connectionId);
        return """
                CREATE OR REPLACE FUNCTION %s.%s()
                RETURNS trigger
                LANGUAGE plpgsql
                SECURITY DEFINER
                SET search_path = pg_catalog, %s
                AS $datatrail$
                DECLARE
                    capture_config_id BIGINT := TG_ARGV[0]::BIGINT;
                    capture_time TIMESTAMP WITHOUT TIME ZONE :=
                        clock_timestamp() AT TIME ZONE TG_ARGV[3];
                    effective_from TIMESTAMP WITHOUT TIME ZONE :=
                        NULLIF(TG_ARGV[1], '')::TIMESTAMP;
                    effective_to TIMESTAMP WITHOUT TIME ZONE :=
                        NULLIF(TG_ARGV[2], '')::TIMESTAMP;
                    actor_name TEXT := NULLIF(current_setting('datatrail.actor', TRUE), '');
                    before_row TEXT;
                    after_row TEXT;
                BEGIN
                    IF (effective_from IS NOT NULL AND capture_time < effective_from)
                       OR (effective_to IS NOT NULL AND capture_time > effective_to) THEN
                        IF TG_OP = 'DELETE' THEN
                            RETURN OLD;
                        END IF;
                        RETURN NEW;
                    END IF;

                    IF TG_OP = 'INSERT' THEN
                        after_row := to_jsonb(NEW)::TEXT;
                    ELSIF TG_OP = 'UPDATE' THEN
                        before_row := to_jsonb(OLD)::TEXT;
                        after_row := to_jsonb(NEW)::TEXT;
                    ELSIF TG_OP = 'DELETE' THEN
                        before_row := to_jsonb(OLD)::TEXT;
                    ELSE
                        RAISE EXCEPTION 'Unsupported DataTrail capture operation: %%', TG_OP;
                    END IF;

                    INSERT INTO %s (
                        config_id, operation, transaction_id, before_state, after_state,
                        changed_by, database_user, actor_source, event_timestamp
                    ) VALUES (
                        capture_config_id, TG_OP, txid_current()::TEXT, before_row, after_row,
                        COALESCE(actor_name, session_user), session_user,
                        CASE WHEN actor_name IS NULL THEN 'DATABASE' ELSE 'APPLICATION' END,
                        capture_time
                    );

                    IF TG_OP = 'DELETE' THEN
                        RETURN OLD;
                    END IF;
                    RETURN NEW;
                END;
                $datatrail$
                """.formatted(
                        schema,
                        quoteIdentifier(quote, CAPTURE_FUNCTION),
                        schema,
                        outboxTable);
    }
}
