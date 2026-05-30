package com.example.demo.service;

import com.example.demo.analysis.AnalysisIssue;
import com.example.demo.analysis.AnalysisReport;
import com.example.demo.analysis.AnalysisSettings;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class DatabaseAnalyzerService {
    private final DatabaseConnectionService connectionService;
    private final SqlAnalyzerService sqlAnalyzerService;

    public DatabaseAnalyzerService(DatabaseConnectionService connectionService, SqlAnalyzerService sqlAnalyzerService) {
        this.connectionService = connectionService;
        this.sqlAnalyzerService = sqlAnalyzerService;
    }

    public AnalysisReport analyze(AnalysisSettings settings) throws Exception {
        try (Connection connection = connectionService.openConnection()) {
            String database = currentDatabase(connection);
            List<AnalysisIssue> issues = new ArrayList<>();
            Map<String, Object> metrics = new LinkedHashMap<>();

            if (settings.isCheckTables() || settings.isCheckIndexes()) {
                analyzeTables(connection, settings, issues, metrics);
            }
            if (settings.isCheckViews()) {
                analyzeViews(connection, issues, metrics);
            }
            if (settings.isCheckProcedures() || settings.isCheckFunctions()) {
                analyzeRoutines(connection, settings, issues, metrics);
            }
            if (settings.isCheckSelects() || settings.isCheckJoins()) {
                analyzeStatementDigests(connection, settings, issues, metrics);
            }

            metrics.put("riskScore", riskScore(issues));
            return sqlAnalyzerService.buildReport("database", database, issues, null, metrics);
        }
    }

    private void analyzeTables(Connection connection, AnalysisSettings settings, List<AnalysisIssue> issues, Map<String, Object> metrics) {
        List<Map<String, Object>> tables = query(connection, """
                SELECT table_name, engine, table_rows, data_length, index_length, table_collation
                FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'
                ORDER BY table_name
                """);
        Set<String> primaryKeyTables = querySet(connection, """
                SELECT table_name
                FROM information_schema.table_constraints
                WHERE table_schema = DATABASE() AND constraint_type = 'PRIMARY KEY'
                """, "table_name");
        Map<String, Long> indexCount = queryLongMap(connection, """
                SELECT table_name, COUNT(DISTINCT index_name) AS indexes_count
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                GROUP BY table_name
                """, "table_name", "indexes_count");
        Map<String, List<Map<String, Object>>> columnsByTable = columnsByTable(connection);

        metrics.put("tables", tables.size());
        int tablesWithoutPk = 0;
        int tablesWithWeakIndexes = 0;

        for (Map<String, Object> table : tables) {
            String tableName = value(table, "table_name");
            String engine = value(table, "engine");
            String collation = value(table, "table_collation");
            long rows = number(table.get("table_rows"));
            long dataLength = number(table.get("data_length"));
            long indexes = indexCount.getOrDefault(tableName, 0L);

            if (settings.isCheckTables() && !primaryKeyTables.contains(tableName)) {
                tablesWithoutPk++;
                issues.add(issue("HIGH", "schema", "table", tableName, "Tabela sem chave primaria",
                        "A tabela `" + tableName + "` nao possui PRIMARY KEY, o que prejudica integridade, joins e manutencao.",
                        "Defina uma chave primaria estavel antes de usar a tabela em producao.",
                        primaryKeyFix(tableName, columnsByTable.get(tableName)), 0.94));
            }

            if (settings.isCheckTables() && engine != null && !"InnoDB".equalsIgnoreCase(engine)) {
                issues.add(issue("MEDIUM", "schema", "table", tableName, "Engine nao transacional",
                        "A tabela `" + tableName + "` usa " + engine + ", sem o mesmo nivel de transacao e integridade do InnoDB.",
                        "Migre a tabela para InnoDB apos backup e teste.",
                        "ALTER TABLE `" + tableName + "` ENGINE=InnoDB;", 0.86));
            }

            if (settings.isCheckIndexes() && rows >= settings.getLargeTableRows() && indexes <= 1) {
                tablesWithWeakIndexes++;
                issues.add(issue("MEDIUM", "performance", "table", tableName, "Tabela grande com poucos indices",
                        "A tabela `" + tableName + "` tem cerca de " + rows + " linhas e " + indexes + " indice(s).",
                        "Crie indices para colunas usadas em WHERE, JOIN e ORDER BY.",
                        indexFix(tableName, columnsByTable.get(tableName)), 0.8));
            }

            if (settings.isCheckIndexes() && dataLength > 128L * 1024L * 1024L && indexes == 0) {
                issues.add(issue("HIGH", "performance", "table", tableName, "Tabela grande sem indices",
                        "A tabela `" + tableName + "` ocupa mais de 128MB e nao tem indices registrados.",
                        "Adicione chave primaria e indices de consulta antes de aumentar o volume.",
                        indexFix(tableName, columnsByTable.get(tableName)), 0.88));
            }

            if (settings.isCheckSecurity()) {
                inspectSensitiveColumns(tableName, columnsByTable.get(tableName), issues);
            }

            if (collation != null && collation.toLowerCase(Locale.ROOT).contains("latin1")) {
                issues.add(issue("LOW", "schema", "table", tableName, "Collation legada",
                        "A tabela `" + tableName + "` usa " + collation + ".",
                        "Considere utf8mb4 para suportar acentos, emojis e compatibilidade moderna.",
                        "ALTER TABLE `" + tableName + "` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;", 0.7));
            }
        }

        metrics.put("tablesWithoutPk", tablesWithoutPk);
        metrics.put("tablesWithWeakIndexes", tablesWithWeakIndexes);
    }

    private void analyzeViews(Connection connection, List<AnalysisIssue> issues, Map<String, Object> metrics) {
        List<Map<String, Object>> views = query(connection, """
                SELECT table_name, view_definition
                FROM information_schema.views
                WHERE table_schema = DATABASE()
                ORDER BY table_name
                """);
        metrics.put("views", views.size());
        for (Map<String, Object> view : views) {
            String viewName = value(view, "table_name");
            String definition = value(view, "view_definition");
            if (definition != null && definition.matches("(?is).*\\bSELECT\\s+\\*\\s+FROM\\b.*")) {
                issues.add(issue("MEDIUM", "view", "view", viewName, "View com SELECT *",
                        "A view `" + viewName + "` depende de todas as colunas da tabela base.",
                        "Liste as colunas explicitamente para evitar quebra ao alterar a tabela.",
                        null, 0.84));
            }
            if (definition != null && definition.matches("(?is).*\\bWHERE\\s+1\\s*=\\s*1\\b.*")) {
                issues.add(issue("LOW", "view", "view", viewName, "Filtro neutro em view",
                        "A view `" + viewName + "` contem WHERE 1=1, possivel sobra de SQL dinamico.",
                        "Remova filtros neutros e documente filtros obrigatorios.",
                        null, 0.66));
            }
        }
    }

    private void analyzeRoutines(Connection connection, AnalysisSettings settings, List<AnalysisIssue> issues, Map<String, Object> metrics) {
        List<Map<String, Object>> routines = query(connection, """
                SELECT routine_name, routine_type, routine_definition, security_type
                FROM information_schema.routines
                WHERE routine_schema = DATABASE()
                ORDER BY routine_type, routine_name
                """);
        metrics.put("routines", routines.size());
        for (Map<String, Object> routine : routines) {
            String routineName = value(routine, "routine_name");
            String routineType = value(routine, "routine_type");
            String definition = value(routine, "routine_definition");
            String securityType = value(routine, "security_type");
            boolean isProcedure = "PROCEDURE".equalsIgnoreCase(routineType);
            boolean enabled = isProcedure ? settings.isCheckProcedures() : settings.isCheckFunctions();
            if (!enabled || definition == null) {
                continue;
            }
            String upper = definition.toUpperCase(Locale.ROOT);
            if (upper.contains("DROP ") || upper.contains("TRUNCATE ")) {
                issues.add(issue("HIGH", "routine", routineType.toLowerCase(Locale.ROOT), routineName, "Rotina com comando destrutivo",
                        routineType + " `" + routineName + "` contem DROP ou TRUNCATE.",
                        "Exija trilha de auditoria, validacao de parametros e aprovacao manual.",
                        null, 0.9));
            }
            if (upper.contains("PREPARE ") || upper.contains("EXECUTE ")) {
                issues.add(issue("HIGH", "routine", routineType.toLowerCase(Locale.ROOT), routineName, "Rotina usa SQL dinamico",
                        routineType + " `" + routineName + "` monta SQL em runtime.",
                        "Use listas permitidas para nomes dinamicos e parametros preparados.",
                        null, 0.86));
            }
            if ("DEFINER".equalsIgnoreCase(securityType)) {
                issues.add(issue("LOW", "routine", routineType.toLowerCase(Locale.ROOT), routineName, "Rotina com SQL SECURITY DEFINER",
                        routineType + " `" + routineName + "` executa com privilegios do definidor.",
                        "Revise se a rotina deveria usar INVOKER e permissao minima.",
                        null, 0.68));
            }
        }
    }

    private void analyzeStatementDigests(Connection connection, AnalysisSettings settings, List<AnalysisIssue> issues, Map<String, Object> metrics) {
        List<Map<String, Object>> digests = query(connection, """
                SELECT digest_text, sum_errors, sum_warnings, count_star, avg_timer_wait
                FROM performance_schema.events_statements_summary_by_digest
                WHERE schema_name = DATABASE()
                ORDER BY sum_errors DESC, sum_warnings DESC, count_star DESC
                LIMIT 20
                """);
        metrics.put("statementDigests", digests.size());
        for (Map<String, Object> digest : digests) {
            String sql = value(digest, "digest_text");
            if (sql == null || sql.isBlank()) {
                continue;
            }
            long errors = number(digest.get("sum_errors"));
            long warnings = number(digest.get("sum_warnings"));
            if (errors > 0) {
                issues.add(issue("HIGH", "runtime", "query", "performance_schema", "Query com erros recorrentes",
                        "Uma consulta registrada no performance_schema acumula " + errors + " erro(s).",
                        "Analise a query abaixo e corrija sintaxe, tabelas ou permissoes.",
                        sql, 0.88));
            }
            if (warnings > 0) {
                issues.add(issue("MEDIUM", "runtime", "query", "performance_schema", "Query com warnings recorrentes",
                        "Uma consulta registrada no performance_schema acumula " + warnings + " warning(s).",
                        "Revise conversoes implicitas, truncamentos e uso de indices.",
                        sql, 0.76));
            }
            AnalysisReport digestReport = sqlAnalyzerService.analyze(sql, settings);
            for (AnalysisIssue issue : digestReport.issues()) {
                issues.add(new AnalysisIssue(
                        UUID.randomUUID().toString(),
                        issue.severity(),
                        issue.category(),
                        "query_digest",
                        "performance_schema",
                        issue.title(),
                        issue.detail(),
                        issue.recommendation(),
                        issue.fixSql(),
                        issue.confidence()
                ));
            }
        }
    }

    private void inspectSensitiveColumns(String tableName, List<Map<String, Object>> columns, List<AnalysisIssue> issues) {
        if (columns == null) {
            return;
        }
        for (Map<String, Object> column : columns) {
            String columnName = value(column, "column_name");
            String dataType = value(column, "data_type");
            String nullable = value(column, "is_nullable");
            if (columnName == null) {
                continue;
            }
            String normalized = columnName.toLowerCase(Locale.ROOT);
            if (normalized.matches(".*(senha|password|token|secret|apikey|api_key).*") && dataType != null && dataType.startsWith("varchar")) {
                issues.add(issue("HIGH", "security", "column", tableName + "." + columnName, "Coluna sensivel em texto",
                        "A coluna `" + tableName + "`.`" + columnName + "` parece armazenar credencial como texto comum.",
                        "Armazene hashes fortes para senhas e nunca grave tokens sem criptografia.",
                        null, 0.82));
            }
            if (normalized.matches(".*(email|cpf|cnpj|documento).*") && "YES".equalsIgnoreCase(nullable)) {
                issues.add(issue("LOW", "data-quality", "column", tableName + "." + columnName, "Campo chave permite NULL",
                        "A coluna `" + tableName + "`.`" + columnName + "` aceita NULL.",
                        "Avalie NOT NULL e UNIQUE conforme a regra de negocio.",
                        null, 0.64));
            }
        }
    }

    private Map<String, List<Map<String, Object>>> columnsByTable(Connection connection) {
        List<Map<String, Object>> columns = query(connection, """
                SELECT table_name, column_name, data_type, column_type, is_nullable, column_key, extra
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                ORDER BY table_name, ordinal_position
                """);
        Map<String, List<Map<String, Object>>> grouped = new HashMap<>();
        for (Map<String, Object> column : columns) {
            grouped.computeIfAbsent(value(column, "table_name"), key -> new ArrayList<>()).add(column);
        }
        return grouped;
    }

    private String primaryKeyFix(String tableName, List<Map<String, Object>> columns) {
        if (columns != null) {
            for (Map<String, Object> column : columns) {
                String columnName = value(column, "column_name");
                String dataType = value(column, "data_type");
                if ("id".equalsIgnoreCase(columnName) && isIntegerType(dataType)) {
                    return "ALTER TABLE `" + tableName + "` ADD PRIMARY KEY (`" + columnName + "`);";
                }
            }
        }
        return "ALTER TABLE `" + tableName + "` ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;";
    }

    private String indexFix(String tableName, List<Map<String, Object>> columns) {
        String preferred = null;
        if (columns != null) {
            for (Map<String, Object> column : columns) {
                String columnName = value(column, "column_name");
                if (columnName != null && columnName.matches("(?i).*(id|email|created_at|data|status|tipo).*")) {
                    preferred = columnName;
                    break;
                }
            }
        }
        if (preferred == null) {
            return "-- Escolha uma coluna usada em WHERE/JOIN antes de criar o indice.\nCREATE INDEX idx_" + tableName + "_coluna ON `" + tableName + "` (`coluna`);";
        }
        return "CREATE INDEX idx_" + tableName + "_" + preferred + " ON `" + tableName + "` (`" + preferred + "`);";
    }

    private boolean isIntegerType(String dataType) {
        if (dataType == null) {
            return false;
        }
        return dataType.matches("(?i)(tinyint|smallint|mediumint|int|bigint)");
    }

    private String currentDatabase(Connection connection) {
        List<Map<String, Object>> rows = query(connection, "SELECT DATABASE() AS database_name");
        if (rows.isEmpty()) {
            return connectionService.activeDatabaseName();
        }
        return value(rows.get(0), "database_name");
    }

    private List<Map<String, Object>> query(Connection connection, String sql) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            ResultSetMetaData metaData = resultSet.getMetaData();
            while (resultSet.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    row.put(metaData.getColumnLabel(i).toLowerCase(Locale.ROOT), resultSet.getObject(i));
                }
                rows.add(row);
            }
        } catch (Exception ignored) {
            return List.of();
        }
        return rows;
    }

    private Set<String> querySet(Connection connection, String sql, String column) {
        Set<String> values = new HashSet<>();
        for (Map<String, Object> row : query(connection, sql)) {
            values.add(value(row, column));
        }
        return values;
    }

    private Map<String, Long> queryLongMap(Connection connection, String sql, String keyColumn, String valueColumn) {
        Map<String, Long> values = new HashMap<>();
        for (Map<String, Object> row : query(connection, sql)) {
            values.put(value(row, keyColumn), number(row.get(valueColumn.toLowerCase(Locale.ROOT))));
        }
        return values;
    }

    private String value(Map<String, Object> row, String key) {
        if (row == null || key == null || row.get(key.toLowerCase(Locale.ROOT)) == null) {
            return null;
        }
        return String.valueOf(row.get(key.toLowerCase(Locale.ROOT)));
    }

    private long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private AnalysisIssue issue(String severity, String category, String objectType, String objectName,
                                String title, String detail, String recommendation, String fixSql, double confidence) {
        return new AnalysisIssue(UUID.randomUUID().toString(), severity, category, objectType, objectName,
                title, detail, recommendation, fixSql, confidence);
    }

    private int riskScore(List<AnalysisIssue> issues) {
        int score = 0;
        for (AnalysisIssue issue : issues) {
            score += switch (issue.severity()) {
                case "CRITICAL" -> 35;
                case "HIGH" -> 22;
                case "MEDIUM" -> 12;
                default -> 5;
            };
        }
        return Math.min(score, 100);
    }
}
