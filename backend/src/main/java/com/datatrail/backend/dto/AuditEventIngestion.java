package com.datatrail.backend.dto;

import java.time.LocalDateTime;

public record AuditEventIngestion(
        String sourceEventId,
        String operation,
        String transactionId,
        String beforeState,
        String afterState,
        String changedBy,
        String databaseUser,
        String actorSource,
        LocalDateTime eventTimestamp) {
}
