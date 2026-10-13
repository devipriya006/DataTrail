
package com.datatrail.backend.repository;

import com.datatrail.backend.entity.ProvenanceEdge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProvenanceEdgeRepository
        extends JpaRepository<ProvenanceEdge, Long> {

    @Query("""
            SELECT edge
            FROM ProvenanceEdge edge
            WHERE edge.fromAuditLog.configuration.connection.project.projectId
                    = :projectId
              AND edge.toAuditLog.configuration.connection.project.projectId
                    = :projectId
              AND (
                    edge.fromAuditLog.auditId = :auditId
                    OR edge.toAuditLog.auditId = :auditId
                  )
            """)
    List<ProvenanceEdge> findConnectedEdgesForProject(
            @Param("projectId") Long projectId,
            @Param("auditId") Long auditId);

       @Query("""
            SELECT edge
            FROM ProvenanceEdge edge
            WHERE edge.fromAuditLog.configuration.connection.project.projectId
                    = :projectId
              AND edge.toAuditLog.configuration.connection.project.projectId
                    = :projectId
            """)
    List<ProvenanceEdge> findAllForProject(
            @Param("projectId") Long projectId);

    @Query("""
            SELECT edge
            FROM ProvenanceEdge edge
            WHERE edge.edgeId = :edgeId
              AND edge.fromAuditLog.configuration.connection.project.projectId
                    = :projectId
              AND edge.toAuditLog.configuration.connection.project.projectId
                    = :projectId
            """)
    java.util.Optional<ProvenanceEdge> findByIdForProject(
            @Param("edgeId") Long edgeId,
            @Param("projectId") Long projectId);
}
