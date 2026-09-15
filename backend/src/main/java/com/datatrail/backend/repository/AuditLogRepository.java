package com.datatrail.backend.repository;

import com.datatrail.backend.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByConfigurationConfigId(Long configId);
}