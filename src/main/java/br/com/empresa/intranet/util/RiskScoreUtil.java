package br.com.empresa.intranet.util;

import br.com.empresa.intranet.dto.response.AnalysisIssue;

import java.util.List;

public final class RiskScoreUtil {
    private RiskScoreUtil() {
    }

    public static int calculate(List<AnalysisIssue> issues) {
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
