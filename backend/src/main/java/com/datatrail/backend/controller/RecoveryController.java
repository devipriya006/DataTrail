package com.datatrail.backend.controller;

import com.datatrail.backend.dto.RecoveryPreviewResponse;
import com.datatrail.backend.entity.RecoveryAction;
import com.datatrail.backend.entity.RecoveryExecution;
import com.datatrail.backend.service.RecoveryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/recovery")
public class RecoveryController {

    private final RecoveryService recoveryService;

    public RecoveryController(RecoveryService recoveryService) {
        this.recoveryService = recoveryService;
    }

    @GetMapping("/audit/{auditId}/preview")
    public ResponseEntity<Map<String, Object>> previewRecovery(
            @PathVariable Long auditId,
            Principal principal) {

        RecoveryPreviewResponse preview = recoveryService.previewRecovery(
                auditId,
                principal.getName()
        );

        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("success", true);
        payload.put("auditId", preview.auditId());
        payload.put("tableName", preview.tableName());
        payload.put("recordKey", preview.recordKey());
        payload.put("currentState", preview.currentState());
        payload.put("targetState", preview.targetState());
        payload.put("changedFields", preview.changedFields());
        payload.put("allowed", preview.allowed());
        payload.put("overrideAllowed", preview.overrideAllowed());
        payload.put("status", preview.message());
        payload.put("blastRadiusEstimate", preview.blastRadiusEstimate());
        payload.put("recommendedSql", preview.recommendedSql());
        payload.put("recoveryId", preview.recoveryId());
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/audit/{auditId}/approve")
    public ResponseEntity<Map<String, Object>> approveRecovery(
            @PathVariable Long auditId,
            Principal principal) {

        RecoveryAction recoveryAction = recoveryService.approveRecovery(
                auditId,
                principal.getName()
        );

        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("success", true);
        payload.put("message", "Recovery approved for execution.");
        payload.put("auditId", auditId);
        payload.put("recoveryId", recoveryAction.getRecoveryId());
        payload.put("approvalStatus", recoveryAction.getApprovalStatus());
        payload.put("recoveryType", recoveryAction.getRecoveryType());
        payload.put("approvedBy", recoveryAction.getApprovedBy() != null
                ? recoveryAction.getApprovedBy().getUsername()
                : null);
        payload.put("approvedAt", recoveryAction.getApprovedAt());
        payload.put("recommendedSql", recoveryAction.getRecommendedSql());
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/{recoveryId}/execute")
    public ResponseEntity<Map<String, Object>> executeRecovery(
            @PathVariable Long recoveryId,
            Principal principal) {

        RecoveryExecution execution = recoveryService.executeRecovery(
                recoveryId,
                principal.getName()
        );

        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("success", true);
        payload.put("message", "Recovery executed successfully. The original audit event remains unchanged.");
        payload.put("recoveryId", recoveryId);
        payload.put("executionId", execution.getExecutionId());
        payload.put("executionStatus", execution.getExecutionStatus());
        payload.put("dryRunResult", execution.getDryRunResult());
        payload.put("auditEventId", execution.getAuditEventId());
        payload.put("completedAt", execution.getCompletedAt());
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/audit/{auditId}/restore-anyway")
    public ResponseEntity<Map<String, Object>> restoreAnyway(
            @PathVariable Long auditId,
            @RequestBody Map<String, String> request,
            Principal principal) {

        RecoveryExecution execution = recoveryService.restoreAnyway(
                auditId,
                principal.getName(),
                request.get("reason"),
                request.get("confirmation"));

        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("success", true);
        payload.put("message", "Administrator recovery completed and its PostgreSQL audit event was verified.");
        payload.put("executionId", execution.getExecutionId());
        payload.put("executionStatus", execution.getExecutionStatus());
        payload.put("auditEventId", execution.getAuditEventId());
        payload.put("dryRunResult", execution.getDryRunResult());
        payload.put("completedAt", execution.getCompletedAt());
        return ResponseEntity.ok(payload);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleRecoveryError(
            ResponseStatusException exception) {

        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("success", false);
        payload.put("status", exception.getStatusCode().value());
        payload.put("message", exception.getReason() != null
                ? exception.getReason()
                : "Recovery request failed.");
        return ResponseEntity.status(exception.getStatusCode()).body(payload);
    }
}
