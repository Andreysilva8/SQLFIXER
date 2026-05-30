(function () {
    const state = {
        seenAlerts: new Set(JSON.parse(localStorage.getItem("mysqlfixer.seenAlerts") || "[]")),
        status: null,
        alerts: [],
        monitorRunning: false,
        monitorInFlight: false,
        monitorRuns: 0
    };

    const icons = {
        sun: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41"/></svg>',
        moon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20.99 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 20.99 12.79z"/></svg>',
        bell: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 7h18s-3 0-3-7"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>',
        alert: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.3"><path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><path d="M12 9v4M12 17h.01"/></svg>',
        check: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M20 6 9 17l-5-5"/></svg>',
        scan: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.3"><path d="M3 7V5a2 2 0 0 1 2-2h2M17 3h2a2 2 0 0 1 2 2v2M21 17v2a2 2 0 0 1-2 2h-2M7 21H5a2 2 0 0 1-2-2v-2M7 12h10"/></svg>',
        plug: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.3"><path d="M12 22v-5M9 8V2M15 8V2M6 8h12v5a6 6 0 0 1-12 0V8z"/></svg>'
    };

    document.addEventListener("DOMContentLoaded", () => {
        applyInitialTheme();
        ensureNavbarActions();
        bindConnectionPage();
        bindAnalyzerPage();
        bindUsersPage();
        refreshStatus();
        refreshAlerts();
        refreshMonitorStatus();
        setInterval(refreshStatus, 8000);
        setInterval(refreshAlerts, 6000);
        setInterval(refreshMonitorStatus, 5000);
    });

    function applyInitialTheme() {
        const queryTheme = new URLSearchParams(location.search).get("theme");
        const saved = queryTheme === "light" || queryTheme === "dark"
            ? queryTheme
            : localStorage.getItem("mysqlfixer.theme") || "dark";
        document.documentElement.dataset.theme = saved;
    }

    function ensureNavbarActions() {
        const navbar = document.querySelector(".navbar");
        const host = navbar || document.body;
        let actions = document.querySelector(".navbar-actions");
        if (!actions) {
            actions = document.createElement("div");
            actions.className = "navbar-actions";
            if (navbar) {
                const status = navbar.querySelector(".navbar-status");
                if (status) {
                    actions.appendChild(status);
                }
                navbar.appendChild(actions);
            } else {
                actions.style.position = "fixed";
                actions.style.top = "14px";
                actions.style.right = "14px";
                actions.style.zIndex = "200";
                host.appendChild(actions);
            }
        }

        if (!actions.querySelector(".notification-toggle")) {
            const button = document.createElement("button");
            button.type = "button";
            button.className = "notification-toggle";
            button.title = "Ativar notificações";
            button.innerHTML = icons.bell;
            button.addEventListener("click", requestNotifications);
            actions.appendChild(button);
        }

        if (!actions.querySelector(".theme-toggle")) {
            const button = document.createElement("button");
            button.type = "button";
            button.className = "theme-toggle";
            button.title = "Alternar tema";
            button.addEventListener("click", toggleTheme);
            actions.appendChild(button);
            updateThemeIcon();
        }
    }

    function toggleTheme() {
        const next = document.documentElement.dataset.theme === "light" ? "dark" : "light";
        document.documentElement.dataset.theme = next;
        localStorage.setItem("mysqlfixer.theme", next);
        updateThemeIcon();
        window.dispatchEvent(new CustomEvent("mysqlfixer:theme-change"));
    }

    function updateThemeIcon() {
        const button = document.querySelector(".theme-toggle");
        if (!button) return;
        button.innerHTML = document.documentElement.dataset.theme === "light" ? icons.moon : icons.sun;
    }

    async function api(path, options = {}) {
        const response = await fetch(path, {
            headers: options.body instanceof FormData ? {} : { "Content-Type": "application/json" },
            ...options
        });
        const contentType = response.headers.get("content-type") || "";
        const payload = contentType.includes("application/json") ? await response.json() : await response.text();
        if (!response.ok) {
            throw new Error(payload.message || payload || "Falha na requisição.");
        }
        return payload;
    }

    function bindConnectionPage() {
        const form = document.getElementById("connection-form");
        if (!form) return;

        const modeButtons = document.querySelectorAll("[data-connection-mode]");
        modeButtons.forEach((button) => {
            button.addEventListener("click", () => setConnectionMode(button.dataset.connectionMode));
        });
        setConnectionMode(document.querySelector("[data-connection-mode].active")?.dataset.connectionMode || "jdbc");

        form.addEventListener("submit", async (event) => {
            event.preventDefault();
            const mode = document.querySelector("[data-connection-mode].active")?.dataset.connectionMode || "jdbc";
            const payload = mode === "api"
                ? {
                    mode: "api",
                    apiBaseUrl: value("apiBaseUrl"),
                    apiToken: value("apiToken")
                }
                : {
                    mode: "jdbc",
                    host: value("dbHost") || "localhost",
                    port: Number(value("dbPort") || 3306),
                    database: value("dbName") || "db_mysqlfixer",
                    username: value("dbUser") || "root",
                    password: value("dbPassword"),
                    useSsl: document.getElementById("dbSsl")?.checked || false
                };
            await runWithStatus("connection-result", "Conectando...", async () => {
                const status = await api("/api/database/connect", {
                    method: "POST",
                    body: JSON.stringify(payload)
                });
                renderStatus(status);
                await refreshStatus();
                return formatStatus(status);
            });
        });

        const scanButton = document.getElementById("run-db-scan");
        if (scanButton) {
            scanButton.addEventListener("click", runDatabaseScan);
        }
    }

    function setConnectionMode(mode) {
        document.querySelectorAll("[data-connection-mode]").forEach((button) => {
            button.classList.toggle("active", button.dataset.connectionMode === mode);
        });
        document.querySelectorAll(".connection-mode-fields").forEach((section) => {
            section.hidden = section.dataset.mode !== mode;
        });
    }

    function bindAnalyzerPage() {
        const sqlForm = document.getElementById("sql-form");
        if (sqlForm) {
            sqlForm.addEventListener("submit", async (event) => {
                event.preventDefault();
                await analyzeSql();
            });
        }

        const fileInput = document.getElementById("arquivo");
        if (fileInput) {
            fileInput.addEventListener("change", async () => {
                const file = fileInput.files[0];
                if (!file) return;
                document.getElementById("file-label").textContent = file.name;
                document.getElementById("file-name").style.display = "inline-flex";
                const text = await file.text();
                const editor = document.getElementById("sql-editor");
                if (editor) editor.value = text;
            });
        }

        const dbScan = document.getElementById("analyze-db-from-sql");
        if (dbScan) {
            dbScan.addEventListener("click", runDatabaseScan);
        }

        bindContinuousMonitor();

        const dataForm = document.getElementById("data-anomaly-form");
        if (dataForm) {
            dataForm.addEventListener("submit", async (event) => {
                event.preventDefault();
                await analyzeData();
            });
        }

        const settingsForm = document.getElementById("analysis-settings");
        if (settingsForm) {
            loadSettings(settingsForm);
            settingsForm.addEventListener("change", () => saveSettings(settingsForm));
        }
    }

    function bindUsersPage() {
        if (typeof window.listar === "function") {
            window.listar();
        }
    }

    async function analyzeSql() {
        const sql = value("sql-editor");
        const settings = collectSettings();
        await runWithStatus("resultado", "Analisando SQL...", async () => {
            const report = await api("/api/analyzer/sql", {
                method: "POST",
                body: JSON.stringify({ sql, settings })
            });
            renderReport(report, "sql-report");
            return reportToText(report);
        });
        await refreshAlerts();
    }

    async function runDatabaseScan() {
        try {
            await executeDatabaseScan(collectDatabaseAnalysisRequest(), "db-scan-output", "Executando varredura do banco...");
        } catch (error) {
            showDatabaseScanError(error);
        }
        await refreshAlerts();
    }

    async function executeDatabaseScan(request, outputId, loadingText) {
        const output = document.getElementById(outputId);
        if (output) output.textContent = loadingText;
        try {
            const report = await api("/api/analyzer/database", {
                method: "POST",
                body: JSON.stringify(request)
            });
            renderReport(report, "db-report");
            hydrateDashboardReport(report);
            if (output) output.textContent = reportToText(report);
            return report;
        } catch (error) {
            if (output) output.textContent = "ERRO: " + error.message;
            showToast(error.message, "red");
            error.mysqlfixerShown = true;
            throw error;
        }
    }

    function bindContinuousMonitor() {
        const start = document.getElementById("monitor-start");
        const stop = document.getElementById("monitor-stop");
        const targetType = document.getElementById("monitor-target-type");
        if (!start || !stop) return;

        start.addEventListener("click", startContinuousMonitor);
        stop.addEventListener("click", stopContinuousMonitor);
        if (targetType) {
            targetType.addEventListener("change", updateMonitorTargetHint);
            updateMonitorTargetHint();
        }
    }

    async function startContinuousMonitor() {
        try {
            const request = collectDatabaseMonitorRequest();
            setMonitorButtons(true);
            setMonitorStatus("Iniciando...", "tag-cyan");
            const status = await api("/api/monitor/database/start", {
                method: "POST",
                body: JSON.stringify(request)
            });
            renderMonitorStatus(status);
        } catch (error) {
            showDatabaseScanError(error);
            setMonitorButtons(false);
            setMonitorStatus("Parado", "tag-amber");
        }
    }

    async function stopContinuousMonitor(showMessage = true) {
        try {
            const status = await api("/api/monitor/database/stop", { method: "POST" });
            renderMonitorStatus(status);
            if (showMessage) {
                showToast("Monitoramento parado.", "amber");
            }
        } catch (error) {
            showDatabaseScanError(error);
        }
    }

    async function refreshMonitorStatus() {
        if (!document.getElementById("monitor-status")) return;
        try {
            const status = await api("/api/monitor/database/status");
            renderMonitorStatus(status);
        } catch (error) {
            setMonitorButtons(false);
            setMonitorStatus("Offline", "tag-red");
        }
    }

    function collectDatabaseAnalysisRequest(options = {}) {
        const targetType = value("monitor-target-type") || "full";
        const targetName = value("monitor-target-name");
        if (targetType === "table" && !targetName) {
            throw new Error("Informe o nome da tabela que deve ser monitorada.");
        }
        return {
            settings: settingsForTarget(collectSettings(), targetType),
            targetType,
            targetName,
            monitor: Boolean(options.monitor)
        };
    }

    function collectDatabaseMonitorRequest() {
        const request = collectDatabaseAnalysisRequest({ monitor: true });
        request.intervalSeconds = Math.max(Number(value("monitor-interval-seconds") || 30), 10);
        return request;
    }

    function settingsForTarget(settings, targetType) {
        const scoped = { ...settings };
        if (targetType === "full") return scoped;
        scoped.checkTables = targetType === "tables" || targetType === "table";
        scoped.checkIndexes = targetType === "tables" || targetType === "table";
        scoped.checkViews = targetType === "views";
        scoped.checkProcedures = targetType === "routines";
        scoped.checkFunctions = targetType === "routines";
        scoped.checkSelects = targetType === "performance";
        scoped.checkJoins = targetType === "performance";
        scoped.checkSecurity = targetType === "tables" || targetType === "table" || settings.checkSecurity;
        return scoped;
    }

    function updateMonitorTargetHint() {
        const targetType = value("monitor-target-type") || "full";
        const targetName = document.getElementById("monitor-target-name");
        const helper = document.getElementById("monitor-target-helper");
        if (!targetName || !helper) return;
        const hints = {
            full: ["ex: deixe vazio para analisar tudo", "Opcional no schema completo."],
            tables: ["ex: deixe vazio para todas as tabelas", "Opcional para todas as tabelas."],
            table: ["ex: usuarios", "Obrigatorio para tabela especifica."],
            views: ["ex: vw_vendas ou deixe vazio", "Opcional para views."],
            routines: ["ex: sp_processar_pedido ou deixe vazio", "Opcional para procedures/functions."],
            performance: ["ex: usuarios ou deixe vazio", "Filtra queries que contenham esse texto."]
        };
        targetName.placeholder = hints[targetType]?.[0] || hints.full[0];
        helper.textContent = hints[targetType]?.[1] || hints.full[1];
    }

    function setMonitorButtons(running) {
        const start = document.getElementById("monitor-start");
        const stop = document.getElementById("monitor-stop");
        if (start) start.disabled = running;
        if (stop) stop.disabled = !running;
    }

    function setMonitorStatus(text, tone) {
        const status = document.getElementById("monitor-status");
        if (!status) return;
        status.className = `tag ${tone || "tag-amber"}`;
        status.textContent = text;
    }

    function renderMonitorStatus(status) {
        if (!status) return;
        state.monitorRunning = Boolean(status.running);
        state.monitorInFlight = Boolean(status.inFlight);
        state.monitorRuns = Number(status.runCount || 0);
        setMonitorButtons(status.running);

        if (!status.running) {
            setMonitorStatus("Parado", "tag-amber");
        } else if (status.inFlight) {
            setMonitorStatus("Analisando...", "tag-cyan");
        } else if (status.lastError) {
            setMonitorStatus("Aguardando", "tag-amber");
        } else {
            const issues = status.lastReport?.totalIssues ?? 0;
            setMonitorStatus(`Ativo (${status.runCount || 0})`, issues > 0 ? "tag-red" : "tag-green");
        }

        const output = document.getElementById("db-scan-output");
        if (!output) return;
        if (status.lastReport) {
            renderReport(status.lastReport, "db-report");
            hydrateDashboardReport(status.lastReport);
            output.textContent = reportToText(status.lastReport);
            return;
        }
        if (status.running && status.lastError) {
            output.textContent = status.lastError;
            return;
        }
        if (status.running) {
            output.textContent = `Monitoramento ativo. Alvo: ${status.targetType || "full"}${status.targetName ? " / " + status.targetName : ""}. Intervalo: ${status.intervalSeconds || 30}s.`;
        }
    }

    function showDatabaseScanError(error) {
        const message = error?.message || "Falha na varredura do banco.";
        const output = document.getElementById("db-scan-output");
        if (output && !output.textContent.startsWith("ERRO:")) {
            output.textContent = "ERRO: " + message;
        }
        if (!error?.mysqlfixerShown) {
            showToast(message, "red");
        }
    }

    async function analyzeData() {
        await runWithStatus("data-anomaly-output", "Analisando dados...", async () => {
            const request = parseDataAnomalyRequest(value("data-anomaly-input"));
            const report = await api("/api/analyzer/data", {
                method: "POST",
                body: JSON.stringify(request)
            });
            renderReport(report, "data-report");
            return reportToText(report);
        });
        await refreshAlerts();
    }

    function parseDataAnomalyRequest(rawValue) {
        if (!rawValue) {
            throw new Error("Informe um array JSON com as linhas para analisar.");
        }
        let parsed;
        try {
            parsed = JSON.parse(rawValue);
        } catch (error) {
            throw new Error("JSON invalido: " + error.message);
        }
        if (Array.isArray(parsed)) {
            return { sourceName: "dataset JSON", rows: parsed };
        }
        if (parsed && Array.isArray(parsed.rows)) {
            return parsed;
        }
        throw new Error("O JSON precisa ser um array de objetos ou conter a propriedade rows.");
    }

    async function runWithStatus(outputId, loadingText, task) {
        const output = document.getElementById(outputId);
        if (output) output.textContent = loadingText;
        try {
            const text = await task();
            if (output) output.textContent = text;
        } catch (error) {
            if (output) output.textContent = "ERRO: " + error.message;
            showToast(error.message, "red");
        }
    }

    async function loadSettings(form) {
        try {
            const settings = await api("/api/settings");
            Object.entries(settings).forEach(([key, val]) => {
                const input = form.querySelector(`[name="${key}"]`);
                if (!input) return;
                if (input.type === "checkbox") {
                    input.checked = Boolean(val);
                } else {
                    input.value = val;
                }
            });
        } catch (error) {
            showToast("Configurações locais ativas.", "amber");
        }
    }

    async function saveSettings(form) {
        const settings = collectSettings(form);
        try {
            await api("/api/settings", {
                method: "PUT",
                body: JSON.stringify(settings)
            });
        } catch (error) {
            showToast(error.message, "red");
        }
    }

    function collectSettings(root) {
        const host = root || document.getElementById("analysis-settings");
        const defaults = {
            checkTables: true,
            checkIndexes: true,
            checkViews: true,
            checkProcedures: true,
            checkFunctions: true,
            checkSelects: true,
            checkJoins: true,
            checkSyntax: true,
            checkSecurity: true,
            autoFixSql: true,
            largeTableRows: 50000,
            slowQueryMs: 1000
        };
        if (!host) return defaults;
        Object.keys(defaults).forEach((key) => {
            const input = host.querySelector(`[name="${key}"]`);
            if (!input) return;
            defaults[key] = input.type === "checkbox" ? input.checked : Number(input.value || defaults[key]);
        });
        return defaults;
    }

    async function refreshStatus() {
        try {
            const status = await api("/api/database/status");
            state.status = status;
            renderStatus(status);
        } catch (error) {
            renderStatus({ connected: false, message: "Backend offline" });
        }
        updateUserCount();
    }

    async function updateUserCount() {
        const targets = document.querySelectorAll("#user-count, [data-metric='users']");
        if (!targets.length) return;
        try {
            const users = await api("/usuarios");
            targets.forEach((target) => target.textContent = users.length);
        } catch (error) {
            targets.forEach((target) => target.textContent = "--");
        }
    }

    function renderStatus(status) {
        const statusText = status.connected
            ? `${status.mode} · ${status.database || status.apiBaseUrl || "conectado"}`
            : "Sem conexão";
        document.querySelectorAll("[data-db-status]").forEach((el) => {
            el.textContent = statusText;
        });
        document.querySelectorAll(".navbar-status").forEach((el) => {
            el.innerHTML = `<span class="pulse ${status.connected ? "" : "amber"}"></span>${status.connected ? "MONITORAMENTO ATIVO" : "AGUARDANDO CONEXÃO"}`;
        });
        const box = document.getElementById("connection-status-card");
        if (box) {
            box.innerHTML = `
                <div class="status-line">
                    <span class="pulse ${status.connected ? "" : "amber"}"></span>
                    <strong>${escapeHtml(statusText)}</strong>
                    <span>${escapeHtml(status.message || "")}</span>
                </div>
                ${status.connected ? `<div class="panel-sub">Versão: ${escapeHtml(status.serverVersion || "-")} · Latência: ${status.latencyMs || 0}ms</div>` : ""}
            `;
        }
    }

    function formatStatus(status) {
        return [
            status.connected ? "Conexão validada." : "Conexão não validada.",
            `Modo: ${status.mode || "NONE"}`,
            `Destino: ${status.database || status.apiBaseUrl || "-"}`,
            `Versão/Resposta: ${status.serverVersion || "-"}`,
            `Latência: ${status.latencyMs || 0}ms`
        ].join("\n");
    }

    async function refreshAlerts() {
        try {
            const alerts = await api("/api/alerts");
            state.alerts = alerts;
            renderAlerts(alerts);
            notifyNewAlerts(alerts);
        } catch (error) {
            renderAlerts([]);
        }
    }

    function renderAlerts(alerts) {
        document.querySelectorAll("[data-alert-count]").forEach((el) => {
            el.textContent = alerts.length;
        });
        const feed = document.getElementById("latest-alerts");
        if (!feed) return;
        if (!alerts.length) {
            feed.innerHTML = `<div class="empty-state">Nenhum alerta crítico no momento.</div>`;
            return;
        }
        feed.innerHTML = alerts.slice(0, 8).map((alert) => `
            <div class="alert-item">
                <div class="alert-icon ${alertClass(alert.severity)}"><div class="alert-sev"></div></div>
                <div>
                    <div class="alert-msg">${escapeHtml(alert.title)}</div>
                    <div class="alert-meta">${escapeHtml(alert.database || alert.source || "sistema")} · ${formatDate(alert.detectedAt)}</div>
                </div>
                <span class="tag ${severityTag(alert.severity)}">${escapeHtml(alert.severity)}</span>
            </div>
        `).join("");
    }

    function notifyNewAlerts(alerts) {
        if (!("Notification" in window) || Notification.permission !== "granted") return;
        alerts.forEach((alert) => {
            if (state.seenAlerts.has(alert.id)) return;
            if (alert.severity === "CRITICAL" || alert.severity === "HIGH") {
                new Notification("MySQLFixer detectou um erro", {
                    body: `${alert.title} · ${alert.database || alert.source || "banco"}`,
                    tag: alert.id
                });
            }
            state.seenAlerts.add(alert.id);
        });
        localStorage.setItem("mysqlfixer.seenAlerts", JSON.stringify([...state.seenAlerts].slice(-100)));
    }

    async function requestNotifications() {
        if (!("Notification" in window)) {
            showToast("Este navegador não suporta notificações.", "amber");
            return;
        }
        const permission = await Notification.requestPermission();
        showToast(permission === "granted" ? "Notificações ativadas." : "Notificações não ativadas.", permission === "granted" ? "green" : "amber");
    }

    function renderReport(report, containerId) {
        const container = document.getElementById(containerId);
        if (!container) return;
        if (!report || !report.issues || report.issues.length === 0) {
            container.innerHTML = `<div class="empty-state">Nenhuma anomalia encontrada nesta análise.</div>`;
            return;
        }
        container.innerHTML = report.issues.map((issue) => `
            <div class="issue-item">
                <div class="issue-icon ${escapeHtml(issue.severity)}">${icons.alert}</div>
                <div>
                    <div class="issue-title">${escapeHtml(issue.title)}</div>
                    <div class="issue-detail">${escapeHtml(issue.detail)}</div>
                    <div class="issue-recommendation">${escapeHtml(issue.recommendation || "")}</div>
                    ${issue.fixSql ? `<pre class="issue-fix">${escapeHtml(issue.fixSql)}</pre>` : ""}
                </div>
                <span class="tag ${severityTag(issue.severity)}">${escapeHtml(issue.severity)}</span>
            </div>
        `).join("");
    }

    function hydrateDashboardReport(report) {
        if (!report) return;
        const risk = document.querySelector("[data-metric='risk']");
        const issues = document.querySelector("[data-metric='issues']");
        const tables = document.querySelector("[data-metric='tables']");
        if (risk) risk.textContent = report.metrics?.riskScore ?? 0;
        if (issues) issues.textContent = report.totalIssues ?? 0;
        if (tables) tables.textContent = report.metrics?.tables ?? "--";
    }

    function reportToText(report) {
        const lines = [
            `Relatório: ${report.id}`,
            `Origem: ${report.source}`,
            `Banco: ${report.database}`,
            `Issues: ${report.totalIssues}`,
            `Score de risco: ${report.metrics?.riskScore ?? 0}`,
            ""
        ];
        if (report.fixedSql) {
            lines.push("Correção SQL sugerida:", report.fixedSql, "");
        }
        if (!report.issues || report.issues.length === 0) {
            lines.push("Nenhuma anomalia detectada.");
            return lines.join("\n");
        }
        report.issues.forEach((issue, index) => {
            lines.push(`${index + 1}. [${issue.severity}] ${issue.title}`);
            lines.push(`   Objeto: ${issue.objectType} · ${issue.objectName}`);
            lines.push(`   Detalhe: ${issue.detail}`);
            if (issue.fixSql) lines.push(`   Fix: ${issue.fixSql.replace(/\n/g, " ")}`);
            lines.push("");
        });
        return lines.join("\n");
    }

    function showToast(message, tone) {
        const toast = document.createElement("div");
        toast.className = `tag tag-${tone || "cyan"}`;
        toast.textContent = message;
        toast.style.position = "fixed";
        toast.style.right = "16px";
        toast.style.bottom = "16px";
        toast.style.zIndex = "300";
        toast.style.boxShadow = "var(--shadow)";
        document.body.appendChild(toast);
        setTimeout(() => toast.remove(), 3600);
    }

    function value(id) {
        return document.getElementById(id)?.value?.trim() || "";
    }

    function severityTag(severity) {
        if (severity === "CRITICAL" || severity === "HIGH") return "tag-red";
        if (severity === "MEDIUM") return "tag-amber";
        if (severity === "LOW") return "tag-blue";
        return "tag-cyan";
    }

    function alertClass(severity) {
        if (severity === "CRITICAL" || severity === "HIGH") return "crit";
        if (severity === "MEDIUM") return "warn";
        return "info";
    }

    function formatDate(value) {
        if (!value) return "--";
        return new Date(value).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" });
    }

    function escapeHtml(value) {
        return String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;");
    }

    window.MySQLFixerApp = {
        api,
        runDatabaseScan,
        startContinuousMonitor,
        stopContinuousMonitor,
        analyzeSql,
        analyzeData,
        refreshAlerts,
        refreshStatus,
        renderReport,
        reportToText,
        icons
    };
})();
