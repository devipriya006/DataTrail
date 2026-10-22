
package com.datatrail.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ProvenanceTraceResponse(
        Long edgeId,
        Long fromAuditId,
        Long toAuditId,
        String relationshipType,
        Double edgeWeight,
        String inferenceMethod,
        Long snapshotId,
        LocalDateTime edgeCreatedAt,
        String fromOperation,
        String fromSchemaName,
        String fromTableName,
        LocalDateTime fromEventTimestamp,
        String toOperation,
        String toSchemaName,
        String toTableName,
        LocalDateTime toEventTimestamp,
        List<String> fromChangedFieldNames,
        Integer fromChangedFieldCount,
        List<String> toChangedFieldNames,
        Integer toChangedFieldCount,
        String fromBeforeState,
        String fromAfterState,
        String toBeforeState,
        String toAfterState
) {
}
