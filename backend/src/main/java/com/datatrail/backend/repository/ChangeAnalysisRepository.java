package com.datatrail.backend.repository;

import com.datatrail.backend.entity.ChangeAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChangeAnalysisRepository extends JpaRepository<ChangeAnalysis, Long> {
}