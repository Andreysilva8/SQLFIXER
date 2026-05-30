package br.com.empresa.intranet.controller;

import br.com.empresa.intranet.service.AlertService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alerts")
@CrossOrigin
public class AlertController {
    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public List<AlertService.AlertItem> latest() {
        return alertService.latest();
    }

    @PostMapping("/clear")
    public Map<String, Object> clear() {
        alertService.clear();
        return Map.of("cleared", true);
    }
}
