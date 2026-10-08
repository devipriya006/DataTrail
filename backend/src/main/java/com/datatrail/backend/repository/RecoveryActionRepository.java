package com.datatrail.backend.repository;

import com.datatrail.backend.entity.RecoveryAction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecoveryActionRepository extends JpaRepository<RecoveryAction, Long> {
}