package com.datatrail.backend.controller;

import com.datatrail.backend.entity.DatabaseConnection;
import com.datatrail.backend.service.DatabaseConnectionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
        Integer port = Integer.valueOf(request.get("port").toString());
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
}