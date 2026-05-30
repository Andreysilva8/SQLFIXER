package br.com.empresa.intranet.dto.request;

public record DatabaseAnalysisRequest(
        AnalysisSettings settings,
        String targetType,
        String targetName,
        Boolean monitor
) {
    public String resolvedTargetType() {
        if (targetType == null || targetType.trim().isEmpty()) {
            return "full";
        }
        return targetType.trim().toLowerCase();
    }

    public String resolvedTargetName() {
        return targetName == null || targetName.trim().isEmpty() ? null : targetName.trim();
    }

    public boolean isMonitor() {
        return Boolean.TRUE.equals(monitor);
    }
}
