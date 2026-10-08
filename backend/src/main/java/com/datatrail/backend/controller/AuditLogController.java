package com.datatrail.backend.controller;

import com.datatrail.backend.dto.AuditLogResponse;
import com.datatrail.backend.entity.AuditLog;
import com.datatrail.backend.service.AuditLogService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
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
            @RequestBody Map<String, Object> request,
            Principal principal) {

        if (principal == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Authentication is required.");
        }

        Long configId = Long.valueOf(request.get("configId").toString());
        String operation = (String) request.get("operation");
        String transactionId = (String) request.get("transactionId");
        String beforeState = (String) request.get("beforeState");
        String afterState = (String) request.get("afterState");
        String changedBy = (String) request.get("changedBy");
        LocalDateTime eventTimestamp = LocalDateTime.parse(request.get("eventTimestamp").toString());
        String logHash = (String) request.get("logHash");
        String prevHash = (String) request.get("prevHash");
        Long chainSequence = Long.valueOf(request.get("chainSequence").toString());

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
    public List<AuditLogResponse> getAllAuditLogs(Principal principal) {
        return auditLogService.getAllAuditLogsForOwner(principal.getName());
    }

    @GetMapping("/{id}")
    public AuditLogResponse getAuditLogById(
            @PathVariable Long id,
            Principal principal) {
        return auditLogService.getAuditLogById(id, principal.getName());
    }

    @GetMapping("/project/{projectId}")
    public List<AuditLogResponse> getAuditLogsByProject(
            @PathVariable Long projectId,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String table,
            @RequestParam(required = false) String changedBy,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            Principal principal) {

        return auditLogService.getAuditLogsByProject(
                projectId,
                principal.getName(),
                operation,
                table,
                changedBy,
                dateFrom,
                dateTo
        );
    }

    @GetMapping("/connection/{connectionId}")
    public List<AuditLogResponse> getAuditLogsByConnection(
            @PathVariable Long connectionId,
            @RequestParam Long projectId,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String table,
            @RequestParam(required = false) String changedBy,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            Principal principal) {

        return auditLogService.getAuditLogsByConnection(
                connectionId,
                projectId,
                principal.getName(),
                operation,
                table,
                changedBy,
                dateFrom,
                dateTo
        );
    }

    @GetMapping("/configuration/{configId}")
    public List<AuditLogResponse> getAuditLogsByConfiguration(
            @PathVariable Long configId,
            @RequestParam Long projectId,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String table,
            @RequestParam(required = false) String changedBy,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            Principal principal) {

        return auditLogService.getAuditLogsByConfiguration(
                configId,
                projectId,
                principal.getName(),
                operation,
                table,
                changedBy,
                dateFrom,
                dateTo
        );
    }

    @GetMapping("/configuration/{configId}/verify")
    public Map<String, Object> verifyHashChain(
            @PathVariable Long configId,
            @RequestParam Long projectId,
            Principal principal) {
        return auditLogService.verifyHashChain(configId, projectId, principal.getName());
    }

    @DeleteMapping("/{id}")
    public String deleteAuditLog(
            @PathVariable Long id,
            Principal principal) {

        auditLogService.deleteAuditLog(id, principal.getName());
        return "Audit log deleted successfully";
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleAuditError(
            ResponseStatusException exception) {
        int status = exception.getStatusCode().value();
        String message = exception.getReason() != null
                ? exception.getReason()
                : "Audit log request failed.";

        return ResponseEntity.status(status)
                .body(Map.of("status", status, "message", message));
    }
}