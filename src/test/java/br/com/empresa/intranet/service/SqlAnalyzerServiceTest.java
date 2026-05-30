package br.com.empresa.intranet.service;

import br.com.empresa.intranet.dto.response.AnalysisReport;
import br.com.empresa.intranet.dto.request.AnalysisSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlAnalyzerServiceTest {
    private final SqlAnalyzerService service = new SqlAnalyzerService();

    @Test
    void detectsDangerousUpdateWithoutWhere() {
        AnalysisReport report = service.analyze("UPDATE usuarios SET senha = '123';", AnalysisSettings.defaults());

        assertTrue(report.issues().stream().anyMatch(issue -> "UPDATE sem WHERE".equals(issue.title())));
        assertTrue(report.issueCounts().containsKey("CRITICAL"));
    }

    @Test
    void fixesCommonSyntaxTypos() {
        AnalysisReport report = service.analyze("SELEC * FORM usuarios", AnalysisSettings.defaults());

        assertEquals("SELECT * FROM usuarios;", report.fixedSql());
        assertTrue(report.issues().stream().anyMatch(issue -> issue.title().contains("digitacao")));
    }
}
