package com.datatrail.backend.dto;

public record DatabaseTableResponse(
        String schemaName,
        String tableName,
        String tableType) {
}
