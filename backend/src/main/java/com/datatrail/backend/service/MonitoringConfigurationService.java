package com.datatrail.backend.service;

import com.datatrail.backend.entity.DatabaseConnection;
import com.datatrail.backend.entity.MonitoringConfiguration;
import com.datatrail.backend.repository.DatabaseConnectionRepository;
import com.datatrail.backend.repository.MonitoringConfigurationRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MonitoringConfigurationService {

    private final MonitoringConfigurationRepository monitoringConfigurationRepository;
    private final DatabaseConnectionRepository databaseConnectionRepository;

    public MonitoringConfigurationService(
            MonitoringConfigurationRepository monitoringConfigurationRepository,
            DatabaseConnectionRepository databaseConnectionRepository) {

        this.monitoringConfigurationRepository = monitoringConfigurationRepository;
        this.databaseConnectionRepository = databaseConnectionRepository;
    }

    public MonitoringConfiguration createConfiguration(
            Long connectionId,
            String schemaName,
            String tableName,
            Boolean monitorInsert,
            Boolean monitorUpdate,
            Boolean monitorDelete,
            String sensitivityLevel,
            Boolean monitoringEnabled,
            LocalDateTime effectiveFrom,
            LocalDateTime effectiveTo) {

        DatabaseConnection connection =
                databaseConnectionRepository.findById(connectionId)
                        .orElseThrow(() ->
                                new RuntimeException("Database connection not found"));

        MonitoringConfiguration configuration =
                new MonitoringConfiguration(
                        connection,
                        schemaName,
                        tableName,
                        monitorInsert,
                        monitorUpdate,
                        monitorDelete,
                        sensitivityLevel,
                        monitoringEnabled,
                        effectiveFrom,
                        effectiveTo
                );

        return monitoringConfigurationRepository.save(configuration);
    }

    public List<MonitoringConfiguration> getAllConfigurations() {
        return monitoringConfigurationRepository.findAll();
    }

    public MonitoringConfiguration getConfigurationById(Long id) {
        return monitoringConfigurationRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Monitoring configuration not found"));
    }

    public List<MonitoringConfiguration> getConfigurationsByConnection(
            Long connectionId) {

        return monitoringConfigurationRepository
                .findByConnectionConnectionId(connectionId);
    }

    public void deleteConfiguration(Long id) {

        if (!monitoringConfigurationRepository.existsById(id)) {
            throw new RuntimeException("Monitoring configuration not found");
        }

        monitoringConfigurationRepository.deleteById(id);
    }
}