package com.datatrail.backend.repository;

import com.datatrail.backend.entity.MonitoringConfiguration;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select configuration from MonitoringConfiguration configuration where configuration.configId = :configId")
        java.util.Optional<MonitoringConfiguration> findByIdForAuditIngestion(
                @Param("configId") Long configId);

        long countByConnectionConnectionId(Long connectionId);
}