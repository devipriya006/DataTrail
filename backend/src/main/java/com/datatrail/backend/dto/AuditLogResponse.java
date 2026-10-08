package com.datatrail.backend.dto;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long auditId,
        Long configId,
        String schemaName,
        String tableName,
        String operation,
        String transactionId,
        String beforeState,
        String afterState,
        String changedBy,
        LocalDateTime eventTimestamp,
        String logHash,
        String prevHash,
        Long chainSequence) {
}
