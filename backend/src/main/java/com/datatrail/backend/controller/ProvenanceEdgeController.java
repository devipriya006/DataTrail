package com.datatrail.backend.controller;

import com.datatrail.backend.entity.ProvenanceEdge;
import com.datatrail.backend.service.ProvenanceEdgeService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/provenance-edges")
public class ProvenanceEdgeController {

    private final ProvenanceEdgeService provenanceEdgeService;

    public ProvenanceEdgeController(
            ProvenanceEdgeService provenanceEdgeService) {

        this.provenanceEdgeService = provenanceEdgeService;
    }

    @PostMapping
    public ProvenanceEdge createProvenanceEdge(
            @RequestBody Map<String, Object> request) {

        Long fromAuditId = Long.valueOf(
                request.get("fromAuditId").toString()
        );

        Long toAuditId = Long.valueOf(
                request.get("toAuditId").toString()
        );

        String relationshipType =
                (String) request.get("relationshipType");

        Double edgeWeight = request.get("edgeWeight") != null
                ? Double.valueOf(request.get("edgeWeight").toString())
                : null;

        String inferenceMethod =
                (String) request.get("inferenceMethod");

        Long snapshotId = request.get("snapshotId") != null
                ? Long.valueOf(request.get("snapshotId").toString())
                : null;

        LocalDateTime createdAt = request.get("createdAt") != null
                ? LocalDateTime.parse(request.get("createdAt").toString())
                : null;

        return provenanceEdgeService.createProvenanceEdge(
                fromAuditId,
                toAuditId,
                relationshipType,
                edgeWeight,
                inferenceMethod,
                snapshotId,
                createdAt
        );
    }

    @GetMapping
    public List<ProvenanceEdge> getAllProvenanceEdges() {
        return provenanceEdgeService.getAllProvenanceEdges();
    }

    @GetMapping("/{id}")
    public ProvenanceEdge getProvenanceEdgeById(
            @PathVariable Long id) {

        return provenanceEdgeService.getProvenanceEdgeById(id);
    }

    @DeleteMapping("/{id}")
    public String deleteProvenanceEdge(
            @PathVariable Long id) {

        provenanceEdgeService.deleteProvenanceEdge(id);

        return "Provenance edge deleted successfully";
    }
}