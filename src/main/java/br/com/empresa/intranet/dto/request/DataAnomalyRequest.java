package br.com.empresa.intranet.dto.request;

import java.util.List;
import java.util.Map;

public record DataAnomalyRequest(
        List<Map<String, Object>> rows,
        String sourceName,
        Double zScoreThreshold,
        Boolean detectMissingValues,
        Boolean detectNegativeValues,
        Boolean detectOutliers
) {
    public double resolvedZScoreThreshold() {
        return zScoreThreshold == null || zScoreThreshold <= 0 ? 3.0 : zScoreThreshold;
    }

    public boolean shouldDetectMissingValues() {
        return !Boolean.FALSE.equals(detectMissingValues);
    }

    public boolean shouldDetectNegativeValues() {
        return !Boolean.FALSE.equals(detectNegativeValues);
    }

    public boolean shouldDetectOutliers() {
        return !Boolean.FALSE.equals(detectOutliers);
    }
}
