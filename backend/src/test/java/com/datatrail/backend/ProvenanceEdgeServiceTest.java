
package com.datatrail.backend;

import com.datatrail.backend.dto.ProvenanceTraceResponse;
import com.datatrail.backend.dto.AuditLogResponse;
import com.datatrail.backend.entity.*;
import com.datatrail.backend.repository.AuditLogRepository;
import com.datatrail.backend.repository.ProvenanceEdgeRepository;
import com.datatrail.backend.service.AuditLogService;
import com.datatrail.backend.service.DatabaseConnectionService;
import com.datatrail.backend.service.ProvenanceEdgeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProvenanceEdgeServiceTest {

    @Mock
    private ProvenanceEdgeRepository provenanceEdgeRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private DatabaseConnectionService databaseConnectionService;

    @InjectMocks
    private ProvenanceEdgeService provenanceEdgeService;

    @Test
    void deniesTraceWhenProjectAccessIsRejected() {
        doThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Forbidden"))
                .when(auditLogService)
                .requireProjectAccess(10L, "other-user");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> provenanceEdgeService.traceAuditEvent(
                        10L, 99L, "other-user"));

        assertEquals(403, exception.getStatusCode().value());
        verifyNoInteractions(provenanceEdgeRepository);
    }

    @Test
    void deniesCandidateDiscoveryWhenProjectAccessIsRejected() {
        doThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Forbidden"))
                .when(auditLogService)
                .requireProjectAccess(10L, "other-user");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> provenanceEdgeService.discoverCandidates(
                        10L, 99L, "other-user"));

        assertEquals(403, exception.getStatusCode().value());
        verifyNoInteractions(auditLogRepository);
        verifyNoInteractions(databaseConnectionService);
    }

    @Test
    void returnsNotFoundWhenAuditEventDoesNotExist() {
        when(auditLogRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> provenanceEdgeService.traceAuditEvent(
                        10L, 99L, "owner"));

        assertEquals(404, exception.getStatusCode().value());
        verifyNoInteractions(provenanceEdgeRepository);
    }

    @Test
    void rejectsAuditEventFromAnotherProject() {
        AuditLog auditLog = createAuditLog(20L);

        when(auditLogRepository.findById(99L))
                .thenReturn(Optional.of(auditLog));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> provenanceEdgeService.traceAuditEvent(
                        10L, 99L, "owner"));

        assertEquals(404, exception.getStatusCode().value());
        verifyNoInteractions(provenanceEdgeRepository);
    }

    @Test
    void returnsConnectedTraceForAuthorizedProject() {
        AuditLog from = createAuditLog(10L);
        from.setAuditId(98L);

        AuditLog to = createAuditLog(10L);
        to.setAuditId(99L);

        ProvenanceEdge edge = new ProvenanceEdge(
                from, to, "DATA_FLOW", 0.9, "AUDIT_BASED", 7L,
                LocalDateTime.now());

        when(auditLogRepository.findById(99L))
                .thenReturn(Optional.of(to));
        when(auditLogService.toAuditLogResponse(from))
                .thenReturn(auditLogResponse(
                        from, "{\"status\":\"old\"}",
                        "{\"status\":\"new\"}"));
        when(auditLogService.toAuditLogResponse(to))
                .thenReturn(auditLogResponse(
                        to, "{\"name\":\"before\"}",
                        "{\"name\":\"after\"}"));
        when(provenanceEdgeRepository.findConnectedEdgesForProject(
                10L, 99L))
                .thenReturn(List.of(edge));

        List<ProvenanceTraceResponse> result =
                provenanceEdgeService.traceAuditEvent(
                        10L, 99L, "owner");

        assertEquals(1, result.size());
        assertEquals(98L, result.get(0).fromAuditId());
        assertEquals(99L, result.get(0).toAuditId());
        assertEquals("DATA_FLOW", result.get(0).relationshipType());
        assertEquals(List.of("status"),
                result.get(0).fromChangedFieldNames());
        assertEquals(1, result.get(0).fromChangedFieldCount());
        assertEquals(List.of("name"),
                result.get(0).toChangedFieldNames());
        assertEquals(1, result.get(0).toChangedFieldCount());
        assertEquals("{\"status\":\"old\"}",
                result.get(0).fromBeforeState());
        assertEquals("{\"status\":\"new\"}",
                result.get(0).fromAfterState());
        assertEquals("{\"name\":\"before\"}",
                result.get(0).toBeforeState());
        assertEquals("{\"name\":\"after\"}",
                result.get(0).toAfterState());

        verify(auditLogService)
                .requireProjectAccess(10L, "owner");
        verify(provenanceEdgeRepository)
                .findConnectedEdgesForProject(10L, 99L);
    }

    @Test
    void returnsEmptyTraceWhenProjectHasNoConnectedEdges() {
        AuditLog auditLog = createAuditLog(10L);

        when(auditLogRepository.findById(99L))
                .thenReturn(Optional.of(auditLog));
        when(provenanceEdgeRepository.findConnectedEdgesForProject(
                10L, 99L))
                .thenReturn(List.of());

        List<ProvenanceTraceResponse> result =
                provenanceEdgeService.traceAuditEvent(
                        10L, 99L, "owner");

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(auditLogService)
                .requireProjectAccess(10L, "owner");
        verify(provenanceEdgeRepository)
                .findConnectedEdgesForProject(10L, 99L);
    }

    @Test
    void marksChangedFieldSummaryUnavailableForMalformedSnapshot() {
        AuditLog from = createAuditLog(10L);
        from.setAuditId(98L);

        AuditLog to = createAuditLog(10L);
        to.setAuditId(99L);

        ProvenanceEdge edge = new ProvenanceEdge(
                from, to, "DATA_FLOW", null, null, null,
                LocalDateTime.now());

        when(auditLogRepository.findById(99L))
                .thenReturn(Optional.of(to));
        when(auditLogService.toAuditLogResponse(from))
                .thenReturn(auditLogResponse(from, "not-json", "{}"));
        when(auditLogService.toAuditLogResponse(to))
                .thenReturn(auditLogResponse(to, "{}", "{}"));
        when(provenanceEdgeRepository.findConnectedEdgesForProject(
                10L, 99L))
                .thenReturn(List.of(edge));

        List<ProvenanceTraceResponse> result =
                provenanceEdgeService.traceAuditEvent(10L, 99L, "owner");

        assertTrue(result.get(0).fromChangedFieldNames().isEmpty());
        assertNull(result.get(0).fromChangedFieldCount());
        assertEquals(0, result.get(0).toChangedFieldCount());
    }

    @Test
    void marksUpdateSummaryUnavailableWhenSnapshotIsMissing() {
        AuditLog from = createAuditLog(10L);
        from.setAuditId(98L);

        AuditLog to = createAuditLog(10L);
        to.setAuditId(99L);

        ProvenanceEdge edge = new ProvenanceEdge(
                from, to, "DATA_FLOW", null, null, null,
                LocalDateTime.now());

        when(auditLogRepository.findById(99L))
                .thenReturn(Optional.of(to));
        when(auditLogService.toAuditLogResponse(from))
                .thenReturn(auditLogResponse(from, "{}", null));
        when(auditLogService.toAuditLogResponse(to))
                .thenReturn(auditLogResponse(to, "{}", "{}"));
        when(provenanceEdgeRepository.findConnectedEdgesForProject(
                10L, 99L))
                .thenReturn(List.of(edge));

        List<ProvenanceTraceResponse> result =
                provenanceEdgeService.traceAuditEvent(10L, 99L, "owner");

        assertTrue(result.get(0).fromChangedFieldNames().isEmpty());
        assertNull(result.get(0).fromChangedFieldCount());
    }

    @Test
    void discoversChronologicalCandidateForMatchingPrimaryKey() throws Exception {
        AuditLog selected = createAuditLog(10L);
        selected.setAuditId(99L);
        selected.setEventTimestamp(LocalDateTime.parse("2026-01-02T10:00:00"));
        AuditLog earlier = createAuditLog(10L);
        earlier.setAuditId(98L);
        earlier.setEventTimestamp(LocalDateTime.parse("2026-01-01T10:00:00"));
        mockCandidateDiscovery(
                selected,
                List.of(selected, earlier),
                "{\"account_uuid\":\"key-1\",\"name\":\"old\"}",
                "{\"account_uuid\":\"key-1\",\"name\":\"current\"}",
                "{\"account_uuid\":\"key-1\",\"name\":\"before\"}",
                "{\"account_uuid\":\"key-1\",\"name\":\"after\"}");

        var candidates = provenanceEdgeService.discoverCandidates(
                10L, 99L, "owner");

        assertEquals(1, candidates.size());
        assertEquals(98L, candidates.get(0).fromAuditId());
        assertEquals(99L, candidates.get(0).toAuditId());
        assertTrue(candidates.get(0).explanation().contains("account_uuid"));
        assertTrue(candidates.get(0).explanation().contains("not causation"));
        verify(provenanceEdgeRepository).findAllForProject(10L);
    }

    @Test
    void doesNotSuggestEventsWithDifferentPrimaryKeyValues() throws Exception {
        AuditLog selected = createAuditLog(10L);
        selected.setAuditId(99L);
        AuditLog unrelated = createAuditLog(10L);
        unrelated.setAuditId(98L);
        mockCandidateDiscovery(
                selected,
                List.of(selected, unrelated),
                "{\"account_uuid\":\"key-1\"}",
                "{\"account_uuid\":\"key-1\"}",
                "{\"account_uuid\":\"key-2\"}",
                "{\"account_uuid\":\"key-2\"}");

        assertTrue(provenanceEdgeService.discoverCandidates(
                10L, 99L, "owner").isEmpty());
    }

    @Test
    void doesNotSuggestEventsWithoutSnapshotPrimaryKeyEvidence() throws Exception {
        AuditLog selected = createAuditLog(10L);
        selected.setAuditId(99L);
        AuditLog event = createAuditLog(10L);
        event.setAuditId(98L);
        mockCandidateDiscovery(
                selected,
                List.of(selected, event),
                "{\"ordinary_field\":\"same\"}",
                "{\"ordinary_field\":\"same\"}",
                "{\"ordinary_field\":\"same\"}",
                "{\"ordinary_field\":\"same\"}");

        assertTrue(provenanceEdgeService.discoverCandidates(
                10L, 99L, "owner").isEmpty());
        verify(provenanceEdgeRepository, never()).findAllForProject(10L);
    }

    @Test
    void rejectsCandidateDiscoveryForAuditEventFromAnotherProject() {
        AuditLog selected = createAuditLog(20L);
        selected.setAuditId(99L);
        when(auditLogRepository.findById(99L))
                .thenReturn(Optional.of(selected));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> provenanceEdgeService.discoverCandidates(
                        10L, 99L, "owner"));

        assertEquals(404, exception.getStatusCode().value());
        verifyNoInteractions(provenanceEdgeRepository, databaseConnectionService);
        verify(auditLogRepository, never())
                .findForProvenanceCandidates(any(), any(), any(), any());
    }

    private void mockCandidateDiscovery(
            AuditLog selected,
            List<AuditLog> events,
            String selectedBefore,
            String selectedAfter,
            String otherBefore,
            String otherAfter) throws Exception {
        AuditLog other = events.stream()
                .filter(event -> !event.getAuditId().equals(selected.getAuditId()))
                .findFirst()
                .orElseThrow();
        when(auditLogRepository.findById(selected.getAuditId()))
                .thenReturn(Optional.of(selected));
        when(auditLogService.toAuditLogResponse(selected))
                .thenReturn(auditLogResponse(selected, selectedBefore, selectedAfter));
        if (selectedBefore.contains("\"account_uuid\"")
                || selectedAfter.contains("\"account_uuid\"")) {
            when(auditLogRepository.findForProvenanceCandidates(
                    10L, 30L, "public", "users"))
                    .thenReturn(events);
            when(auditLogService.toAuditLogResponse(other))
                    .thenReturn(auditLogResponse(other, otherBefore, otherAfter));
            when(provenanceEdgeRepository.findAllForProject(10L))
                    .thenReturn(List.of());
        }

        Connection target = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        ResultSet primaryKeys = mock(ResultSet.class);
        when(databaseConnectionService.openTargetConnection(
                selected.getConfiguration().getConnection()))
                .thenReturn(target);
        when(target.getMetaData()).thenReturn(metadata);
        when(target.getCatalog()).thenReturn("DataTrail");
        when(metadata.getPrimaryKeys("DataTrail", "public", "users"))
                .thenReturn(primaryKeys);
        when(primaryKeys.next()).thenReturn(true, false);
        when(primaryKeys.getShort("KEY_SEQ")).thenReturn((short) 1);
        when(primaryKeys.getString("COLUMN_NAME")).thenReturn("account_uuid");
    }

    private AuditLog createAuditLog(Long projectId) {
        Role role = new Role("USER", "Standard user role");
        User owner = new User(
                "owner", "owner@example.com", "hash", role);

        Project project = new Project(
                owner, "Project", "Test project");
        project.setProjectId(projectId);

        DatabaseConnection connection = new DatabaseConnection(
                project, "PostgreSQL", "localhost", 5432,
                "DataTrail", "app_user", "secret", true, "CONNECTED");
        connection.setConnectionId(30L);

        MonitoringConfiguration configuration =
                new MonitoringConfiguration(
                        connection, "public", "users",
                        true, true, true, "NORMAL", true,
                        LocalDateTime.now(), null);

        AuditLog auditLog = new AuditLog(
                configuration, "UPDATE", "tx-1",
                "{}", "{}", "postgres", LocalDateTime.now(),
                "hash-1", "prev-hash", 1L);

        auditLog.setAuditId(99L);
        return auditLog;
    }

        private AuditLogResponse auditLogResponse(
                        AuditLog auditLog,
                        String beforeState,
                        String afterState) {

                MonitoringConfiguration configuration = auditLog.getConfiguration();
                return new AuditLogResponse(
                                auditLog.getAuditId(),
                                configuration.getConfigId(),
                                configuration.getSchemaName(),
                                configuration.getTableName(),
                                auditLog.getOperation(),
                                auditLog.getTransactionId(),
                                beforeState,
                                afterState,
                                auditLog.getChangedBy(),
                                auditLog.getDatabaseUser(),
                                auditLog.getActorSource(),
                                auditLog.getEventTimestamp(),
                                auditLog.getLogHash(),
                                auditLog.getPrevHash(),
                                auditLog.getChainSequence());
        }
}
