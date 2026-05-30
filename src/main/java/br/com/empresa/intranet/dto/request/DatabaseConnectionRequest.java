package br.com.empresa.intranet.dto.request;

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
