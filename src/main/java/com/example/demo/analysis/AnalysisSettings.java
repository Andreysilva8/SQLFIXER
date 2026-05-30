package com.example.demo.analysis;

public class AnalysisSettings {
    private boolean checkTables = true;
    private boolean checkIndexes = true;
    private boolean checkViews = true;
    private boolean checkProcedures = true;
    private boolean checkFunctions = true;
    private boolean checkSelects = true;
    private boolean checkJoins = true;
    private boolean checkSyntax = true;
    private boolean checkSecurity = true;
    private boolean autoFixSql = true;
    private long largeTableRows = 50000;
    private long slowQueryMs = 1000;

    public static AnalysisSettings defaults() {
        return new AnalysisSettings();
    }

    public AnalysisSettings merge(AnalysisSettings override) {
        if (override == null) {
            return this;
        }
        this.checkTables = override.checkTables;
        this.checkIndexes = override.checkIndexes;
        this.checkViews = override.checkViews;
        this.checkProcedures = override.checkProcedures;
        this.checkFunctions = override.checkFunctions;
        this.checkSelects = override.checkSelects;
        this.checkJoins = override.checkJoins;
        this.checkSyntax = override.checkSyntax;
        this.checkSecurity = override.checkSecurity;
        this.autoFixSql = override.autoFixSql;
        this.largeTableRows = override.largeTableRows;
        this.slowQueryMs = override.slowQueryMs;
        return this;
    }

    public boolean isCheckTables() {
        return checkTables;
    }

    public void setCheckTables(boolean checkTables) {
        this.checkTables = checkTables;
    }

    public boolean isCheckIndexes() {
        return checkIndexes;
    }

    public void setCheckIndexes(boolean checkIndexes) {
        this.checkIndexes = checkIndexes;
    }

    public boolean isCheckViews() {
        return checkViews;
    }

    public void setCheckViews(boolean checkViews) {
        this.checkViews = checkViews;
    }

    public boolean isCheckProcedures() {
        return checkProcedures;
    }

    public void setCheckProcedures(boolean checkProcedures) {
        this.checkProcedures = checkProcedures;
    }

    public boolean isCheckFunctions() {
        return checkFunctions;
    }

    public void setCheckFunctions(boolean checkFunctions) {
        this.checkFunctions = checkFunctions;
    }

    public boolean isCheckSelects() {
        return checkSelects;
    }

    public void setCheckSelects(boolean checkSelects) {
        this.checkSelects = checkSelects;
    }

    public boolean isCheckJoins() {
        return checkJoins;
    }

    public void setCheckJoins(boolean checkJoins) {
        this.checkJoins = checkJoins;
    }

    public boolean isCheckSyntax() {
        return checkSyntax;
    }

    public void setCheckSyntax(boolean checkSyntax) {
        this.checkSyntax = checkSyntax;
    }

    public boolean isCheckSecurity() {
        return checkSecurity;
    }

    public void setCheckSecurity(boolean checkSecurity) {
        this.checkSecurity = checkSecurity;
    }

    public boolean isAutoFixSql() {
        return autoFixSql;
    }

    public void setAutoFixSql(boolean autoFixSql) {
        this.autoFixSql = autoFixSql;
    }

    public long getLargeTableRows() {
        return largeTableRows;
    }

    public void setLargeTableRows(long largeTableRows) {
        this.largeTableRows = largeTableRows;
    }

    public long getSlowQueryMs() {
        return slowQueryMs;
    }

    public void setSlowQueryMs(long slowQueryMs) {
        this.slowQueryMs = slowQueryMs;
    }
}
