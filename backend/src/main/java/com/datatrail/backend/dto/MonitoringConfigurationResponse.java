package com.datatrail.backend.dto;

import java.time.LocalDateTime;

public record MonitoringConfigurationResponse(
        Long configId,
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
}
