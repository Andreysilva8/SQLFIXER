package com.example.demo.service;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Properties;

@Service
public class DatabaseConnectionService {
    private final AlertService alertService;
    private volatile DatabaseProfile currentProfile;

    public DatabaseConnectionService(AlertService alertService) {
        this.alertService = alertService;
    }

    public synchronized DatabaseConnectionStatus connect(DatabaseConnectionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Informe os dados de conexao.");
        }

        String mode = normalizeMode(request.mode());
        if ("API".equals(mode)) {
            return connectApi(request);
        }
        return connectJdbc(request);
    }

    public DatabaseConnectionStatus status() {
        DatabaseProfile profile = currentProfile;
        if (profile == null) {
            return new DatabaseConnectionStatus(false, "NONE", null, null, null, null, 0, null, "Nenhuma conexao ativa.");
        }
        return profile.toStatus(true, "Conexao ativa.");
    }

    public synchronized void disconnect() {
        currentProfile = null;
    }

    public Connection openConnection() throws Exception {
        DatabaseProfile profile = currentProfile;
        if (profile == null) {
            throw new IllegalStateException("Conecte um banco antes de iniciar a varredura.");
        }
        if (!"JDBC".equals(profile.mode())) {
            throw new IllegalStateException("A varredura estrutural precisa de uma conexao por login do banco.");
        }
        Properties props = new Properties();
        props.setProperty("user", profile.username());
        props.setProperty("password", profile.password());
        props.setProperty("connectTimeout", "5000");
        props.setProperty("socketTimeout", "12000");
        return DriverManager.getConnection(profile.jdbcUrl(), props);
    }

    public String activeDatabaseName() {
        DatabaseProfile profile = currentProfile;
        return profile == null ? null : profile.database();
    }

    private DatabaseConnectionStatus connectJdbc(DatabaseConnectionRequest request) {
        String jdbcUrl = buildJdbcUrl(request);
        String username = textOrDefault(request.username(), "root");
        String password = request.password() == null ? "" : request.password();

        long start = System.nanoTime();
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT VERSION(), DATABASE()")) {

            resultSet.next();
            long latencyMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
            String serverVersion = resultSet.getString(1);
            String database = textOrDefault(resultSet.getString(2), extractDatabaseFromUrl(jdbcUrl));
            DatabaseProfile profile = new DatabaseProfile(
                    "JDBC",
                    jdbcUrl,
                    request.host(),
                    null,
                    username,
                    password,
                    database,
                    serverVersion,
                    latencyMs,
                    Instant.now()
            );
            currentProfile = profile;
            alertService.publish("INFO", "Banco conectado", "Conexao validada em " + latencyMs + "ms.", "database", database);
            return profile.toStatus(true, "Conexao por login do banco validada.");
        } catch (Exception ex) {
            currentProfile = null;
            throw new IllegalStateException("Falha ao conectar no MySQL: " + ex.getMessage(), ex);
        }
    }

    private DatabaseConnectionStatus connectApi(DatabaseConnectionRequest request) {
        String apiBaseUrl = normalizeApiUrl(request.apiBaseUrl());
        long start = System.nanoTime();
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(4))
                    .build();
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl))
                    .timeout(Duration.ofSeconds(6))
                    .GET();
            if (hasText(request.apiToken())) {
                builder.header("Authorization", "Bearer " + request.apiToken().trim());
            }
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 500) {
                throw new IllegalStateException("API respondeu HTTP " + response.statusCode());
            }

            long latencyMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
            DatabaseProfile profile = new DatabaseProfile(
                    "API",
                    null,
                    null,
                    apiBaseUrl,
                    null,
                    null,
                    "API externa",
                    "HTTP " + response.statusCode(),
                    latencyMs,
                    Instant.now()
            );
            currentProfile = profile;
            alertService.publish("INFO", "API conectada", "Endpoint respondeu em " + latencyMs + "ms.", "api", "API externa");
            return profile.toStatus(true, "Conexao via API validada.");
        } catch (Exception ex) {
            currentProfile = null;
            throw new IllegalStateException("Falha ao conectar via API: " + ex.getMessage(), ex);
        }
    }

    private String buildJdbcUrl(DatabaseConnectionRequest request) {
        if (hasText(request.jdbcUrl())) {
            return request.jdbcUrl().trim();
        }
        String host = textOrDefault(request.host(), "localhost");
        int port = request.port() == null || request.port() <= 0 ? 3306 : request.port();
        String database = textOrDefault(request.database(), "db_mysqlfixer");
        boolean useSsl = Boolean.TRUE.equals(request.useSsl());
        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=" + useSsl
                + "&allowPublicKeyRetrieval=true"
                + "&serverTimezone=UTC";
    }

    private String normalizeMode(String mode) {
        if (!hasText(mode)) {
            return "JDBC";
        }
        String normalized = mode.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("API")) {
            return "API";
        }
        return "JDBC";
    }

    private String normalizeApiUrl(String apiBaseUrl) {
        if (!hasText(apiBaseUrl)) {
            throw new IllegalArgumentException("Informe a URL da API.");
        }
        String url = apiBaseUrl.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        return url;
    }

    private String extractDatabaseFromUrl(String jdbcUrl) {
        int slash = jdbcUrl.lastIndexOf('/');
        if (slash < 0 || slash == jdbcUrl.length() - 1) {
            return "desconhecido";
        }
        String database = jdbcUrl.substring(slash + 1);
        int params = database.indexOf('?');
        return params >= 0 ? database.substring(0, params) : database;
    }

    private String textOrDefault(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public record DatabaseConnectionRequest(
            String mode,
            String jdbcUrl,
            String host,
            Integer port,
            String database,
            String username,
            String password,
            Boolean useSsl,
            String apiBaseUrl,
            String apiToken
    ) {
    }

    private record DatabaseProfile(
            String mode,
            String jdbcUrl,
            String host,
            String apiBaseUrl,
            String username,
            String password,
            String database,
            String serverVersion,
            long latencyMs,
            Instant connectedAt
    ) {
        DatabaseConnectionStatus toStatus(boolean connected, String message) {
            return new DatabaseConnectionStatus(
                    connected,
                    mode,
                    database,
                    host,
                    apiBaseUrl,
                    serverVersion,
                    latencyMs,
                    connectedAt,
                    message
            );
        }
    }

    public record DatabaseConnectionStatus(
            boolean connected,
            String mode,
            String database,
            String host,
            String apiBaseUrl,
            String serverVersion,
            long latencyMs,
            Instant connectedAt,
            String message
    ) {
    }
}
