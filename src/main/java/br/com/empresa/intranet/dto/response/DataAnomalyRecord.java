package br.com.empresa.intranet.dto.response;

import java.util.List;
import java.util.Map;

public record DataAnomalyRecord(
        int rowIndex,
        Map<String, Object> data,
        List<String> anomalyTypes,
        boolean isAnomaly
) {
}
