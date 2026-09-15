package com.datatrail.backend.service;

import com.datatrail.backend.entity.AuditLog;
import com.datatrail.backend.entity.MonitoringConfiguration;
import com.datatrail.backend.repository.AuditLogRepository;
import com.datatrail.backend.repository.MonitoringConfigurationRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final MonitoringConfigurationRepository monitoringConfigurationRepository;

    public AuditLogService(
            AuditLogRepository auditLogRepository,
            MonitoringConfigurationRepository monitoringConfigurationRepository) {

        this.auditLogRepository = auditLogRepository;
        this.monitoringConfigurationRepository =
                monitoringConfigurationRepository;
    }

    public AuditLog createAuditLog(
            Long configId,
            String operation,
            String transactionId,
            String beforeState,
            String afterState,
            String changedBy,
            LocalDateTime eventTimestamp,
            String logHash,
            String prevHash,
            Long chainSequence) {

        MonitoringConfiguration configuration =
                monitoringConfigurationRepository.findById(configId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Monitoring configuration not found"));

        AuditLog auditLog = new AuditLog(
                configuration,
                operation,
                transactionId,
                beforeState,
                afterState,
                changedBy,
                eventTimestamp,
                logHash,
                prevHash,
                chainSequence
        );

        return auditLogRepository.save(auditLog);
    }

    public List<AuditLog> getAllAuditLogs() {
        return auditLogRepository.findAll();
    }

    public AuditLog getAuditLogById(Long id) {
        return auditLogRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Audit log not found"));
    }

    public List<AuditLog> getAuditLogsByConfiguration(Long configId) {
        return auditLogRepository
                .findByConfigurationConfigId(configId);
    }

    public void deleteAuditLog(Long id) {

        if (!auditLogRepository.existsById(id)) {
            throw new RuntimeException("Audit log not found");
        }

        auditLogRepository.deleteById(id);
    }
}