package com.datatrail.backend.controller;

import com.datatrail.backend.entity.EventContext;
import com.datatrail.backend.service.EventContextService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/event-context")
public class EventContextController {

    private final EventContextService eventContextService;

    public EventContextController(EventContextService eventContextService) {
        this.eventContextService = eventContextService;
    }

    @PostMapping
    public EventContext createEventContext(
            @RequestBody Map<String, Object> request) {

        Long auditId = Long.valueOf(
                request.get("auditId").toString()
        );

        String recordKeyJson =
                (String) request.get("recordKeyJson");

        String recordKeyHash =
                (String) request.get("recordKeyHash");

        String transactionId =
                (String) request.get("transactionId");

        Long edgeCountIn = request.get("edgeCountIn") != null
                ? Long.valueOf(request.get("edgeCountIn").toString())
                : null;

        Long edgeCountOut = request.get("edgeCountOut") != null
                ? Long.valueOf(request.get("edgeCountOut").toString())
                : null;

        LocalDateTime builtAt = request.get("builtAt") != null
                ? LocalDateTime.parse(request.get("builtAt").toString())
                : null;

        String buildStatus =
                (String) request.get("buildStatus");

        return eventContextService.createEventContext(
                auditId,
                recordKeyJson,
                recordKeyHash,
                transactionId,
                edgeCountIn,
                edgeCountOut,
                builtAt,
                buildStatus
        );
    }

    @GetMapping
    public List<EventContext> getAllEventContexts() {
        return eventContextService.getAllEventContexts();
    }

    @GetMapping("/{id}")
    public EventContext getEventContextById(
            @PathVariable Long id) {

        return eventContextService.getEventContextById(id);
    }

    @DeleteMapping("/{id}")
    public String deleteEventContext(
            @PathVariable Long id) {

        eventContextService.deleteEventContext(id);

        return "Event context deleted successfully";
    }
}