package com.datatrail.backend.repository;

import com.datatrail.backend.entity.DatabaseConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DatabaseConnectionRepository
        extends JpaRepository<DatabaseConnection, Long> {

    List<DatabaseConnection> findByProjectProjectId(Long projectId);
}