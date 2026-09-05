package edu.adelaide.assignment1speechtotext.web;

import edu.adelaide.assignment1speechtotext.dto.GlobalStatsResponse;
import edu.adelaide.assignment1speechtotext.service.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes aggregate usage counters under {@code /api/v1/global}.
 *
 * <p>Separate from {@code UptimeController} because the contract groups it under a different
 * base path and a different tag; merging them would mean one class serving two unrelated
 * resources.
 */
@RestController
@RequestMapping("/api/v1/global")
public class StatsController {
    private final StatsService statsService;

    public StatsController(StatsService statsService) { this.statsService = statsService; }

    @GetMapping("/stats")
    public GlobalStatsResponse stats() {
        return statsService.currentStats();
    }
}
