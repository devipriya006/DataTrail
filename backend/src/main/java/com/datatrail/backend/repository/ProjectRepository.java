package com.datatrail.backend.repository;

import com.datatrail.backend.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByOwnerUserId(Long ownerId);
}