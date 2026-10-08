package com.datatrail.backend.controller;

import com.datatrail.backend.entity.RecoveryAction;
import com.datatrail.backend.service.RecoveryActionService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/recovery-actions")
public class RecoveryActionController {

    private final RecoveryActionService recoveryActionService;

    public RecoveryActionController(
            RecoveryActionService recoveryActionService) {

        this.recoveryActionService = recoveryActionService;
    }

    @PostMapping
    public RecoveryAction createRecoveryAction(
            @RequestBody Map<String, Object> request) {

        Long analysisId = Long.valueOf(
                request.get("analysisId").toString()
        );

        String recommendedSql =
                (String) request.get("recommendedSql");

        String recoveryType =
                (String) request.get("recoveryType");

        String approvalStatus =
                (String) request.get("approvalStatus");

        Long approvedBy = request.get("approvedBy") != null
                ? Long.valueOf(request.get("approvedBy").toString())
                : null;

        LocalDateTime approvedAt = request.get("approvedAt") != null
                ? LocalDateTime.parse(request.get("approvedAt").toString())
                : null;

        LocalDateTime createdAt = request.get("createdAt") != null
                ? LocalDateTime.parse(request.get("createdAt").toString())
                : null;

        return recoveryActionService.createRecoveryAction(
                analysisId,
                recommendedSql,
                recoveryType,
                approvalStatus,
                approvedBy,
                approvedAt,
                createdAt
        );
    }

    @GetMapping
    public List<RecoveryAction> getAllRecoveryActions() {
        return recoveryActionService.getAllRecoveryActions();
    }

    @GetMapping("/{id}")
    public RecoveryAction getRecoveryActionById(
            @PathVariable Long id) {

        return recoveryActionService.getRecoveryActionById(id);
    }

    @DeleteMapping("/{id}")
    public String deleteRecoveryAction(
            @PathVariable Long id) {

        recoveryActionService.deleteRecoveryAction(id);

        return "Recovery action deleted successfully";
    }
}