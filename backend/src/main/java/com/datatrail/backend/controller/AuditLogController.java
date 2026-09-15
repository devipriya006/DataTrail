package com.datatrail.backend.controller;

import com.datatrail.backend.entity.AuditLog;
import com.datatrail.backend.service.AuditLogService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @PostMapping
    public AuditLog createAuditLog(
            @RequestBody Map<String, Object> request) {

        Long configId =
                Long.valueOf(request.get("configId").toString());

        String operation =
                (String) request.get("operation");

        String transactionId =
                (String) request.get("transactionId");

        String beforeState =
                (String) request.get("beforeState");

        String afterState =
                (String) request.get("afterState");

        String changedBy =
                (String) request.get("changedBy");

        LocalDateTime eventTimestamp =
                LocalDateTime.parse(
                        request.get("eventTimestamp").toString());

        String logHash =
                (String) request.get("logHash");

        String prevHash =
                (String) request.get("prevHash");

        Long chainSequence =
                Long.valueOf(request.get("chainSequence").toString());

        return auditLogService.createAuditLog(
                configId,
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
    }

    @GetMapping
    public List<AuditLog> getAllAuditLogs() {
        return auditLogService.getAllAuditLogs();
    }

    @GetMapping("/{id}")
    public AuditLog getAuditLogById(
            @PathVariable Long id) {

        return auditLogService.getAuditLogById(id);
    }

    @GetMapping("/configuration/{configId}")
    public List<AuditLog> getAuditLogsByConfiguration(
            @PathVariable Long configId) {

        return auditLogService
                .getAuditLogsByConfiguration(configId);
    }

    @DeleteMapping("/{id}")
    public String deleteAuditLog(
            @PathVariable Long id) {

        auditLogService.deleteAuditLog(id);

        return "Audit log deleted successfully";
    }
}