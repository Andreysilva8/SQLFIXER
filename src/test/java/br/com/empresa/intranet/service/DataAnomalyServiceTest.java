package br.com.empresa.intranet.service;

import br.com.empresa.intranet.dto.request.DataAnomalyRequest;
import br.com.empresa.intranet.dto.response.AnalysisReport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataAnomalyServiceTest {
    private final DataAnomalyService service = new DataAnomalyService();

    @Test
    void detectsMissingAndNegativeValues() {
        List<Map<String, Object>> rows = List.of(
                row("id", 1, "saldo", 25, "email", "ana@example.com"),
                row("id", 2, "saldo", -8, "email", null)
        );

        AnalysisReport report = service.analyze(new DataAnomalyRequest(rows, "clientes", null, null, null, null));

        assertEquals(1L, report.metrics().get("anomalyRows"));
        assertEquals(1L, report.metrics().get("negativeRows"));
        assertEquals(1L, report.metrics().get("missingRows"));
        assertTrue(report.issues().stream().anyMatch(issue -> issue.title().contains("multiplas anomalias")));
    }

    @Test
    void detectsZScoreOutliers() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            rows.add(row("latencyMs", 0));
        }
        rows.add(row("latencyMs", 100));

        AnalysisReport report = service.analyze(new DataAnomalyRequest(rows, "metricas", null, null, null, null));

        assertEquals(1L, report.metrics().get("outlierRows"));
        assertTrue(report.issues().stream().anyMatch(issue -> "Outlier por Z-score".equals(issue.title())));
    }

    private Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            row.put(String.valueOf(values[i]), values[i + 1]);
        }
        return row;
    }
}
