package br.com.empresa.intranet.service;

import br.com.empresa.intranet.dto.request.AnalysisSettings;
import br.com.empresa.intranet.dto.request.DatabaseAnalysisRequest;
import br.com.empresa.intranet.dto.request.DatabaseMonitorRequest;
import br.com.empresa.intranet.dto.response.AnalysisReport;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class DatabaseMonitorService {
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "mysqlfixer-monitor");
        thread.setDaemon(true);
        return thread;
    });

    private final DatabaseAnalyzerService analyzerService;
    private final AnalysisSettingsService settingsService;
    private final AlertService alertService;
    private final AtomicBoolean inFlight = new AtomicBoolean(false);

    private ScheduledFuture<?> scheduledTask;
    private MonitorConfig config;
    private AnalysisReport lastReport;
    private String lastError;
    private Instant startedAt;
    private Instant lastRunAt;
    private Instant lastSuccessAt;
    private long runCount;

    public DatabaseMonitorService(DatabaseAnalyzerService analyzerService,
                                  AnalysisSettingsService settingsService,
                                  AlertService alertService) {
        this.analyzerService = analyzerService;
        this.settingsService = settingsService;
        this.alertService = alertService;
    }

    public synchronized DatabaseMonitorStatus start(DatabaseMonitorRequest request) {
        stopInternal();
        DatabaseMonitorRequest safeRequest = request == null
                ? new DatabaseMonitorRequest(null, "full", null, 30)
                : request;
        AnalysisSettings resolvedSettings = settingsService.resolve(safeRequest.settings());
        DatabaseAnalysisRequest analysisRequest = safeRequest.toAnalysisRequest();
        int intervalSeconds = safeRequest.resolvedIntervalSeconds();

        config = new MonitorConfig(resolvedSettings, analysisRequest, intervalSeconds);
        startedAt = Instant.now();
        lastError = null;
        lastReport = null;
        lastRunAt = null;
        lastSuccessAt = null;
        runCount = 0;

        scheduledTask = executor.scheduleAtFixedRate(this::runOnceSafely, 0, intervalSeconds, TimeUnit.SECONDS);
        return status("Monitoramento iniciado.");
    }

    public synchronized DatabaseMonitorStatus stop() {
        stopInternal();
        lastError = null;
        return status("Monitoramento parado.");
    }

    public synchronized DatabaseMonitorStatus status() {
        return status(isRunning() ? "Monitoramento ativo." : "Monitoramento parado.");
    }

    private void runOnceSafely() {
        if (!inFlight.compareAndSet(false, true)) {
            return;
        }
        try {
            MonitorConfig current = config;
            if (current == null) {
                return;
            }
            lastRunAt = Instant.now();
            AnalysisReport report = analyzerService.analyze(current.settings(), current.request());
            lastReport = report;
            lastSuccessAt = Instant.now();
            lastError = null;
            runCount++;
            alertService.publishReport(report);
        } catch (Exception ex) {
            lastRunAt = Instant.now();
            lastError = friendlyMessage(ex);
        } finally {
            inFlight.set(false);
        }
    }

    private synchronized void stopInternal() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledTask = null;
        }
        config = null;
        inFlight.set(false);
    }

    private synchronized boolean isRunning() {
        return scheduledTask != null && !scheduledTask.isCancelled();
    }

    private synchronized DatabaseMonitorStatus status(String message) {
        MonitorConfig current = config;
        DatabaseAnalysisRequest request = current == null ? null : current.request();
        return new DatabaseMonitorStatus(
                isRunning(),
                inFlight.get(),
                current == null ? 0 : current.intervalSeconds(),
                request == null ? "full" : request.resolvedTargetType(),
                request == null ? null : request.resolvedTargetName(),
                startedAt,
                lastRunAt,
                lastSuccessAt,
                runCount,
                lastError,
                lastReport,
                message
        );
    }

    private String friendlyMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return "Nao foi possivel executar a varredura agora.";
        }
        if (message.contains("Conecte um banco")) {
            return "Aguardando conexao com o banco. Conecte na Home para o monitor iniciar as varreduras.";
        }
        return message;
    }

    private record MonitorConfig(
            AnalysisSettings settings,
            DatabaseAnalysisRequest request,
            int intervalSeconds
    ) {
    }

    public record DatabaseMonitorStatus(
            boolean running,
            boolean inFlight,
            int intervalSeconds,
            String targetType,
            String targetName,
            Instant startedAt,
            Instant lastRunAt,
            Instant lastSuccessAt,
            long runCount,
            String lastError,
            AnalysisReport lastReport,
            String message
    ) {
    }
}
