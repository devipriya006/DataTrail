package com.datatrail.backend.controller;

import com.datatrail.backend.entity.ProvenanceEdge;
import com.datatrail.backend.service.ProvenanceEdgeService;
import org.springframework.web.bind.annotation.*;

import com.datatrail.backend.dto.ProvenanceTraceResponse;
import com.datatrail.backend.dto.ProvenanceCandidateResponse;
import java.security.Principal;

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

    @PostMapping("/project/{projectId}")
public ProvenanceEdge createProvenanceEdge(
        @PathVariable Long projectId,
        @RequestBody Map<String, Object> request,
        Principal principal) {

    Long fromAuditId = Long.valueOf(
            request.get("fromAuditId").toString());

    Long toAuditId = Long.valueOf(
            request.get("toAuditId").toString());

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
            projectId,
            principal.getName(),
            fromAuditId,
            toAuditId,
            relationshipType,
            edgeWeight,
            inferenceMethod,
            snapshotId,
            createdAt);
}

   @GetMapping("/project/{projectId}")
public List<ProvenanceEdge> getAllProvenanceEdges(
        @PathVariable Long projectId,
        Principal principal) {

    return provenanceEdgeService.getAllProvenanceEdges(
            projectId, principal.getName());
}

@GetMapping("/project/{projectId}/{id}")
public ProvenanceEdge getProvenanceEdgeById(
        @PathVariable Long projectId,
        @PathVariable Long id,
        Principal principal) {

    return provenanceEdgeService.getProvenanceEdgeById(
            projectId, id, principal.getName());
}

@DeleteMapping("/project/{projectId}/{id}")
public String deleteProvenanceEdge(
        @PathVariable Long projectId,
        @PathVariable Long id,
        Principal principal) {

    provenanceEdgeService.deleteProvenanceEdge(
            projectId, id, principal.getName());

    return "Provenance edge deleted successfully";
}

    @GetMapping("/project/{projectId}/audit/{auditId}/trace")
    public List<ProvenanceTraceResponse> traceAuditEvent(
            @PathVariable Long projectId,
            @PathVariable Long auditId,
            Principal principal) {

        return provenanceEdgeService.traceAuditEvent(
                projectId,
                auditId,
                principal.getName()
        );
    }

    @GetMapping("/project/{projectId}/audit/{auditId}/candidates")
    public List<ProvenanceCandidateResponse> discoverCandidates(
            @PathVariable Long projectId,
            @PathVariable Long auditId,
            Principal principal) {

        return provenanceEdgeService.discoverCandidates(
                projectId,
                auditId,
                principal.getName());
    }

}
