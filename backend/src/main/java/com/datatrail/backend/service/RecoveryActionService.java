package com.datatrail.backend.service;

import com.datatrail.backend.entity.ChangeAnalysis;
import com.datatrail.backend.entity.RecoveryAction;
import com.datatrail.backend.entity.User;
import com.datatrail.backend.repository.ChangeAnalysisRepository;
import com.datatrail.backend.repository.RecoveryActionRepository;
import com.datatrail.backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RecoveryActionService {

    private final RecoveryActionRepository recoveryActionRepository;
    private final ChangeAnalysisRepository changeAnalysisRepository;
    private final UserRepository userRepository;

    public RecoveryActionService(
            RecoveryActionRepository recoveryActionRepository,
            ChangeAnalysisRepository changeAnalysisRepository,
            UserRepository userRepository) {

        this.recoveryActionRepository = recoveryActionRepository;
        this.changeAnalysisRepository = changeAnalysisRepository;
        this.userRepository = userRepository;
    }

    public RecoveryAction createRecoveryAction(
            Long analysisId,
            String recommendedSql,
            String recoveryType,
            String approvalStatus,
            Long approvedBy,
            LocalDateTime approvedAt,
            LocalDateTime createdAt) {

        ChangeAnalysis changeAnalysis =
                changeAnalysisRepository.findById(analysisId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Change analysis not found"));

        User user = null;

        if (approvedBy != null) {
            user = userRepository.findById(approvedBy)
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "User not found"));
        }

        RecoveryAction recoveryAction = new RecoveryAction(
                changeAnalysis,
                recommendedSql,
                recoveryType,
                approvalStatus,
                user,
                approvedAt,
                createdAt
        );

        return recoveryActionRepository.save(recoveryAction);
    }

    public List<RecoveryAction> getAllRecoveryActions() {
        return recoveryActionRepository.findAll();
    }

    public RecoveryAction getRecoveryActionById(Long id) {
        return recoveryActionRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Recovery action not found"));
    }

    public void deleteRecoveryAction(Long id) {

        if (!recoveryActionRepository.existsById(id)) {
            throw new RuntimeException(
                    "Recovery action not found");
        }

        recoveryActionRepository.deleteById(id);
    }
}