package com.datatrail.backend.repository;

import com.datatrail.backend.entity.RecoveryExecution;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecoveryExecutionRepository extends JpaRepository<RecoveryExecution, Long> {
}