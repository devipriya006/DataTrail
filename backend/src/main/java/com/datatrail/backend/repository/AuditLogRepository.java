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

    List<AuditLog> findByConfigurationConnectionConnectionId(Long connectionId);

    List<AuditLog> findByConfigurationConnectionProjectProjectId(Long projectId);

    List<AuditLog> findByConfigurationConnectionProjectOwnerUsername(String ownerUsername);

    long countByConfigurationConfigId(Long configId);
}