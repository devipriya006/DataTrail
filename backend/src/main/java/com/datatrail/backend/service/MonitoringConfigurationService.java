package com.datatrail.backend.service;

import com.datatrail.backend.entity.DatabaseConnection;
import com.datatrail.backend.entity.MonitoringConfiguration;
import com.datatrail.backend.dto.MonitoringConfigurationRequest;
import com.datatrail.backend.dto.MonitoringConfigurationResponse;
import com.datatrail.backend.repository.AuditLogRepository;
import com.datatrail.backend.repository.MonitoringConfigurationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class MonitoringConfigurationService {

    private final MonitoringConfigurationRepository monitoringConfigurationRepository;
    private final DatabaseConnectionService databaseConnectionService;
    private final AuditLogRepository auditLogRepository;

    public MonitoringConfigurationService(
            MonitoringConfigurationRepository monitoringConfigurationRepository,
            DatabaseConnectionService databaseConnectionService,
            AuditLogRepository auditLogRepository) {

        this.monitoringConfigurationRepository = monitoringConfigurationRepository;
        this.databaseConnectionService = databaseConnectionService;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public MonitoringConfigurationResponse createOrUpdateConfiguration(
            MonitoringConfigurationRequest request,
            String ownerUsername) {

        if (request == null || request.connectionId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Connection ID is required.");
        }

        DatabaseConnection connection = databaseConnectionService
            .requireOwnedConnection(
                request.connectionId(),
                request.projectId(),
                ownerUsername);
        validateUsableConnection(connection);
        databaseConnectionService.requireTargetBaseTable(
                connection,
                request.schemaName(),
                request.tableName());

        if (request.schemaName() == null || request.tableName() == null) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Schema name and table name are required.");
        }

        MonitoringConfiguration configuration = monitoringConfigurationRepository
                .findFirstByConnectionConnectionIdAndSchemaNameAndTableName(
                        connection.getConnectionId(),
                        request.schemaName(),
                        request.tableName())
                .orElseGet(() -> new MonitoringConfiguration(
                        connection,
                        request.schemaName(),
                        request.tableName(),
                        true,
                        true,
                        true,
                        "NORMAL",
                        true,
                        LocalDateTime.now(),
                        null));

        applyRequest(configuration, request);
        return toResponse(monitoringConfigurationRepository.save(configuration));
    }

    public List<MonitoringConfigurationResponse> getAllConfigurations(String ownerUsername) {
        return monitoringConfigurationRepository
                .findByConnectionProjectOwnerUsername(ownerUsername)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public MonitoringConfigurationResponse getConfigurationById(
            Long id,
            Long projectId,
            String ownerUsername) {
        return toResponse(requireOwnedConfiguration(id, projectId, ownerUsername));
    }

    public List<MonitoringConfigurationResponse> getConfigurationsByConnection(
            Long connectionId,
            Long projectId,
            String ownerUsername) {

        databaseConnectionService.requireOwnedConnection(connectionId, projectId, ownerUsername);
        return monitoringConfigurationRepository
            .findByConnectionConnectionId(connectionId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public MonitoringConfigurationResponse updateConfiguration(
            Long id,
            MonitoringConfigurationRequest request,
            String ownerUsername) {

        MonitoringConfiguration configuration = requireOwnedConfiguration(
            id,
            request.projectId(),
            ownerUsername);
        DatabaseConnection connection = configuration.getConnection();
        if (request.connectionId() != null
                && !request.connectionId().equals(connection.getConnectionId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A monitoring configuration cannot be moved to another connection.");
        }
                if (!configuration.getSchemaName().equals(request.schemaName())
                    || !configuration.getTableName().equals(request.tableName())) {
                    throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "A monitoring configuration's table cannot be changed.");
                }
        applyRequest(configuration, request);
        return toResponse(monitoringConfigurationRepository.save(configuration));
    }

    @Transactional
    public void deleteConfiguration(Long id, Long projectId, String ownerUsername) {
        requireOwnedConfiguration(id, projectId, ownerUsername);
        if (auditLogRepository.countByConfigurationConfigId(id) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This configuration has audit history and cannot be deleted.");
        }
        monitoringConfigurationRepository.deleteById(id);
    }

    private MonitoringConfiguration requireOwnedConfiguration(
            Long id,
            Long projectId,
            String ownerUsername) {
        MonitoringConfiguration configuration = monitoringConfigurationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Monitoring configuration not found."));
        databaseConnectionService.requireOwnedConnection(
                configuration.getConnection().getConnectionId(),
            projectId,
                ownerUsername);
        return configuration;
    }

    private void validateUsableConnection(DatabaseConnection connection) {
        if (connection.getDbType() == null
                || !connection.getDbType().equalsIgnoreCase("PostgreSQL")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Monitoring configuration currently supports PostgreSQL only.");
        }
        if (connection.getConnectionStatus() == null
                || !connection.getConnectionStatus().equalsIgnoreCase("CONNECTED")) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Test the database connection before configuring monitoring.");
        }
    }

    private void applyRequest(
            MonitoringConfiguration configuration,
            MonitoringConfigurationRequest request) {

        if (request.schemaName() == null || request.schemaName().isBlank()
                || request.tableName() == null || request.tableName().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Schema name and table name are required.");
        }

        String sensitivity = request.sensitivityLevel() == null
                ? "NORMAL"
                : request.sensitivityLevel().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("LOW", "NORMAL", "MEDIUM", "HIGH").contains(sensitivity)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Sensitivity level must be LOW, NORMAL, MEDIUM, or HIGH.");
        }

        configuration.setMonitorInsert(defaultTrue(request.monitorInsert()));
        configuration.setMonitorUpdate(defaultTrue(request.monitorUpdate()));
        configuration.setMonitorDelete(defaultTrue(request.monitorDelete()));
        configuration.setSensitivityLevel(sensitivity);
        configuration.setMonitoringEnabled(defaultTrue(request.monitoringEnabled()));
        if (request.effectiveFrom() != null) {
            configuration.setEffectiveFrom(request.effectiveFrom());
        } else if (configuration.getEffectiveFrom() == null) {
            configuration.setEffectiveFrom(LocalDateTime.now());
        }
        configuration.setEffectiveTo(request.effectiveTo());
    }

    private Boolean defaultTrue(Boolean value) {
        return value == null || value;
    }

    private MonitoringConfigurationResponse toResponse(MonitoringConfiguration configuration) {
        return new MonitoringConfigurationResponse(
                configuration.getConfigId(),
                configuration.getConnection().getConnectionId(),
                configuration.getSchemaName(),
                configuration.getTableName(),
                configuration.getMonitorInsert(),
                configuration.getMonitorUpdate(),
                configuration.getMonitorDelete(),
                configuration.getSensitivityLevel(),
                configuration.getMonitoringEnabled(),
                configuration.getEffectiveFrom(),
                configuration.getEffectiveTo());
    }

}