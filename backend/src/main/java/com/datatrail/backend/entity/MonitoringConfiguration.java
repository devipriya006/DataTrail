package com.datatrail.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "monitoring_configurations")
public class MonitoringConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long configId;

    @ManyToOne
    @JoinColumn(name = "connection_id", nullable = false)
    private DatabaseConnection connection;

    @Column(name = "schema_name", nullable = false, length = 100)
    private String schemaName;

    @Column(name = "table_name", nullable = false, length = 100)
    private String tableName;

    @Column(name = "monitor_insert", nullable = false)
    private Boolean monitorInsert = true;

    @Column(name = "monitor_update", nullable = false)
    private Boolean monitorUpdate = true;

    @Column(name = "monitor_delete", nullable = false)
    private Boolean monitorDelete = true;

    @Column(name = "sensitivity_level", nullable = false, length = 30)
    private String sensitivityLevel;

    @Column(name = "monitoring_enabled", nullable = false)
    private Boolean monitoringEnabled = true;

    @Column(name = "effective_from", nullable = false)
    private LocalDateTime effectiveFrom;

    @Column(name = "effective_to")
    private LocalDateTime effectiveTo;

    protected MonitoringConfiguration() {
    }

    public MonitoringConfiguration(
            DatabaseConnection connection,
            String schemaName,
            String tableName,
            Boolean monitorInsert,
            Boolean monitorUpdate,
            Boolean monitorDelete,
            String sensitivityLevel,
            Boolean monitoringEnabled,
            LocalDateTime effectiveFrom,
            LocalDateTime effectiveTo) {

        this.connection = connection;
        this.schemaName = schemaName;
        this.tableName = tableName;
        this.monitorInsert = monitorInsert;
        this.monitorUpdate = monitorUpdate;
        this.monitorDelete = monitorDelete;
        this.sensitivityLevel = sensitivityLevel;
        this.monitoringEnabled = monitoringEnabled;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }

    public Long getConfigId() {
        return configId;
    }

    public void setConfigId(Long configId) {
        this.configId = configId;
    }

    public DatabaseConnection getConnection() {
        return connection;
    }

    public void setConnection(DatabaseConnection connection) {
        this.connection = connection;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public Boolean getMonitorInsert() {
        return monitorInsert;
    }

    public void setMonitorInsert(Boolean monitorInsert) {
        this.monitorInsert = monitorInsert;
    }

    public Boolean getMonitorUpdate() {
        return monitorUpdate;
    }

    public void setMonitorUpdate(Boolean monitorUpdate) {
        this.monitorUpdate = monitorUpdate;
    }

    public Boolean getMonitorDelete() {
        return monitorDelete;
    }

    public void setMonitorDelete(Boolean monitorDelete) {
        this.monitorDelete = monitorDelete;
    }

    public String getSensitivityLevel() {
        return sensitivityLevel;
    }

    public void setSensitivityLevel(String sensitivityLevel) {
        this.sensitivityLevel = sensitivityLevel;
    }

    public Boolean getMonitoringEnabled() {
        return monitoringEnabled;
    }

    public void setMonitoringEnabled(Boolean monitoringEnabled) {
        this.monitoringEnabled = monitoringEnabled;
    }

    public LocalDateTime getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDateTime effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDateTime getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(LocalDateTime effectiveTo) {
        this.effectiveTo = effectiveTo;
    }
}