package br.com.empresa.intranet.dto.request;

public record SqlAnalysisRequest(String sql, AnalysisSettings settings) {
}
