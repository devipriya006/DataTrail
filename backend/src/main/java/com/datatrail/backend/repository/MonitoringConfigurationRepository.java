package com.datatrail.backend.repository;

import com.datatrail.backend.entity.MonitoringConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MonitoringConfigurationRepository
        extends JpaRepository<MonitoringConfiguration, Long> {

    List<MonitoringConfiguration> findByConnectionConnectionId(Long connectionId);

    List<MonitoringConfiguration> findByConnectionProjectOwnerUsername(String username);

        java.util.Optional<MonitoringConfiguration> findFirstByConnectionConnectionIdAndSchemaNameAndTableName(
            Long connectionId,
            String schemaName,
            String tableName);

        long countByConnectionConnectionId(Long connectionId);
}