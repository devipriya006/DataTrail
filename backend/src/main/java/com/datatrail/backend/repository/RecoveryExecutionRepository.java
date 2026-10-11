package com.datatrail.backend.repository;

import com.datatrail.backend.entity.RecoveryExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RecoveryExecutionRepository extends JpaRepository<RecoveryExecution, Long> {

	@Query("""
			select count(execution) > 0 from RecoveryExecution execution
			where execution.recoveryAction.recoveryId = :recoveryId
			and upper(execution.executionStatus) = 'SUCCESS'
			""")
	boolean hasSuccessfulExecution(@Param("recoveryId") Long recoveryId);

	@Query("""
			select execution from RecoveryExecution execution
			where execution.recoveryAction.recoveryId = :recoveryId
			and upper(execution.executionStatus) = 'RUNNING'
			""")
	List<RecoveryExecution> findRunningExecutions(@Param("recoveryId") Long recoveryId);
}