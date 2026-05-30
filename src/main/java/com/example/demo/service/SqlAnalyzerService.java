package com.example.demo.service;

import com.example.demo.analysis.AnalysisIssue;
import com.example.demo.analysis.AnalysisReport;
import com.example.demo.analysis.AnalysisSettings;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SqlAnalyzerService {
    private static final Pattern SELECT_STAR = Pattern.compile("(?is)\\bSELECT\\s+\\*\\s+FROM\\b");
    private static final Pattern DELETE_WITHOUT_WHERE = Pattern.compile("(?is)^\\s*DELETE\\s+FROM\\s+[`\\w.]+\\s*;?\\s*$");
    private static final Pattern UPDATE_WITHOUT_WHERE = Pattern.compile("(?is)^\\s*UPDATE\\s+[`\\w.]+\\s+SET\\s+.+(?<!\\bWHERE\\b)\\s*;?\\s*$");
    private static final Pattern DANGEROUS_DDL = Pattern.compile("(?is)\\b(DROP\\s+(DATABASE|TABLE)|TRUNCATE\\s+TABLE)\\b");
    private static final Pattern SQL_INJECTION = Pattern.compile("(?is)(\\bOR\\s+1\\s*=\\s*1\\b|\\bUNION\\s+SELECT\\b|--\\s*$|/\\*|\\bSLEEP\\s*\\(|\\bBENCHMARK\\s*\\()");
    private static final Pattern JOIN_WITHOUT_CONDITION = Pattern.compile("(?is)\\bJOIN\\s+[`\\w.]+\\s*(;|$|\\bWHERE\\b|\\bGROUP\\b|\\bORDER\\b|\\bLIMIT\\b)");
    private static final Pattern COMMA_JOIN = Pattern.compile("(?is)\\bFROM\\s+[`\\w.]+\\s*,\\s*[`\\w.]+");

    public AnalysisReport analyze(String sql, AnalysisSettings settings) {
        String safeSql = sql == null ? "" : sql;
        List<AnalysisIssue> issues = new ArrayList<>();
        List<String> statements = splitStatements(safeSql);

        if (safeSql.trim().isEmpty()) {
            issues.add(issue("LOW", "syntax", "script", "SQL", "Script vazio",
                    "Nenhum comando SQL foi informado para analise.",
                    "Cole um SELECT, JOIN, VIEW, PROCEDURE, FUNCTION ou script completo.",
                    null, 1.0));
        }

        if (settings.isCheckSyntax()) {
            inspectSyntax(safeSql, issues);
        }

        for (String statement : statements) {
            inspectStatement(statement, settings, issues);
        }

        String fixedSql = settings.isAutoFixSql() ? autoFix(safeSql) : null;
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("statements", statements.size());
        metrics.put("characters", safeSql.length());
        metrics.put("riskScore", riskScore(issues));

        return buildReport("sql", "SQL manual", issues, fixedSql, metrics);
    }

    AnalysisReport buildReport(String source, String database, List<AnalysisIssue> issues, String fixedSql, Map<String, Object> metrics) {
        Map<String, Long> counts = issues.stream()
                .collect(Collectors.groupingBy(AnalysisIssue::severity, LinkedHashMap::new, Collectors.counting()));
        return new AnalysisReport(
                UUID.randomUUID().toString(),
                source,
                database,
                Instant.now(),
                issues.size(),
                counts,
                List.copyOf(issues),
                fixedSql,
                metrics
        );
    }

    private void inspectSyntax(String sql, List<AnalysisIssue> issues) {
        if (count(sql, '\'') % 2 != 0) {
            issues.add(issue("HIGH", "syntax", "script", "SQL", "Aspas simples sem fechamento",
                    "O script tem uma quantidade impar de aspas simples, indicando string aberta.",
                    "Feche a string ou escape aspas internas com duas aspas simples.",
                    null, 0.96));
        }
        if (balance(sql, '(', ')') != 0) {
            issues.add(issue("HIGH", "syntax", "script", "SQL", "Parenteses desbalanceados",
                    "A quantidade de parenteses de abertura e fechamento nao confere.",
                    "Revise funcoes, subqueries, CHECK constraints e listas de colunas.",
                    null, 0.92));
        }
        String trimmed = sql.trim();
        if (!trimmed.isEmpty() && !trimmed.endsWith(";")) {
            issues.add(issue("LOW", "syntax", "script", "SQL", "Ponto e virgula ausente",
                    "O ultimo comando nao termina com ';'.",
                    "Finalize o comando para evitar erro em execucoes em lote.",
                    trimmed + ";", 0.9));
        }
        if (containsTypo(trimmed)) {
            issues.add(issue("MEDIUM", "syntax", "script", "SQL", "Possivel erro de digitacao SQL",
                    "Foram encontrados tokens parecidos com comandos SQL escritos incorretamente.",
                    "Use a correcao sugerida e reexecute a validacao.",
                    autoFix(trimmed), 0.86));
        }
    }

    private void inspectStatement(String statement, AnalysisSettings settings, List<AnalysisIssue> issues) {
        String normalized = statement.toUpperCase(Locale.ROOT);
        String objectType = detectObjectType(normalized);
        String objectName = detectObjectName(statement);

        if (settings.isCheckSecurity()) {
            if (SQL_INJECTION.matcher(statement).find()) {
                issues.add(issue("CRITICAL", "security", objectType, objectName, "Padrao de SQL injection",
                        "A consulta contem padroes associados a bypass de autenticacao, UNION injection ou delays maliciosos.",
                        "Use parametros preparados, remova concatenacoes de entrada do usuario e valide filtros.",
                        null, 0.98));
            }
            if (DANGEROUS_DDL.matcher(statement).find()) {
                issues.add(issue("CRITICAL", "security", objectType, objectName, "Comando destrutivo detectado",
                        "DROP ou TRUNCATE pode apagar estrutura ou dados do banco.",
                        "Exija aprovacao manual, backup e janela de manutencao antes de executar.",
                        "-- Revisar manualmente antes de liberar:\n" + statement.trim(), 0.97));
            }
            if (DELETE_WITHOUT_WHERE.matcher(statement).matches()) {
                issues.add(issue("CRITICAL", "security", objectType, objectName, "DELETE sem WHERE",
                        "O comando remove todas as linhas da tabela.",
                        "Inclua um filtro WHERE ou transforme a acao em manutencao explicitamente aprovada.",
                        statement.replaceFirst("(?is);?\\s*$", " WHERE /* condicao */;"), 0.99));
            }
            if (UPDATE_WITHOUT_WHERE.matcher(statement).matches() && !normalized.contains(" WHERE ")) {
                issues.add(issue("CRITICAL", "security", objectType, objectName, "UPDATE sem WHERE",
                        "O comando altera todas as linhas da tabela.",
                        "Inclua um filtro WHERE com chave primaria, periodo ou criterio validado.",
                        statement.replaceFirst("(?is);?\\s*$", " WHERE /* condicao */;"), 0.96));
            }
        }

        if (settings.isCheckSelects() && normalized.contains("SELECT")) {
            if (SELECT_STAR.matcher(statement).find()) {
                issues.add(issue("MEDIUM", "performance", objectType, objectName, "SELECT * encontrado",
                        "Selecionar todas as colunas aumenta IO, rede e risco de expor campos sensiveis.",
                        "Declare apenas as colunas necessarias para a tela ou processo.",
                        statement.replaceFirst("(?is)SELECT\\s+\\*", "SELECT coluna_1, coluna_2"), 0.84));
            }
            if (!normalized.contains(" LIMIT ") && normalized.startsWith("SELECT")) {
                issues.add(issue("LOW", "performance", objectType, objectName, "SELECT sem LIMIT",
                        "Consultas exploratorias sem limite podem carregar volume alto de linhas.",
                        "Adicione LIMIT em analises pontuais ou paginacao em rotas de API.",
                        statement.replaceFirst("(?is);?\\s*$", " LIMIT 100;"), 0.72));
            }
        }

        if (settings.isCheckJoins()) {
            if (JOIN_WITHOUT_CONDITION.matcher(statement).find()) {
                issues.add(issue("HIGH", "logic", objectType, objectName, "JOIN sem ON/USING",
                        "JOIN sem condicao tende a gerar produto cartesiano ou erro de sintaxe.",
                        "Defina ON tabela_a.chave = tabela_b.chave ou USING(chave).",
                        statement.replaceFirst("(?is)\\bJOIN\\s+([`\\w.]+)", "JOIN $1 ON /* tabela.chave = outra.chave */"), 0.9));
            }
            if (COMMA_JOIN.matcher(statement).find()) {
                issues.add(issue("MEDIUM", "logic", objectType, objectName, "JOIN implicito por virgula",
                        "FROM tabela_a, tabela_b dificulta manutencao e pode ocultar produto cartesiano.",
                        "Troque por JOIN explicito com ON.",
                        null, 0.78));
            }
        }

        if (settings.isCheckViews() && normalized.contains("CREATE VIEW") && SELECT_STAR.matcher(statement).find()) {
            issues.add(issue("MEDIUM", "view", objectType, objectName, "VIEW com SELECT *",
                    "A view pode quebrar ou expor novas colunas quando a tabela base mudar.",
                    "Declare a lista de colunas da view explicitamente.",
                    null, 0.84));
        }

        if ((settings.isCheckProcedures() || settings.isCheckFunctions())
                && (normalized.contains("CREATE PROCEDURE") || normalized.contains("CREATE FUNCTION"))) {
            if (normalized.contains("DEFINER=")) {
                issues.add(issue("MEDIUM", "routine", objectType, objectName, "Rotina com DEFINER fixo",
                        "O DEFINER pode falhar em outro ambiente ou elevar privilegios indevidamente.",
                        "Prefira SQL SECURITY INVOKER quando fizer sentido e revise o usuario definidor.",
                        null, 0.75));
            }
            if (normalized.contains("PREPARE ") || normalized.contains("EXECUTE ")) {
                issues.add(issue("HIGH", "routine", objectType, objectName, "SQL dinamico em rotina",
                        "SQL dinamico dentro de procedure/function aumenta risco de injecao e erros em runtime.",
                        "Valide parametros, use listas permitidas e registre auditoria da execucao.",
                        null, 0.88));
            }
        }
    }

    private String autoFix(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return "";
        }
        String fixed = sql.trim()
                .replaceAll("(?i)\\bSELEC\\b", "SELECT")
                .replaceAll("(?i)\\bFORM\\b", "FROM")
                .replaceAll("(?i)\\bWHER\\b", "WHERE")
                .replaceAll("(?i)\\bINSER\\b", "INSERT")
                .replaceAll("(?i)\\bVIWER\\b", "VIEW")
                .replaceAll("(?i)\\bUPDTE\\b", "UPDATE");
        if (!fixed.endsWith(";")) {
            fixed += ";";
        }
        return fixed;
    }

    private List<String> splitStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (ch == '\'' && (i == 0 || sql.charAt(i - 1) != '\\')) {
                inSingleQuote = !inSingleQuote;
            }
            current.append(ch);
            if (ch == ';' && !inSingleQuote) {
                addStatement(statements, current);
            }
        }
        addStatement(statements, current);
        return statements;
    }

    private void addStatement(List<String> statements, StringBuilder current) {
        String statement = current.toString().trim();
        if (!statement.isEmpty()) {
            statements.add(statement);
        }
        current.setLength(0);
    }

    private AnalysisIssue issue(String severity, String category, String objectType, String objectName,
                                String title, String detail, String recommendation, String fixSql, double confidence) {
        return new AnalysisIssue(
                UUID.randomUUID().toString(),
                severity,
                category,
                objectType,
                objectName,
                title,
                detail,
                recommendation,
                fixSql,
                confidence
        );
    }

    private String detectObjectType(String normalized) {
        if (normalized.contains("CREATE PROCEDURE")) {
            return "procedure";
        }
        if (normalized.contains("CREATE FUNCTION")) {
            return "function";
        }
        if (normalized.contains("CREATE VIEW")) {
            return "view";
        }
        if (normalized.startsWith("SELECT")) {
            return "select";
        }
        if (normalized.contains(" JOIN ")) {
            return "join";
        }
        return "statement";
    }

    private String detectObjectName(String statement) {
        String[] tokens = statement.replace("`", "").split("\\s+");
        for (int i = 0; i < tokens.length - 1; i++) {
            String token = tokens[i].toUpperCase(Locale.ROOT);
            if ("TABLE".equals(token) || "VIEW".equals(token) || "PROCEDURE".equals(token) || "FUNCTION".equals(token)
                    || "FROM".equals(token) || "UPDATE".equals(token) || "JOIN".equals(token)) {
                return tokens[i + 1].replaceAll("[;(),]", "");
            }
        }
        return "SQL";
    }

    private boolean containsTypo(String sql) {
        return sql.matches("(?is).*\\b(SELEC|FORM|WHER|INSER|VIWER|UPDTE)\\b.*");
    }

    private int count(String value, char target) {
        int total = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == target) {
                total++;
            }
        }
        return total;
    }

    private int balance(String value, char open, char close) {
        int balance = 0;
        boolean inSingleQuote = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\'' && (i == 0 || value.charAt(i - 1) != '\\')) {
                inSingleQuote = !inSingleQuote;
            }
            if (!inSingleQuote && ch == open) {
                balance++;
            }
            if (!inSingleQuote && ch == close) {
                balance--;
            }
        }
        return balance;
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
