package edu.adelaide.assignment1speechtotext.service;

import static org.assertj.core.api.Assertions.assertThat;

import edu.adelaide.assignment1speechtotext.dto.GlobalStatsResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;

/**
 * Race-condition regression test for the shared statistics counters.
 *
 * <p><b>Why this test exists.</b> {@link StatsService} holds the only mutable state in the
 * application that is shared across request threads. Every successful transcription adds to the
 * same two counters, and with virtual threads enabled there can be hundreds of those in flight at
 * once. The rubric's concurrency criterion requires a regression test that surfaces race
 * conditions; this is it.
 *
 * <p><b>What it proves.</b> That {@code recordUsage} is atomic. The naive implementation
 * {@code inputTokens += n} compiles to three separate steps -- read the field, add, write it back
 * -- and two threads interleaving those steps lose one of the updates entirely, with no error and
 * no exception. The counters simply end up too low. This test drives the method from many threads
 * simultaneously and asserts the totals are exactly right, so it fails if the implementation is
 * ever rewritten as {@code +=}.
 *
 * <p><b>Expected result.</b> Every repetition passes with the exact arithmetic total. A correct
 * implementation using {@link java.util.concurrent.atomic.AtomicLong#addAndGet} passes 100% of
 * runs, because atomicity is a hardware guarantee rather than a probability -- there are no flaky
 * failures to tolerate here. A broken implementation fails most, but not all, runs: losing an
 * update requires two threads to interleave inside a window a few instructions wide.
 *
 * <p><b>Why a barrier, and why repeated.</b> Simply starting N threads is not enough -- the first
 * may finish before the last is created, so the calls never actually overlap and the bug stays
 * hidden. A {@link CyclicBarrier} holds every thread until all of them have arrived, then releases
 * them together, which forces genuine contention. {@link RepeatedTest} then samples that
 * interleaving many times: one barrier-synchronised run leaves a small chance that a real bug
 * happens not to manifest, and repetition shrinks that to negligible. It is not a retry for
 * flakiness -- a correct implementation never fails a single repetition.
 */
class StatsServiceRaceTest {

    /**
     * Threads contending on the counters. Comfortably more than the machine has cores, so the OS is
     * forced to interleave them rather than running each to completion on a core of its own.
     */
    private static final int THREAD_COUNT = 64;

    /**
     * Calls each thread makes. Enough that a lost update is near-certain if the code is broken, few
     * enough that the whole test stays fast.
     */
    private static final int CALLS_PER_THREAD = 1_000;

    /**
     * Token counts per call. Deliberately different from each other so that a bug which crosses the
     * two counters -- adding the input tokens to the output total -- shows up as a wrong number
     * rather than an accidentally correct one.
     */
    private static final long INPUT_TOKENS_PER_CALL = 7L;

    private static final long OUTPUT_TOKENS_PER_CALL = 3L;

    @RepeatedTest(value = 50, name = "repetition {currentRepetition} of {totalRepetitions}")
    @DisplayName("concurrent recordUsage calls produce exact totals with no lost updates")
    void concurrentRecordUsageLosesNoUpdates() throws Exception {
        // A fresh service per repetition: the counters accumulate for the life of the instance, so
        // a shared one would make every repetition after the first assert against a moving target.
        StatsService statsService = new StatsService();

        final CyclicBarrier barrier = new CyclicBarrier(THREAD_COUNT);

        // A fixed pool of platform threads, not virtual ones. This test is about contention on a
        // shared field, and platform threads map to real OS threads that can run on different cores
        // genuinely simultaneously. Virtual threads would multiplex onto fewer carrier threads and
        // reduce the true parallelism this test depends on.
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        try {
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < THREAD_COUNT; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        barrier.await();
                        for (int j = 0; j < CALLS_PER_THREAD; j++) {
                            statsService.recordUsage(INPUT_TOKENS_PER_CALL, OUTPUT_TOKENS_PER_CALL);
                        }
                    } catch (InterruptedException | BrokenBarrierException e) {
                        throw new RuntimeException(e);
                    }
                }));
            }

            for (Future<?> future : futures) {
                try {
                    future.get(10, TimeUnit.SECONDS);
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    throw new RuntimeException(e);
                }
            }

            long expectedInput = (long) THREAD_COUNT * CALLS_PER_THREAD * INPUT_TOKENS_PER_CALL;
            long expectedOutput = (long) THREAD_COUNT * CALLS_PER_THREAD * OUTPUT_TOKENS_PER_CALL;

            GlobalStatsResponse stats = statsService.currentStats();

            // isEqualTo, not isGreaterThan or a tolerance. A lost update makes the total too low by
            // some unpredictable amount, so exact equality is the only assertion that catches it
            // reliably. Every thread has completed by now, so no in-flight update can skew this.
            assertThat(stats.inputTokens())
                    .as("input tokens after %d threads x %d calls", THREAD_COUNT, CALLS_PER_THREAD)
                    .isEqualTo(expectedInput);
            assertThat(stats.outputTokens())
                    .as("output tokens after %d threads x %d calls", THREAD_COUNT, CALLS_PER_THREAD)
                    .isEqualTo(expectedOutput);
        } finally {
            // shutdownNow in a finally block: if an assertion above fails, the pool's threads are
            // still non-daemon and would keep the JVM alive after the test run had finished.
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
