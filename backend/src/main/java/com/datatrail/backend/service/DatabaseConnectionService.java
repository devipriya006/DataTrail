package com.datatrail.backend.service;

import com.datatrail.backend.entity.DatabaseConnection;
import com.datatrail.backend.entity.Project;
import com.datatrail.backend.repository.DatabaseConnectionRepository;
import com.datatrail.backend.repository.ProjectRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DatabaseConnectionService {

    private final DatabaseConnectionRepository databaseConnectionRepository;
    private final ProjectRepository projectRepository;

    public DatabaseConnectionService(
            DatabaseConnectionRepository databaseConnectionRepository,
            ProjectRepository projectRepository) {

        this.databaseConnectionRepository = databaseConnectionRepository;
        this.projectRepository = projectRepository;
    }

    public DatabaseConnection createConnection(
            Long projectId,
            String dbType,
            String host,
            Integer port,
            String databaseName,
            String username,
            String encryptedPassword,
            Boolean sslEnabled,
            String connectionStatus) {

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        DatabaseConnection connection = new DatabaseConnection(
                project,
                dbType,
                host,
                port,
                databaseName,
                username,
                encryptedPassword,
                sslEnabled,
                connectionStatus
        );

        return databaseConnectionRepository.save(connection);
    }

    public List<DatabaseConnection> getAllConnections() {
        return databaseConnectionRepository.findAll();
    }

    public DatabaseConnection getConnectionById(Long id) {
        return databaseConnectionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Database connection not found"));
    }

    public List<DatabaseConnection> getConnectionsByProject(Long projectId) {
        return databaseConnectionRepository.findByProjectProjectId(projectId);
    }

    public void deleteConnection(Long id) {

        if (!databaseConnectionRepository.existsById(id)) {
            throw new RuntimeException("Database connection not found");
        }

        databaseConnectionRepository.deleteById(id);
    }
}