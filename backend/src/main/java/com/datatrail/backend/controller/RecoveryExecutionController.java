package com.datatrail.backend.controller;

import com.datatrail.backend.entity.RecoveryExecution;
import com.datatrail.backend.service.RecoveryExecutionService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/recovery-executions")
public class RecoveryExecutionController {

    private final RecoveryExecutionService recoveryExecutionService;

    public RecoveryExecutionController(
            RecoveryExecutionService recoveryExecutionService) {

        this.recoveryExecutionService = recoveryExecutionService;
    }

    @PostMapping
    public RecoveryExecution createRecoveryExecution(
            @RequestBody Map<String, Object> request) {

        Long recoveryId = Long.valueOf(
                request.get("recoveryId").toString()
        );

        String executionType =
                (String) request.get("executionType");

        String dryRunResult =
                (String) request.get("dryRunResult");

        Double blastRadiusEstimate =
                request.get("blastRadiusEstimate") != null
                        ? Double.valueOf(
                                request.get("blastRadiusEstimate").toString())
                        : null;

        String executionStatus =
                (String) request.get("executionStatus");

        LocalDateTime startedAt =
                request.get("startedAt") != null
                        ? LocalDateTime.parse(
                                request.get("startedAt").toString())
                        : null;

        LocalDateTime completedAt =
                request.get("completedAt") != null
                        ? LocalDateTime.parse(
                                request.get("completedAt").toString())
                        : null;

        Long executedBy =
                request.get("executedBy") != null
                        ? Long.valueOf(
                                request.get("executedBy").toString())
                        : null;

        return recoveryExecutionService.createRecoveryExecution(
                recoveryId,
                executionType,
                dryRunResult,
                blastRadiusEstimate,
                executionStatus,
                startedAt,
                completedAt,
                executedBy
        );
    }

    @GetMapping
    public List<RecoveryExecution> getAllRecoveryExecutions() {
        return recoveryExecutionService.getAllRecoveryExecutions();
    }

    @GetMapping("/{id}")
    public RecoveryExecution getRecoveryExecutionById(
            @PathVariable Long id) {

        return recoveryExecutionService.getRecoveryExecutionById(id);
    }

    @DeleteMapping("/{id}")
    public String deleteRecoveryExecution(
            @PathVariable Long id) {

        recoveryExecutionService.deleteRecoveryExecution(id);

        return "Recovery execution deleted successfully";
    }
}