package br.com.empresa.intranet.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AnalysisReport(
        String id,
        String source,
        String database,
        Instant generatedAt,
        int totalIssues,
        Map<String, Long> issueCounts,
        List<AnalysisIssue> issues,
        String fixedSql,
        Map<String, Object> metrics
) {
}
