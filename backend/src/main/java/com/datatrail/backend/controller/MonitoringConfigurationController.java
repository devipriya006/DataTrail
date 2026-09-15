package com.datatrail.backend.controller;

import com.datatrail.backend.entity.MonitoringConfiguration;
import com.datatrail.backend.service.MonitoringConfigurationService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/monitoring-configurations")
public class MonitoringConfigurationController {

    private final MonitoringConfigurationService monitoringConfigurationService;

    public MonitoringConfigurationController(
            MonitoringConfigurationService monitoringConfigurationService) {

        this.monitoringConfigurationService =
                monitoringConfigurationService;
    }

    @PostMapping
    public MonitoringConfiguration createConfiguration(
            @RequestBody Map<String, Object> request) {

        Long connectionId =
                Long.valueOf(request.get("connectionId").toString());

        String schemaName =
                (String) request.get("schemaName");

        String tableName =
                (String) request.get("tableName");

        Boolean monitorInsert =
                Boolean.valueOf(request.get("monitorInsert").toString());

        Boolean monitorUpdate =
                Boolean.valueOf(request.get("monitorUpdate").toString());

        Boolean monitorDelete =
                Boolean.valueOf(request.get("monitorDelete").toString());

        String sensitivityLevel =
                (String) request.get("sensitivityLevel");

        Boolean monitoringEnabled =
                Boolean.valueOf(request.get("monitoringEnabled").toString());

        LocalDateTime effectiveFrom =
                LocalDateTime.parse(request.get("effectiveFrom").toString());

        LocalDateTime effectiveTo = null;

        if (request.get("effectiveTo") != null) {
            effectiveTo =
                    LocalDateTime.parse(request.get("effectiveTo").toString());
        }

        return monitoringConfigurationService.createConfiguration(
                connectionId,
                schemaName,
                tableName,
                monitorInsert,
                monitorUpdate,
                monitorDelete,
                sensitivityLevel,
                monitoringEnabled,
                effectiveFrom,
                effectiveTo
        );
    }

    @GetMapping
    public List<MonitoringConfiguration> getAllConfigurations() {
        return monitoringConfigurationService.getAllConfigurations();
    }

    @GetMapping("/{id}")
    public MonitoringConfiguration getConfigurationById(
            @PathVariable Long id) {

        return monitoringConfigurationService.getConfigurationById(id);
    }

    @GetMapping("/connection/{connectionId}")
    public List<MonitoringConfiguration> getConfigurationsByConnection(
            @PathVariable Long connectionId) {

        return monitoringConfigurationService
                .getConfigurationsByConnection(connectionId);
    }

    @DeleteMapping("/{id}")
    public String deleteConfiguration(@PathVariable Long id) {

        monitoringConfigurationService.deleteConfiguration(id);

        return "Monitoring configuration deleted successfully";
    }
}