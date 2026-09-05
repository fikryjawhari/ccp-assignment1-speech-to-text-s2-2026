package edu.adelaide.assignment1speechtotext.service;

import edu.adelaide.assignment1speechtotext.dto.GlobalStatsResponse;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

/**
 * Accumulates speech-to-text token usage for the life of this server process.
 *
 * <p>Both counters are {@link AtomicLong} rather than plain {@code long} because they are written
 * from many request threads at once: {@code counter += n} on a plain field is a read, an add and
 * a write, and two threads interleaving those steps silently lose an update. {@code AtomicLong}
 * performs the whole operation as one indivisible hardware instruction. Stage 7 has a test that
 * asserts exactly this.
 *
 * <p>Nothing increments these yet -- Stage 5 wires them to real transcription responses. The
 * endpoint exists now so the full contract is answerable, reporting the honest zeros of a server
 * that has transcribed nothing.
 */
@Service
public class StatsService {

    private final AtomicLong inputTokens = new AtomicLong();
    private final AtomicLong outputTokens = new AtomicLong();

    /**
     * Builds a snapshot of the counters as of now. Each get() is atomic on its own, but the pair is not. A concurrent
     * update can land between them, resulting in an inconsistent snapshot. At worst the pair can be skewed by in-flight
     * requests, but because each counter is monotonic and independant any skew will be corrected at the next read, and
     * because TITAN does an initial and final check when there is no request happening, the total counters being
     * correct is enough. Contrasting this to the UptimeResponse, which had a third field defined as the difference
     * between the other two fields, both fields had to be derived from the same Instant because for that class the skew
     * would be visible in the difference field.
     */
    public GlobalStatsResponse currentStats() {
        return new GlobalStatsResponse(inputTokens.get(), outputTokens.get());
    }
}
