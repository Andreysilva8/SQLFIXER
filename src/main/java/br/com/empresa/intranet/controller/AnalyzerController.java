package br.com.empresa.intranet.controller;

import br.com.empresa.intranet.dto.request.AnalysisSettings;
import br.com.empresa.intranet.dto.request.DataAnomalyRequest;
import br.com.empresa.intranet.dto.request.DatabaseAnalysisRequest;
import br.com.empresa.intranet.dto.request.DatabaseMonitorRequest;
import br.com.empresa.intranet.dto.request.SqlAnalysisRequest;
import br.com.empresa.intranet.dto.response.AnalysisReport;
import br.com.empresa.intranet.service.AlertService;
import br.com.empresa.intranet.service.AnalysisSettingsService;
import br.com.empresa.intranet.service.DataAnomalyService;
import br.com.empresa.intranet.service.DatabaseAnalyzerService;
import br.com.empresa.intranet.service.DatabaseMonitorService;
import br.com.empresa.intranet.service.SqlAnalyzerService;
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
    private final DatabaseMonitorService databaseMonitorService;
    private final DataAnomalyService dataAnomalyService;
    private final AnalysisSettingsService settingsService;
    private final AlertService alertService;

    public AnalyzerController(SqlAnalyzerService sqlAnalyzerService,
                              DatabaseAnalyzerService databaseAnalyzerService,
                              DatabaseMonitorService databaseMonitorService,
                              DataAnomalyService dataAnomalyService,
                              AnalysisSettingsService settingsService,
                              AlertService alertService) {
        this.sqlAnalyzerService = sqlAnalyzerService;
        this.databaseAnalyzerService = databaseAnalyzerService;
        this.databaseMonitorService = databaseMonitorService;
        this.dataAnomalyService = dataAnomalyService;
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
            AnalysisReport report = databaseAnalyzerService.analyze(resolved, request);
            alertService.publishReport(report);
            return ResponseEntity.ok(report);
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", ex.getMessage(),
                    "totalIssues", 0
            ));
        }
    }

    @PostMapping("/analyzer/data")
    public AnalysisReport analyzeData(@RequestBody(required = false) DataAnomalyRequest request) {
        AnalysisReport report = dataAnomalyService.analyze(request);
        alertService.publishReport(report);
        return report;
    }

    @PostMapping("/monitor/database/start")
    public DatabaseMonitorService.DatabaseMonitorStatus startDatabaseMonitor(@RequestBody(required = false) DatabaseMonitorRequest request) {
        return databaseMonitorService.start(request);
    }

    @PostMapping("/monitor/database/stop")
    public DatabaseMonitorService.DatabaseMonitorStatus stopDatabaseMonitor() {
        return databaseMonitorService.stop();
    }

    @GetMapping("/monitor/database/status")
    public DatabaseMonitorService.DatabaseMonitorStatus databaseMonitorStatus() {
        return databaseMonitorService.status();
    }
}
