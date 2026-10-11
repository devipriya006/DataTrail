package com.datatrail.backend.dto;

import java.util.List;
import java.util.Map;

public record RecoveryPreviewResponse(
        Long auditId,
        String tableName,
        String recordKey,
        Map<String, Object> currentState,
        Map<String, Object> targetState,
        List<String> changedFields,
        boolean allowed,
        boolean overrideAllowed,
        String message,
        Double blastRadiusEstimate,
        String recommendedSql,
        Long recoveryId
) {
}
