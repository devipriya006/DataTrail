package com.datatrail.backend.dto;

public record ProvenanceCandidateResponse(
        Long fromAuditId,
        Long toAuditId,
        String explanation) {
}
