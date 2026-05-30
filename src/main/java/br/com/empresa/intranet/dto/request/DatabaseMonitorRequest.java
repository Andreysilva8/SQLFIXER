package br.com.empresa.intranet.dto.request;

public record DatabaseMonitorRequest(
        AnalysisSettings settings,
        String targetType,
        String targetName,
        Integer intervalSeconds
) {
    public int resolvedIntervalSeconds() {
        if (intervalSeconds == null) {
            return 30;
        }
        return Math.max(intervalSeconds, 10);
    }

    public DatabaseAnalysisRequest toAnalysisRequest() {
        return new DatabaseAnalysisRequest(settings, targetType, targetName, true);
    }
}
