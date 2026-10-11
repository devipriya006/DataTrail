package com.datatrail.backend.repository;

import com.datatrail.backend.entity.RecoveryAction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RecoveryActionRepository extends JpaRepository<RecoveryAction, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select action from RecoveryAction action where action.recoveryId = :id")
	Optional<RecoveryAction> findByIdForUpdate(@Param("id") Long id);

	Optional<RecoveryAction> findFirstByAuditLogAuditIdAndRecoveryTypeOrderByRecoveryIdDesc(
			Long auditId,
			String recoveryType);
}