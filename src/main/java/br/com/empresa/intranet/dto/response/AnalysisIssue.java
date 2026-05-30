package br.com.empresa.intranet.dto.response;

public record AnalysisIssue(
        String id,
        String severity,
        String category,
        String objectType,
        String objectName,
        String title,
        String detail,
        String recommendation,
        String fixSql,
        double confidence
) {
}
