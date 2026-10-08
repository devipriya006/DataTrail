package com.datatrail.backend.service;

import com.datatrail.backend.entity.AuditLog;
import com.datatrail.backend.entity.EventContext;
import com.datatrail.backend.repository.AuditLogRepository;
import com.datatrail.backend.repository.EventContextRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class EventContextService {

    private final EventContextRepository eventContextRepository;
    private final AuditLogRepository auditLogRepository;

    public EventContextService(
            EventContextRepository eventContextRepository,
            AuditLogRepository auditLogRepository) {

        this.eventContextRepository = eventContextRepository;
        this.auditLogRepository = auditLogRepository;
    }

    public EventContext createEventContext(
            Long auditId,
            String recordKeyJson,
            String recordKeyHash,
            String transactionId,
            Long edgeCountIn,
            Long edgeCountOut,
            LocalDateTime builtAt,
            String buildStatus) {

        AuditLog auditLog = auditLogRepository.findById(auditId)
                .orElseThrow(() ->
                        new RuntimeException("Audit log not found"));

        EventContext eventContext = new EventContext(
                auditLog,
                recordKeyJson,
                recordKeyHash,
                transactionId,
                edgeCountIn,
                edgeCountOut,
                builtAt,
                buildStatus
        );

        return eventContextRepository.save(eventContext);
    }

    public List<EventContext> getAllEventContexts() {
        return eventContextRepository.findAll();
    }

    public EventContext getEventContextById(Long id) {
        return eventContextRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Event context not found"));
    }

    public void deleteEventContext(Long id) {

        if (!eventContextRepository.existsById(id)) {
            throw new RuntimeException("Event context not found");
        }

        eventContextRepository.deleteById(id);
    }
}