package com.datatrail.backend.controller;

import com.datatrail.backend.dto.MonitoringConfigurationRequest;
import com.datatrail.backend.dto.MonitoringConfigurationResponse;
import com.datatrail.backend.service.MonitoringConfigurationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
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
    public MonitoringConfigurationResponse createConfiguration(
            @RequestBody MonitoringConfigurationRequest request,
            Principal principal) {
        return monitoringConfigurationService.createOrUpdateConfiguration(
                request,
                principal.getName());
    }

    @GetMapping
    public List<MonitoringConfigurationResponse> getAllConfigurations(Principal principal) {
        return monitoringConfigurationService.getAllConfigurations(principal.getName());
    }

    @GetMapping("/{id}")
    public MonitoringConfigurationResponse getConfigurationById(
            @PathVariable Long id,
                        @RequestParam Long projectId,
            Principal principal) {
                return monitoringConfigurationService.getConfigurationById(
                                id,
                                projectId,
                                principal.getName());
    }

    @GetMapping("/connection/{connectionId}")
    public List<MonitoringConfigurationResponse> getConfigurationsByConnection(
            @PathVariable Long connectionId,
                        @RequestParam Long projectId,
            Principal principal) {
        return monitoringConfigurationService.getConfigurationsByConnection(
                connectionId,
                                projectId,
                principal.getName());
    }

    @PutMapping("/{id}")
    public MonitoringConfigurationResponse updateConfiguration(
            @PathVariable Long id,
            @RequestBody MonitoringConfigurationRequest request,
            Principal principal) {
        return monitoringConfigurationService.updateConfiguration(
                id,
                request,
                principal.getName());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConfiguration(
            @PathVariable Long id,
                        @RequestParam Long projectId,
            Principal principal) {
                monitoringConfigurationService.deleteConfiguration(
                                id,
                                projectId,
                                principal.getName());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleConnectionError(
            ResponseStatusException exception) {
        int status = exception.getStatusCode().value();
        String message = exception.getReason() == null
                ? "Monitoring configuration request failed."
                : exception.getReason();
        return ResponseEntity.status(status)
                .body(Map.of("status", status, "message", message));
    }
}