package com.datatrail.backend.service;

import com.datatrail.backend.dto.AuditEventIngestion;
import com.datatrail.backend.entity.MonitoringConfiguration;
import com.datatrail.backend.repository.MonitoringConfigurationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(
        name = "datatrail.capture.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class PostgresChangeCapturePoller {

    private static final Logger logger =
            LoggerFactory.getLogger(PostgresChangeCapturePoller.class);
    private static final int BATCH_SIZE = 25;

    private final MonitoringConfigurationRepository configurationRepository;
    private final DatabaseConnectionService databaseConnectionService;
    private final PostgresChangeCaptureInstaller captureInstaller;
    private final AuditLogService auditLogService;

    public PostgresChangeCapturePoller(
            MonitoringConfigurationRepository configurationRepository,
            DatabaseConnectionService databaseConnectionService,
            PostgresChangeCaptureInstaller captureInstaller,
            AuditLogService auditLogService) {
        this.configurationRepository = configurationRepository;
        this.databaseConnectionService = databaseConnectionService;
        this.captureInstaller = captureInstaller;
        this.auditLogService = auditLogService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void installConfiguredTriggers() {
        for (MonitoringConfiguration configuration : configurationRepository.findAll()) {
            if (isPostgres(configuration)) {
                try {
                    captureInstaller.install(configuration);
                } catch (RuntimeException exception) {
                    logger.error(
                            "Could not reconcile PostgreSQL capture trigger: configId={}, connectionId={}",
                            configuration.getConfigId(),
                            configuration.getConnection().getConnectionId(),
                            exception);
                }
            }
        }
    }

    @Scheduled(fixedDelayString = "${datatrail.capture.reconcile-interval-ms:30000}")
    public void reconcileConfiguredTriggers() {
        installConfiguredTriggers();
    }

    @Scheduled(fixedDelayString = "${datatrail.capture.poll-interval-ms:1000}")
    public void pollConfiguredTargets() {
        for (MonitoringConfiguration configuration : configurationRepository.findAll()) {
            if (!isPostgres(configuration)) {
                continue;
            }
            try {
                pollConfiguration(configuration);
            } catch (SQLException | RuntimeException exception) {
                logger.error(
                        "PostgreSQL change-capture delivery failed: configId={}, connectionId={}, sqlState={}",
                        configuration.getConfigId(),
                        configuration.getConnection().getConnectionId(),
                        exception instanceof SQLException sqlException
                                ? sqlException.getSQLState()
                                : null,
                        exception);
            }
        }
    }

    private void pollConfiguration(MonitoringConfiguration configuration)
            throws SQLException {
        try (Connection target = databaseConnectionService.openTargetConnection(
                configuration.getConnection())) {
            target.setAutoCommit(false);
            Long connectionId = configuration.getConnection().getConnectionId();
            if (!PostgresChangeCaptureInstaller.captureObjectsExist(
                    target,
                    connectionId)) {
                target.commit();
                return;
            }
            if (!acquirePollLock(target, configuration.getConfigId())) {
                target.rollback();
                return;
            }

            String outboxTable = outboxTableName(target, connectionId);
            List<OutboxEvent> events = readPendingEvents(
                    target,
                    configuration.getConfigId(),
                    outboxTable);
            for (OutboxEvent event : events) {
                auditLogService.ingestEvent(
                        configuration.getConfigId(),
                        new AuditEventIngestion(
                                Long.toString(event.eventId()),
                                event.operation(),
                                event.transactionId(),
                                event.beforeState(),
                                event.afterState(),
                                event.changedBy(),
                                event.databaseUser(),
                                event.actorSource(),
                                event.eventTimestamp()));
                acknowledge(target, outboxTable, event.eventId());
            }
            target.commit();
        }
    }

    private boolean acquirePollLock(Connection target, Long configId)
            throws SQLException {
        try (PreparedStatement statement = target.prepareStatement(
                "SELECT pg_try_advisory_xact_lock(hashtextextended(?, 0))")) {
            statement.setString(1, PostgresChangeCaptureInstaller.lockKey(configId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        }
    }

    private String outboxTableName(Connection target, Long connectionId)
            throws SQLException {
        String quote = target.getMetaData().getIdentifierQuoteString();
        if (quote == null || quote.isBlank()) {
            throw new SQLException(
                    "The PostgreSQL driver did not provide an identifier quote string.");
        }
        return PostgresChangeCaptureInstaller.qualifiedOutboxTable(quote, connectionId);
    }

    private List<OutboxEvent> readPendingEvents(
            Connection target,
            Long configId,
            String outboxTable) throws SQLException {
        String sql = """
                SELECT event_id, operation, transaction_id, before_state, after_state,
                       changed_by, database_user, actor_source, event_timestamp
                FROM %s
                WHERE config_id = ?
                ORDER BY event_id
                LIMIT ?
                FOR UPDATE
                """.formatted(outboxTable);
        try (PreparedStatement statement = target.prepareStatement(sql)) {
            statement.setLong(1, configId);
            statement.setInt(2, BATCH_SIZE);
            try (ResultSet result = statement.executeQuery()) {
                List<OutboxEvent> events = new ArrayList<>();
                while (result.next()) {
                    events.add(new OutboxEvent(
                            result.getLong("event_id"),
                            result.getString("operation"),
                            result.getString("transaction_id"),
                            result.getString("before_state"),
                            result.getString("after_state"),
                            result.getString("changed_by"),
                            result.getString("database_user"),
                            result.getString("actor_source"),
                            result.getObject("event_timestamp", LocalDateTime.class)));
                }
                return events;
            }
        }
    }

    private void acknowledge(
            Connection target,
            String outboxTable,
            long eventId) throws SQLException {
        try (PreparedStatement statement = target.prepareStatement(
                "DELETE FROM " + outboxTable + " WHERE event_id = ?")) {
            statement.setLong(1, eventId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException(
                        "The captured event could not be acknowledged in the outbox.");
            }
        }
    }

    private boolean isPostgres(MonitoringConfiguration configuration) {
        return configuration.getConnection() != null
                && "PostgreSQL".equalsIgnoreCase(
                        configuration.getConnection().getDbType());
    }

    private record OutboxEvent(
            long eventId,
            String operation,
            String transactionId,
            String beforeState,
            String afterState,
            String changedBy,
            String databaseUser,
            String actorSource,
            LocalDateTime eventTimestamp) {
    }
}
