package edu.adelaide.assignment1speechtotext.web;

import edu.adelaide.assignment1speechtotext.dto.UptimeResponse;
import edu.adelaide.assignment1speechtotext.service.UptimeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes server lifecycle information under {@code /api/v1/admin}.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class UptimeController {

    private final UptimeService uptimeService;

    public UptimeController(UptimeService uptimeService) {
        this.uptimeService = uptimeService;
    }

    @GetMapping("/uptime")
    public UptimeResponse getUptime() {
        return uptimeService.currentUptime();
    }
}
