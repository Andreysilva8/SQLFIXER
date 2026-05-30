package com.example.demo.controller;

import com.example.demo.analysis.AnalysisReport;
import com.example.demo.analysis.AnalysisSettings;
import com.example.demo.service.AlertService;
import com.example.demo.service.AnalysisSettingsService;
import com.example.demo.service.DatabaseAnalyzerService;
import com.example.demo.service.SqlAnalyzerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class AnalyzerController {
    private final SqlAnalyzerService sqlAnalyzerService;
    private final DatabaseAnalyzerService databaseAnalyzerService;
    private final AnalysisSettingsService settingsService;
    private final AlertService alertService;

    public AnalyzerController(SqlAnalyzerService sqlAnalyzerService,
                              DatabaseAnalyzerService databaseAnalyzerService,
                              AnalysisSettingsService settingsService,
                              AlertService alertService) {
        this.sqlAnalyzerService = sqlAnalyzerService;
        this.databaseAnalyzerService = databaseAnalyzerService;
        this.settingsService = settingsService;
        this.alertService = alertService;
    }

    @GetMapping("/settings")
    public AnalysisSettings settings() {
        return settingsService.getSettings();
    }

    @PutMapping("/settings")
    public AnalysisSettings updateSettings(@RequestBody AnalysisSettings settings) {
        return settingsService.update(settings);
    }

    @PostMapping("/analyzer/sql")
    public AnalysisReport analyzeSql(@RequestBody SqlAnalysisRequest request) {
        AnalysisSettings resolved = settingsService.resolve(request.settings());
        AnalysisReport report = sqlAnalyzerService.analyze(request.sql(), resolved);
        alertService.publishReport(report);
        return report;
    }

    @PostMapping("/analyzer/database")
    public ResponseEntity<?> analyzeDatabase(@RequestBody(required = false) DatabaseAnalysisRequest request) {
        try {
            AnalysisSettings resolved = settingsService.resolve(request == null ? null : request.settings());
            AnalysisReport report = databaseAnalyzerService.analyze(resolved);
            alertService.publishReport(report);
            return ResponseEntity.ok(report);
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", ex.getMessage(),
                    "totalIssues", 0
            ));
        }
    }

    public record SqlAnalysisRequest(String sql, AnalysisSettings settings) {
    }

    public record DatabaseAnalysisRequest(AnalysisSettings settings) {
    }
}
