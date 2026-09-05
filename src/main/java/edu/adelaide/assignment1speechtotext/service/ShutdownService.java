package edu.adelaide.assignment1speechtotext.service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;

/**
 * Requests a graceful shutdown of this server process, at most once.
 *
 * <p>Two constraints shape this class. First, the HTTP 202 must reach the client <em>before</em>
 * the server stops, and closing the application context stops Tomcat -- so the close happens on a
 * separate thread after the controller has returned. Second, the contract requires a second
 * shutdown request to answer 409, so acceptance has to be decided by a single atomic check.
 */
@Service
public class ShutdownService {

    private static final Logger log = LoggerFactory.getLogger(ShutdownService.class);

    /**
     * How long the closing thread waits before shutting the context down.
     *
     * <p>Long enough for Tomcat to flush the 202 to the client, short enough to be invisible to
     * a caller waiting on the response. Graceful shutdown starts refusing new connections as soon
     * as it begins, so closing immediately would risk the accepting request never being answered.
     */
    private static final Duration RESPONSE_FLUSH_DELAY = Duration.ofMillis(500);

    /**
     * The Spring container itself, injected like any other bean.
     *
     * <p>{@code ConfigurableApplicationContext} rather than {@code ApplicationContext} because
     * {@code close()} is a lifecycle operation and only the configurable sub-interface exposes it.
     */
    private final ConfigurableApplicationContext applicationContext;

    /**
     * False until a shutdown has been accepted.
     *
     * <p>{@link AtomicBoolean} rather than a plain {@code boolean}: with two shutdown requests
     * arriving at once, "read the flag, see false, set it true" on a plain field lets both threads
     * read false and both proceed. The atomic compare-and-set collapses those two steps into one
     * indivisible operation, so exactly one caller can ever win.
     */
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);

    public ShutdownService(ConfigurableApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * Accepts a shutdown request if none has been accepted yet.
     *
     * @return {@code true} if this call accepted the shutdown and started it; {@code false} if a
     *         shutdown was already in progress, which the controller reports as 409.
     */
    public boolean requestShutdown() {
        if (!shutdownRequested.compareAndSet(false, true)) {
            log.warn("Shutdown already requested");
            return false;
        }
        log.info("Shutdown requested");
        closeContextAsynchronously();
        return true;
    }

    /**
     * Closes the application context on a thread of its own, after the accepting request has had
     * a chance to complete.
     */
    private void closeContextAsynchronously() {
        new Thread(() -> {
            try {
                Thread.sleep(RESPONSE_FLUSH_DELAY);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            log.info("Context closing");
            applicationContext.close();
        }, "shutdown").start();
    }
}
