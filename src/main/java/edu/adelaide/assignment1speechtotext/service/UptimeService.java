package edu.adelaide.assignment1speechtotext.service;

import edu.adelaide.assignment1speechtotext.dto.UptimeResponse;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * Tracks when this server process started and reports how long it has been running.
 */
@Service
public class UptimeService {
    private final Instant utcServerStart;

    public UptimeService() {
        this.utcServerStart = Instant.now();
    }

    /**
     * Builds a snapshot of uptime as of now.
     */
    public UptimeResponse currentUptime() {
        Instant utcNow = Instant.now();
        double serverUptimeSeconds = Duration.between(utcServerStart, utcNow).toMillis()/1000.0;
        return new UptimeResponse(utcServerStart, utcNow, serverUptimeSeconds);
    }
}
