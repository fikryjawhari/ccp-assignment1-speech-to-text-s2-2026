package edu.adelaide.assignment1speechtotext.service;

import edu.adelaide.assignment1speechtotext.dto.UptimeResponse;
import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Tracks when this server started accepting requests and reports how long it has been up.
 *
 * <p><strong>Which moment counts as "started" has now failed TITAN twice, in opposite
 * directions.</strong> Bean construction was tried first and was too late -- Spring builds this
 * bean partway through startup, before Tomcat binds its port. JVM launch time
 * ({@code ManagementFactory.getRuntimeMXBean().getStartTime()}) was tried second and was too
 * early: on 2026-09-12 TITAN polled the endpoint from the moment it launched the JAR, connected on
 * its seventh attempt, and was told the server had been up 6.268 seconds when it had been serving
 * requests for a fraction of one. T10 passed on the same run because after a minute of uptime a
 * couple of seconds of JVM startup is proportionally invisible; T01 is the check that catches it.
 *
 * <p>The honest answer is neither: the server started when it could first answer a request.
 * {@link ApplicationReadyEvent} is Spring's signal for exactly that moment -- published once the
 * context is refreshed and the web server is accepting connections. Since TITAN cannot connect
 * before that event fires, no client can ever observe an uptime that predates its own first
 * possible request.
 */
@Service
public class UptimeService {

    /**
     * When the server became able to serve requests.
     *
     * <p>{@code volatile} rather than {@code final}, because the value is no longer available at
     * construction time. {@link ApplicationReadyEvent} is published on the thread running startup,
     * while every read happens later on a request thread; without {@code volatile} the Java memory
     * model does not guarantee those threads ever see the write. This is the cost of moving the
     * assignment out of the constructor -- a {@code final} field would have been safely published
     * for free.
     */
    private volatile Instant utcServerStart;

    /**
     * Records the moment the server became ready.
     *
     * <p>{@code @EventListener} registers this method with Spring's application event system: at
     * startup the framework finds the annotation, notes the event type in the parameter, and calls
     * the method when such an event is published. Nothing in this codebase calls it directly.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        this.utcServerStart = Instant.now();
    }

    /**
     * Builds a snapshot of uptime as of now.
     *
     * <p>Both instants come from one reading of the clock, because the contract defines
     * {@code serverUptimeSeconds} as the difference between the other two fields -- taking
     * {@code Instant.now()} twice would let the reported difference disagree with the reported
     * timestamps.
     *
     * <p>The division is by {@code 1000.0}, not {@code 1000}: the schema types this field
     * {@code format: double}, and integer division would silently discard the fractional seconds
     * and report a whole number.
     */
    public UptimeResponse currentUptime() {
        Instant utcNow = Instant.now();
        double serverUptimeSeconds = Duration.between(utcServerStart, utcNow).toMillis() / 1000.0;
        return new UptimeResponse(utcServerStart, utcNow, serverUptimeSeconds);
    }
}
