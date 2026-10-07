package com.datatrail.backend.controller;

import com.datatrail.backend.dto.DatabaseSchemaResponse;
import com.datatrail.backend.dto.DatabaseTableResponse;
import com.datatrail.backend.entity.DatabaseConnection;
import com.datatrail.backend.service.DatabaseConnectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.security.Principal;

@RestController
@RequestMapping("/database-connections")
public class DatabaseConnectionController {

    private final DatabaseConnectionService databaseConnectionService;

    public DatabaseConnectionController(
            DatabaseConnectionService databaseConnectionService) {

        this.databaseConnectionService = databaseConnectionService;
    }

    @PostMapping
    public DatabaseConnection createConnection(
            @RequestBody Map<String, Object> request) {

        Long projectId = Long.valueOf(request.get("projectId").toString());

        String dbType = (String) request.get("dbType");
        String host = (String) request.get("host");
        Integer port = parsePort(request.get("port"));
        String databaseName = (String) request.get("databaseName");
        String username = (String) request.get("username");
        String encryptedPassword = (String) request.get("encryptedPassword");

        Boolean sslEnabled = request.get("sslEnabled") != null
                ? Boolean.valueOf(request.get("sslEnabled").toString())
                : false;

        String connectionStatus = (String) request.get("connectionStatus");

        return databaseConnectionService.createConnection(
                projectId,
                dbType,
                host,
                port,
                databaseName,
                username,
                encryptedPassword,
                sslEnabled,
                connectionStatus
        );
    }

    @GetMapping
    public List<DatabaseConnection> getAllConnections() {
        return databaseConnectionService.getAllConnections();
    }

    @GetMapping("/{id}")
    public DatabaseConnection getConnectionById(
            @PathVariable Long id) {

        return databaseConnectionService.getConnectionById(id);
    }

    @GetMapping("/project/{projectId}")
    public List<DatabaseConnection> getConnectionsByProject(
            @PathVariable Long projectId) {

        return databaseConnectionService.getConnectionsByProject(projectId);
    }

    @DeleteMapping("/{id}")
    public String deleteConnection(@PathVariable Long id) {

        databaseConnectionService.deleteConnection(id);

        return "Database connection deleted successfully";
    }

        @GetMapping("/{connectionId}/schemas")
    public List<DatabaseSchemaResponse> discoverSchemas(
            @PathVariable Long connectionId,
            @RequestParam Long projectId,
            Principal principal) {
        return databaseConnectionService.discoverSchemas(
            connectionId,
                projectId,
                principal.getName());
    }

        @GetMapping("/{connectionId}/tables")
    public List<DatabaseTableResponse> discoverTables(
            @PathVariable Long connectionId,
            @RequestParam String schemaName,
            @RequestParam Long projectId,
            Principal principal) {
        return databaseConnectionService.discoverTables(
            connectionId,
                projectId,
                principal.getName(),
                schemaName);
    }

    @PostMapping("/{id}/test")
    public DatabaseConnection testConnection(@PathVariable Long id) {
        return databaseConnectionService.testConnection(id);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleConnectionError(
            ResponseStatusException exception) {

        int status = exception.getStatusCode().value();
        String message = exception.getReason() != null
                ? exception.getReason()
                : "Database connection failed.";

        return ResponseEntity.status(status)
                .body(Map.of("status", status, "message", message));
    }

    private Integer parsePort(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.valueOf(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}