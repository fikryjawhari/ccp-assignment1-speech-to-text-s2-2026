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
 * context is refreshed and the web server is accepting connections.
 *
 * <p><strong>The claim that no client can connect before that event fires was wrong, and it failed
 * T01 a third time on 2026-09-16 with a 500.</strong> Tomcat begins accepting connections partway
 * through the context refresh, while {@code ApplicationReadyEvent} is published only once that
 * refresh has returned. Between those two moments the endpoint is reachable and the field below is
 * still null, so {@code Duration.between(null, ...)} threw {@code NullPointerException}. TITAN
 * polls from the instant it launches the JAR and retries on connection refusal, which makes it
 * close to a worst-case client for exactly this window; a human with curl never sees it.
 *
 * <p>The general lesson is not about time at all. <strong>Any field not assigned in the constructor
 * has a window where it holds its default value, and code reachable during that window must define
 * what happens.</strong> Moving this field from {@code final} to {@code volatile} traded away the
 * language guarantee that a field cannot be observed before its constructor sets it; {@code
 * volatile} restores visibility but says nothing about ordering. The first two failures were about
 * choosing the right moment. This one was about the window before that moment arrives.
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
        Instant start = utcServerStart;

        if (start == null) {
            start = utcNow;
        }
        double serverUptimeSeconds = Duration.between(start, utcNow).toMillis() / 1000.0;
        return new UptimeResponse(start, utcNow, serverUptimeSeconds);
    }
}
