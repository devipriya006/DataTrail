package com.datatrail.backend.service;

import com.datatrail.backend.entity.DatabaseConnection;
import com.datatrail.backend.entity.Project;
import com.datatrail.backend.dto.DatabaseSchemaResponse;
import com.datatrail.backend.dto.DatabaseTableResponse;
import com.datatrail.backend.repository.DatabaseConnectionRepository;
import com.datatrail.backend.repository.MonitoringConfigurationRepository;
import com.datatrail.backend.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class DatabaseConnectionService {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseConnectionService.class);

    private final DatabaseConnectionRepository databaseConnectionRepository;
    private final MonitoringConfigurationRepository monitoringConfigurationRepository;
    private final ProjectRepository projectRepository;

    public DatabaseConnectionService(
            DatabaseConnectionRepository databaseConnectionRepository,
            MonitoringConfigurationRepository monitoringConfigurationRepository,
            ProjectRepository projectRepository) {

        this.databaseConnectionRepository = databaseConnectionRepository;
        this.monitoringConfigurationRepository = monitoringConfigurationRepository;
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

        logger.info(
            "Saving database connection: username={}, host={}, port={}, database={}, passwordPresent={}, passwordEmpty={}, passwordLength={}",
            username,
            host,
            port,
            databaseName,
            encryptedPassword != null,
            encryptedPassword != null && encryptedPassword.isEmpty(),
            encryptedPassword == null ? 0 : encryptedPassword.length());

        validateConnectionDetails(
            host,
            port,
            databaseName,
            username,
            encryptedPassword);

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        DatabaseConnection connection = new DatabaseConnection(
                project,
                dbType,
                host,
                port,
                databaseName,
                username,
                // TODO: Encrypt this value before persistence; it is currently plaintext.
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

        if (monitoringConfigurationRepository.countByConnectionConnectionId(id) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Delete this connection's monitoring configurations before deleting the connection.");
        }

        databaseConnectionRepository.deleteById(id);
    }

    public DatabaseConnection testConnection(Long id) {

        DatabaseConnection connection = getConnectionById(id);
        String storedPassword = connection.getEncryptedPassword();

        logger.info(
            "Testing database connection: username={}, host={}, port={}, database={}, sslEnabled={}, passwordPresent={}, passwordEmpty={}, passwordLength={}",
            connection.getUsername(),
            connection.getHost(),
            connection.getPort(),
            connection.getDatabaseName(),
            Boolean.TRUE.equals(connection.getSslEnabled()),
            storedPassword != null,
            storedPassword != null && storedPassword.isEmpty(),
            storedPassword == null ? 0 : storedPassword.length());

        String dbType = connection.getDbType();

        if (dbType == null || !dbType.equalsIgnoreCase("PostgreSQL")) {
            connection.setConnectionStatus("UNSUPPORTED");
            databaseConnectionRepository.save(connection);

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Connection testing currently supports PostgreSQL only."
            );
        }

        try {
            validateConnectionDetails(
                    connection.getHost(),
                    connection.getPort(),
                    connection.getDatabaseName(),
                    connection.getUsername(),
                    connection.getEncryptedPassword());
        } catch (ResponseStatusException exception) {
            connection.setConnectionStatus("FAILED");
            databaseConnectionRepository.save(connection);
            throw exception;
        }

        try (Connection targetConnection = openTargetConnection(connection)) {

            if (targetConnection.isValid(5)) {

                connection.setConnectionStatus("CONNECTED");
                connection.setLastVerified(LocalDateTime.now());

                return databaseConnectionRepository.save(connection);
            }

            connection.setConnectionStatus("FAILED");
            databaseConnectionRepository.save(connection);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Database connection failed. Please verify the PostgreSQL username and password.");

        } catch (SQLException exception) {

            connection.setConnectionStatus("FAILED");
            databaseConnectionRepository.save(connection);
            logger.warn(
                    "PostgreSQL connection failed: username={}, host={}, port={}, database={}, sqlState={}, errorCode={}",
                    connection.getUsername(),
                    connection.getHost(),
                    connection.getPort(),
                    connection.getDatabaseName(),
                    exception.getSQLState(),
                    exception.getErrorCode());

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Database connection failed. Please verify the PostgreSQL username and password."
            );
        }
    }

    public List<DatabaseSchemaResponse> discoverSchemas(
            Long connectionId,
            Long projectId,
            String ownerUsername) {

        DatabaseConnection connection = requireDiscoveryConnection(
                connectionId,
                projectId,
            ownerUsername,
            "Discover schemas");
        String sql = "SELECT schema_name FROM information_schema.schemata "
                + "WHERE schema_name NOT IN ('pg_catalog', 'information_schema') "
                + "AND schema_name NOT LIKE 'pg\\_%' ESCAPE '\\' "
                + "ORDER BY schema_name";

        try (Connection targetConnection = openTargetConnection(connection);
             PreparedStatement statement = targetConnection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {

            logger.info(
                "Target database connection successful: connectionId={}, databaseName={}",
                connectionId,
                connection.getDatabaseName());

            List<DatabaseSchemaResponse> schemas = new java.util.ArrayList<>();
            while (results.next()) {
                schemas.add(new DatabaseSchemaResponse(results.getString("schema_name")));
            }
            logger.info(
                    "Schemas discovered: connectionId={}, schemaCount={}",
                    connectionId,
                    schemas.size());
            return schemas;
        } catch (SQLException exception) {
            logDiscoveryFailure(connection, exception);
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Unable to connect to the target database.");
        }
    }

    public List<DatabaseTableResponse> discoverTables(
            Long connectionId,
            Long projectId,
            String ownerUsername,
            String schemaName) {

        if (schemaName == null || schemaName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Schema name is required.");
        }

        DatabaseConnection connection = requireDiscoveryConnection(
            connectionId,
            projectId,
            ownerUsername,
            "Discover tables");
        String sql = "SELECT table_schema, table_name, table_type "
                + "FROM information_schema.tables "
                + "WHERE table_schema = ? "
                + "AND table_schema NOT IN ('pg_catalog', 'information_schema') "
                + "AND table_schema NOT LIKE 'pg\\_%' ESCAPE '\\' "
                + "AND table_type = 'BASE TABLE' "
                + "ORDER BY table_name";

        try (Connection targetConnection = openTargetConnection(connection);
             PreparedStatement statement = targetConnection.prepareStatement(sql)) {

            logger.info(
                "Target database connection successful: connectionId={}, databaseName={}",
                connectionId,
                connection.getDatabaseName());

            statement.setString(1, schemaName);
            try (ResultSet results = statement.executeQuery()) {
                List<DatabaseTableResponse> tables = new java.util.ArrayList<>();
                while (results.next()) {
                    tables.add(new DatabaseTableResponse(
                            results.getString("table_schema"),
                            results.getString("table_name"),
                            results.getString("table_type")));
                }
                logger.info(
                        "Tables discovered: connectionId={}, schemaName={}, tableCount={}",
                        connectionId,
                        schemaName,
                        tables.size());
                return tables;
            }
        } catch (SQLException exception) {
            logDiscoveryFailure(connection, exception);
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Unable to connect to the target database.");
        }
    }

    public DatabaseConnection requireOwnedConnection(Long connectionId, String ownerUsername) {
        DatabaseConnection connection = databaseConnectionRepository.findById(connectionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Saved connection does not exist."));
        verifyProjectOwner(connection, ownerUsername);
        return connection;
    }

    public DatabaseConnection requireOwnedConnection(
            Long connectionId,
            Long projectId,
            String ownerUsername) {

        DatabaseConnection connection = requireOwnedConnection(connectionId, ownerUsername);
        if (!Objects.equals(connection.getProject().getProjectId(), projectId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Connection does not belong to this project/user.");
        }
        return connection;
    }

    private DatabaseConnection requireDiscoveryConnection(
            Long connectionId,
            Long projectId,
            String ownerUsername,
            String operation) {

        logger.info(
                "{}: connectionId={}, projectId={}, username={}",
                operation,
                connectionId,
                projectId,
                ownerUsername);

        Optional<DatabaseConnection> result = databaseConnectionRepository.findById(connectionId);
        if (result.isEmpty()) {
            logger.warn(
                    "Connection NOT FOUND: connectionId={}, projectId={}",
                    connectionId,
                    projectId);
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Saved connection does not exist.");
        }

        DatabaseConnection connection = result.get();
        Long actualProjectId = connection.getProject().getProjectId();
        logger.info(
                "Connection found: connectionId={}, projectId={}, databaseName={}, host={}, port={}",
                connectionId,
                actualProjectId,
                connection.getDatabaseName(),
                connection.getHost(),
                connection.getPort());

        if (!Objects.equals(actualProjectId, projectId)) {
            logger.warn(
                    "Connection project mismatch: connectionId={}, requestedProjectId={}, actualProjectId={}",
                    connectionId,
                    projectId,
                    actualProjectId);
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Connection does not belong to this project/user.");
        }
        verifyProjectOwner(connection, ownerUsername);
        return validateDiscoveryConnection(connection);
    }

    private void verifyProjectOwner(DatabaseConnection connection, String ownerUsername) {
        String actualOwner = connection.getProject().getOwner().getUsername();
        if (!Objects.equals(actualOwner, ownerUsername)) {
            logger.warn(
                    "Connection owner mismatch: connectionId={}, projectId={}, username={}",
                    connection.getConnectionId(),
                    connection.getProject().getProjectId(),
                    ownerUsername);
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Connection does not belong to this project/user.");
        }
    }

    private DatabaseConnection validateDiscoveryConnection(DatabaseConnection connection) {
        if (connection.getDbType() == null
                || !connection.getDbType().equalsIgnoreCase("PostgreSQL")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Database discovery currently supports PostgreSQL only.");
        }
        if (connection.getConnectionStatus() == null
                || !connection.getConnectionStatus().equalsIgnoreCase("CONNECTED")) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Test the database connection before discovering tables.");
        }
        validateConnectionDetails(
                connection.getHost(),
                connection.getPort(),
                connection.getDatabaseName(),
                connection.getUsername(),
                connection.getEncryptedPassword());
        return connection;
    }

    public void requireTargetBaseTable(
            DatabaseConnection connection,
            String schemaName,
            String tableName) {

        if (schemaName == null || schemaName.isBlank()
                || tableName == null || tableName.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Schema name and table name are required.");
        }
            if (schemaName.equals("pg_catalog")
                || schemaName.equals("information_schema")
                || schemaName.startsWith("pg_")) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "PostgreSQL system schemas cannot be monitored.");
            }

        String sql = "SELECT 1 FROM information_schema.tables "
                + "WHERE table_schema = ? AND table_name = ? "
                + "AND table_type = 'BASE TABLE'";
        try (Connection targetConnection = openTargetConnection(connection);
             PreparedStatement statement = targetConnection.prepareStatement(sql)) {
            statement.setString(1, schemaName);
            statement.setString(2, tableName);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "The selected table was not found in the target database.");
                }
            }
        } catch (SQLException exception) {
            logDiscoveryFailure(connection, exception);
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Unable to connect to the target database.");
        }
    }

    private Connection openTargetConnection(DatabaseConnection connection) throws SQLException {
        // TODO: Decrypt this plaintext field after credential encryption is implemented.
        return DriverManager.getConnection(
                buildPostgresJdbcUrl(connection),
                connection.getUsername(),
                connection.getEncryptedPassword());
    }

    private void logDiscoveryFailure(DatabaseConnection connection, SQLException exception) {
        logger.warn(
                "Target database discovery failed: connectionId={}, host={}, port={}, database={}, sqlState={}, errorCode={}",
                connection.getConnectionId(),
                connection.getHost(),
                connection.getPort(),
                connection.getDatabaseName(),
                exception.getSQLState(),
                exception.getErrorCode());
    }

    private void validateConnectionDetails(
            String host,
            Integer port,
            String databaseName,
            String username,
            String password) {

        if (host == null || host.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Host is required.");
        }
        if (port == null || port < 1 || port > 65535) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Port must be between 1 and 65535.");
        }
        if (databaseName == null || databaseName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Database name is required.");
        }
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is required.");
        }
        if (password == null || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required.");
        }
    }

    private String buildPostgresJdbcUrl(DatabaseConnection connection) {

        StringBuilder url = new StringBuilder();

        url.append("jdbc:postgresql://")
                .append(connection.getHost())
                .append(":")
                .append(connection.getPort())
                .append("/")
                .append(connection.getDatabaseName());

        if (Boolean.TRUE.equals(connection.getSslEnabled())) {
            url.append("?sslmode=require");
        }

        return url.toString();
    }
}