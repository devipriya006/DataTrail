package com.datatrail.backend.repository;

import com.datatrail.backend.entity.MonitoringConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MonitoringConfigurationRepository
        extends JpaRepository<MonitoringConfiguration, Long> {

    List<MonitoringConfiguration> findByConnectionConnectionId(Long connectionId);
}