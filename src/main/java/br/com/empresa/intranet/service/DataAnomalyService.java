package br.com.empresa.intranet.service;

import br.com.empresa.intranet.dto.request.DataAnomalyRequest;
import br.com.empresa.intranet.dto.response.AnalysisIssue;
import br.com.empresa.intranet.dto.response.AnalysisReport;
import br.com.empresa.intranet.dto.response.DataAnomalyRecord;
import br.com.empresa.intranet.util.RiskScoreUtil;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DataAnomalyService {
    private static final String MISSING_VALUES = "Missing Values";
    private static final String INVALID_NEGATIVE_VALUES = "Invalid Negative Values";
    private static final String Z_SCORE_OUTLIERS = "Z-Score Outliers";
    private static final List<String> TYPE_ORDER = List.of(MISSING_VALUES, INVALID_NEGATIVE_VALUES, Z_SCORE_OUTLIERS);

    public AnalysisReport analyze(DataAnomalyRequest request) {
        List<Map<String, Object>> rows = normalizeRows(request == null ? null : request.rows());
        Set<String> columns = collectColumns(rows);
        Map<Integer, LinkedHashSet<String>> anomalyTypes = new LinkedHashMap<>();

        double threshold = request == null ? 3.0 : request.resolvedZScoreThreshold();
        boolean detectMissing = request == null || request.shouldDetectMissingValues();
        boolean detectNegative = request == null || request.shouldDetectNegativeValues();
        boolean detectOutliers = request == null || request.shouldDetectOutliers();

        if (detectMissing) {
            detectMissingValues(rows, columns, anomalyTypes);
        }

        Map<String, List<Double>> numericColumns = detectNumericColumns(rows, columns);

        if (detectNegative) {
            detectInvalidNegativeValues(rows, numericColumns.keySet(), anomalyTypes);
        }

        if (detectOutliers) {
            detectZScoreOutliers(numericColumns, threshold, anomalyTypes);
        }

        List<DataAnomalyRecord> anomalyRecords = toRecords(rows, anomalyTypes);
        List<AnalysisIssue> issues = toIssues(anomalyRecords);
        if (rows.isEmpty()) {
            issues.add(issue("LOW", "data-quality", "dataset", sourceName(request), "Dataset vazio",
                    "Nenhuma linha foi enviada para analise de anomalias.",
                    "Envie um array JSON com registros do banco, API ou consulta exportada.", null, 1.0));
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("rows", rows.size());
        metrics.put("columns", columns.size());
        metrics.put("numericColumns", numericColumns.size());
        metrics.put("anomalyRows", (long) anomalyRecords.size());
        metrics.put("missingRows", countRows(anomalyRecords, MISSING_VALUES));
        metrics.put("negativeRows", countRows(anomalyRecords, INVALID_NEGATIVE_VALUES));
        metrics.put("outlierRows", countRows(anomalyRecords, Z_SCORE_OUTLIERS));
        metrics.put("zScoreThreshold", threshold);
        metrics.put("anomalyRecords", anomalyRecords);
        metrics.put("riskScore", RiskScoreUtil.calculate(issues));

        Map<String, Long> counts = issues.stream()
                .collect(Collectors.groupingBy(AnalysisIssue::severity, LinkedHashMap::new, Collectors.counting()));

        return new AnalysisReport(
                UUID.randomUUID().toString(),
                "data",
                sourceName(request),
                Instant.now(),
                issues.size(),
                counts,
                List.copyOf(issues),
                null,
                metrics
        );
    }

    private List<Map<String, Object>> normalizeRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            normalized.add(row == null ? Map.of() : new LinkedHashMap<>(row));
        }
        return normalized;
    }

    private Set<String> collectColumns(List<Map<String, Object>> rows) {
        Set<String> columns = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            for (String column : row.keySet()) {
                if (hasText(column)) {
                    columns.add(column);
                }
            }
        }
        return columns;
    }

    private void detectMissingValues(List<Map<String, Object>> rows, Set<String> columns,
                                     Map<Integer, LinkedHashSet<String>> anomalyTypes) {
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            Map<String, Object> row = rows.get(rowIndex);
            for (String column : columns) {
                if (!row.containsKey(column) || isMissing(row.get(column))) {
                    addType(anomalyTypes, rowIndex, MISSING_VALUES);
                }
            }
        }
    }

    private Map<String, List<Double>> detectNumericColumns(List<Map<String, Object>> rows, Set<String> columns) {
        Map<String, List<Double>> numericColumns = new LinkedHashMap<>();
        for (String column : columns) {
            List<Double> values = new ArrayList<>();
            boolean hasNumber = false;
            boolean numeric = true;
            for (Map<String, Object> row : rows) {
                Object raw = row.get(column);
                if (isMissing(raw)) {
                    values.add(0.0);
                    continue;
                }
                Double value = numberValue(raw);
                if (value == null) {
                    numeric = false;
                    break;
                }
                hasNumber = true;
                values.add(value);
            }
            if (numeric && hasNumber) {
                numericColumns.put(column, values);
            }
        }
        return numericColumns;
    }

    private void detectInvalidNegativeValues(List<Map<String, Object>> rows, Set<String> numericColumns,
                                             Map<Integer, LinkedHashSet<String>> anomalyTypes) {
        for (String column : numericColumns) {
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Double value = numberValue(rows.get(rowIndex).get(column));
                if (value != null && value < 0) {
                    addType(anomalyTypes, rowIndex, INVALID_NEGATIVE_VALUES);
                }
            }
        }
    }

    private void detectZScoreOutliers(Map<String, List<Double>> numericColumns, double threshold,
                                      Map<Integer, LinkedHashSet<String>> anomalyTypes) {
        for (List<Double> values : numericColumns.values()) {
            if (values.size() < 2) {
                continue;
            }
            double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double variance = values.stream()
                    .mapToDouble(value -> Math.pow(value - mean, 2))
                    .average()
                    .orElse(0.0);
            double standardDeviation = Math.sqrt(variance);
            if (standardDeviation == 0.0) {
                continue;
            }
            for (int rowIndex = 0; rowIndex < values.size(); rowIndex++) {
                double zScore = Math.abs((values.get(rowIndex) - mean) / standardDeviation);
                if (zScore > threshold) {
                    addType(anomalyTypes, rowIndex, Z_SCORE_OUTLIERS);
                }
            }
        }
    }

    private List<DataAnomalyRecord> toRecords(List<Map<String, Object>> rows, Map<Integer, LinkedHashSet<String>> anomalyTypes) {
        List<DataAnomalyRecord> records = new ArrayList<>();
        for (Map.Entry<Integer, LinkedHashSet<String>> entry : anomalyTypes.entrySet()) {
            int rowIndex = entry.getKey();
            List<String> orderedTypes = TYPE_ORDER.stream()
                    .filter(entry.getValue()::contains)
                    .toList();
            records.add(new DataAnomalyRecord(rowIndex, rows.get(rowIndex), orderedTypes, true));
        }
        return records;
    }

    private List<AnalysisIssue> toIssues(List<DataAnomalyRecord> records) {
        List<AnalysisIssue> issues = new ArrayList<>();
        for (DataAnomalyRecord record : records) {
            List<String> types = record.anomalyTypes();
            String title = issueTitle(types);
            String severity = types.contains(INVALID_NEGATIVE_VALUES) ? "HIGH" : "MEDIUM";
            issues.add(issue(severity, "data-quality", "row", "linha " + (record.rowIndex() + 1), title,
                    "A linha " + (record.rowIndex() + 1) + " contem: " + String.join(", ", types) + ".",
                    recommendation(types), null, 0.9));
        }
        return issues;
    }

    private String issueTitle(List<String> types) {
        if (types.size() > 1) {
            return "Linha com multiplas anomalias";
        }
        if (types.contains(INVALID_NEGATIVE_VALUES)) {
            return "Valor negativo invalido";
        }
        if (types.contains(Z_SCORE_OUTLIERS)) {
            return "Outlier por Z-score";
        }
        return "Valores ausentes detectados";
    }

    private String recommendation(List<String> types) {
        List<String> recommendations = new ArrayList<>();
        if (types.contains(MISSING_VALUES)) {
            recommendations.add("preencha ou trate campos obrigatorios antes de gravar no banco");
        }
        if (types.contains(INVALID_NEGATIVE_VALUES)) {
            recommendations.add("valide regras de dominio para colunas que nao aceitam valores negativos");
        }
        if (types.contains(Z_SCORE_OUTLIERS)) {
            recommendations.add("confira se o valor fora do padrao e erro de carga, fraude ou evento real");
        }
        return "Recomendado: " + String.join("; ", recommendations) + ".";
    }

    private long countRows(List<DataAnomalyRecord> records, String type) {
        return records.stream()
                .filter(record -> record.anomalyTypes().contains(type))
                .count();
    }

    private void addType(Map<Integer, LinkedHashSet<String>> anomalyTypes, int rowIndex, String type) {
        anomalyTypes.computeIfAbsent(rowIndex, ignored -> new LinkedHashSet<>()).add(type);
    }

    private Double numberValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && hasText(text)) {
            try {
                return Double.parseDouble(text.trim().replace(",", "."));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean isMissing(Object value) {
        return value == null || (value instanceof String text && text.trim().isEmpty());
    }

    private String sourceName(DataAnomalyRequest request) {
        return request != null && hasText(request.sourceName()) ? request.sourceName().trim() : "dataset JSON";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private AnalysisIssue issue(String severity, String category, String objectType, String objectName,
                                String title, String detail, String recommendation, String fixSql, double confidence) {
        return new AnalysisIssue(UUID.randomUUID().toString(), severity, category, objectType, objectName,
                title, detail, recommendation, fixSql, confidence);
    }
}
