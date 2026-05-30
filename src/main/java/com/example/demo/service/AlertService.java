package com.example.demo.service;

import com.example.demo.analysis.AnalysisIssue;
import com.example.demo.analysis.AnalysisReport;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
public class AlertService {
    private static final int MAX_ALERTS = 80;
    private final Deque<AlertItem> alerts = new ConcurrentLinkedDeque<>();

    public void publish(String severity, String title, String message, String source, String database) {
        alerts.addFirst(new AlertItem(
                UUID.randomUUID().toString(),
                severity,
                title,
                message,
                source,
                database,
                Instant.now(),
                false
        ));
        while (alerts.size() > MAX_ALERTS) {
            alerts.pollLast();
        }
    }

    public void publishReport(AnalysisReport report) {
        if (report == null || report.issues() == null) {
            return;
        }
        for (AnalysisIssue issue : report.issues()) {
            if (isNotifiable(issue.severity())) {
                publish(
                        issue.severity(),
                        issue.title(),
                        issue.detail(),
                        report.source(),
                        report.database()
                );
            }
        }
    }

    public List<AlertItem> latest() {
        return new ArrayList<>(alerts);
    }

    public void clear() {
        alerts.clear();
    }

    private boolean isNotifiable(String severity) {
        return "CRITICAL".equalsIgnoreCase(severity) || "HIGH".equalsIgnoreCase(severity);
    }

    public record AlertItem(
            String id,
            String severity,
            String title,
            String message,
            String source,
            String database,
            Instant detectedAt,
            boolean acknowledged
    ) {
    }
}
