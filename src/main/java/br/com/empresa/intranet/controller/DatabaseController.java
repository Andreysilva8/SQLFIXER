package br.com.empresa.intranet.controller;

import br.com.empresa.intranet.dto.request.DatabaseConnectionRequest;
import br.com.empresa.intranet.service.DatabaseConnectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/database")
@CrossOrigin
public class DatabaseController {
    private final DatabaseConnectionService connectionService;

    public DatabaseController(DatabaseConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @PostMapping("/connect")
    public ResponseEntity<?> connect(@RequestBody DatabaseConnectionRequest request) {
        try {
            return ResponseEntity.ok(connectionService.connect(request));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "connected", false,
                    "message", ex.getMessage()
            ));
        }
    }

    @GetMapping("/status")
    public DatabaseConnectionService.DatabaseConnectionStatus status() {
        return connectionService.status();
    }

    @PostMapping("/disconnect")
    public Map<String, Object> disconnect() {
        connectionService.disconnect();
        return Map.of("connected", false, "message", "Conexao encerrada.");
    }
}
