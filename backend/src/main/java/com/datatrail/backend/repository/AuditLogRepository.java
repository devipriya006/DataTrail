package com.datatrail.backend.repository;

import com.datatrail.backend.entity.AuditLog;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select log from AuditLog log where log.auditId = :id")
    java.util.Optional<AuditLog> findByIdForRecoveryUpdate(@Param("id") Long id);

    List<AuditLog> findByConfigurationConfigId(Long configId);

    java.util.Optional<AuditLog> findByConfigurationConfigIdAndSourceEventId(
            Long configId,
            String sourceEventId);

    List<AuditLog> findByConfigurationConnectionConnectionId(Long connectionId);

    List<AuditLog> findByConfigurationConnectionProjectProjectId(Long projectId);

    @Query("""
            SELECT log
            FROM AuditLog log
            WHERE log.configuration.connection.project.projectId = :projectId
              AND log.configuration.connection.connectionId = :connectionId
              AND log.configuration.schemaName = :schemaName
              AND log.configuration.tableName = :tableName
            """)
    List<AuditLog> findForProvenanceCandidates(
            @Param("projectId") Long projectId,
            @Param("connectionId") Long connectionId,
            @Param("schemaName") String schemaName,
            @Param("tableName") String tableName);

    List<AuditLog> findByConfigurationConnectionProjectOwnerUsername(String ownerUsername);

    long countByConfigurationConfigId(Long configId);
}