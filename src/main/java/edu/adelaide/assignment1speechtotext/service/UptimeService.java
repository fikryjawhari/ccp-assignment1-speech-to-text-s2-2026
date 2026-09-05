package edu.adelaide.assignment1speechtotext.service;

import edu.adelaide.assignment1speechtotext.dto.UptimeResponse;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * Tracks when this server process started and reports how long it has been running.
 *
 * <p>The start instant is taken from the JVM's own runtime management bean rather than from
 * {@code Instant.now()} in this constructor. The contract in {@code docs/assignment1api.yaml}
 * defines {@code utcServerStart} as the time the server <em>process</em> started, and Spring does
 * not construct this bean until several seconds into startup -- a gap TITAN measured and failed
 * us on at Stage 1.
 */
@Service
public class UptimeService {

    private final Instant utcServerStart;

    public UptimeService() {
        // ManagementFactory is the JDK's window into the running JVM itself (the same data
        // jconsole shows). getRuntimeMXBean() hands back a live view of the runtime, and
        // getStartTime() is the epoch-millisecond stamp of JVM launch -- fixed for the life of
        // the process, so reading it once here is enough.
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        this.utcServerStart = Instant.ofEpochMilli(runtime.getStartTime());
    }

    /**
     * Builds a snapshot of uptime as of now.
     */
    public UptimeResponse currentUptime() {
        Instant utcNow = Instant.now();
        double serverUptimeSeconds = Duration.between(utcServerStart, utcNow).toMillis() / 1000.0;
        return new UptimeResponse(utcServerStart, utcNow, serverUptimeSeconds);
    }
}
