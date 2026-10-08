package com.datatrail.backend.controller;

import com.datatrail.backend.entity.ChangeAnalysis;
import com.datatrail.backend.service.ChangeAnalysisService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/change-analysis")
public class ChangeAnalysisController {

    private final ChangeAnalysisService changeAnalysisService;

    public ChangeAnalysisController(
            ChangeAnalysisService changeAnalysisService) {

        this.changeAnalysisService = changeAnalysisService;
    }

    @PostMapping
    public ChangeAnalysis createChangeAnalysis(
            @RequestBody Map<String, Object> request) {

        Long contextId = Long.valueOf(
                request.get("contextId").toString()
        );

        String scoringModelVersion =
                (String) request.get("scoringModelVersion");

        Double dependencyScore = request.get("dependencyScore") != null
                ? Double.valueOf(request.get("dependencyScore").toString())
                : null;

        Double sensitivityScore = request.get("sensitivityScore") != null
                ? Double.valueOf(request.get("sensitivityScore").toString())
                : null;

        Double abnormalityScore = request.get("abnormalityScore") != null
                ? Double.valueOf(request.get("abnormalityScore").toString())
                : null;

        Double recoveryComplexity = request.get("recoveryComplexity") != null
                ? Double.valueOf(request.get("recoveryComplexity").toString())
                : null;

        Double importanceScore = request.get("importanceScore") != null
                ? Double.valueOf(request.get("importanceScore").toString())
                : null;

        String featureVector =
                (String) request.get("featureVector");

        String explanationSummary =
                (String) request.get("explanationSummary");

        LocalDateTime analyzedAt = request.get("analyzedAt") != null
                ? LocalDateTime.parse(request.get("analyzedAt").toString())
                : null;

        return changeAnalysisService.createChangeAnalysis(
                contextId,
                scoringModelVersion,
                dependencyScore,
                sensitivityScore,
                abnormalityScore,
                recoveryComplexity,
                importanceScore,
                featureVector,
                explanationSummary,
                analyzedAt
        );
    }

    @GetMapping
    public List<ChangeAnalysis> getAllChangeAnalyses() {
        return changeAnalysisService.getAllChangeAnalyses();
    }

    @GetMapping("/{id}")
    public ChangeAnalysis getChangeAnalysisById(
            @PathVariable Long id) {

        return changeAnalysisService.getChangeAnalysisById(id);
    }

    @DeleteMapping("/{id}")
    public String deleteChangeAnalysis(
            @PathVariable Long id) {

        changeAnalysisService.deleteChangeAnalysis(id);

        return "Change analysis deleted successfully";
    }
}