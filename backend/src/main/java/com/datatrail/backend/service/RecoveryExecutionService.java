package com.datatrail.backend.service;

import com.datatrail.backend.entity.RecoveryAction;
import com.datatrail.backend.entity.RecoveryExecution;
import com.datatrail.backend.entity.User;
import com.datatrail.backend.repository.RecoveryActionRepository;
import com.datatrail.backend.repository.RecoveryExecutionRepository;
import com.datatrail.backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RecoveryExecutionService {

    private final RecoveryExecutionRepository recoveryExecutionRepository;
    private final RecoveryActionRepository recoveryActionRepository;
    private final UserRepository userRepository;

    public RecoveryExecutionService(
            RecoveryExecutionRepository recoveryExecutionRepository,
            RecoveryActionRepository recoveryActionRepository,
            UserRepository userRepository) {

        this.recoveryExecutionRepository = recoveryExecutionRepository;
        this.recoveryActionRepository = recoveryActionRepository;
        this.userRepository = userRepository;
    }

    public RecoveryExecution createRecoveryExecution(
            Long recoveryId,
            String executionType,
            String dryRunResult,
            Double blastRadiusEstimate,
            String executionStatus,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            Long executedBy) {

        RecoveryAction recoveryAction =
                recoveryActionRepository.findById(recoveryId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Recovery action not found"));

        User user = null;

        if (executedBy != null) {
            user = userRepository.findById(executedBy)
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "User not found"));
        }

        RecoveryExecution recoveryExecution =
                new RecoveryExecution(
                        recoveryAction,
                        executionType,
                        dryRunResult,
                        blastRadiusEstimate,
                        executionStatus,
                        startedAt,
                        completedAt,
                        user
                );

        return recoveryExecutionRepository.save(recoveryExecution);
    }

    public List<RecoveryExecution> getAllRecoveryExecutions() {
        return recoveryExecutionRepository.findAll();
    }

    public RecoveryExecution getRecoveryExecutionById(Long id) {
        return recoveryExecutionRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Recovery execution not found"));
    }

    public void deleteRecoveryExecution(Long id) {

        if (!recoveryExecutionRepository.existsById(id)) {
            throw new RuntimeException(
                    "Recovery execution not found");
        }

        recoveryExecutionRepository.deleteById(id);
    }
}