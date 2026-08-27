package dev.keryeshka.voxyseeu.common.protocol;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotInterpolationTimingTest {
    @Test
    void stableTwoTickFramesUseAOneHundredFiftyMillisecondWindow() {
        SnapshotInterpolationTiming timing = new SnapshotInterpolationTiming();
        long start = TimeUnit.SECONDS.toNanos(1L);
        long hundredMilliseconds = TimeUnit.MILLISECONDS.toNanos(100L);
        long expectedWindow = TimeUnit.MILLISECONDS.toNanos(150L);

        assertEquals(expectedWindow, timing.recordArrival(start, 2));
        assertEquals(expectedWindow, timing.recordArrival(start + hundredMilliseconds, 2));
        assertEquals(
                0.5F,
                SnapshotInterpolationTiming.progress(
                        start + hundredMilliseconds + TimeUnit.MILLISECONDS.toNanos(75L),
                        start + hundredMilliseconds,
                        expectedWindow
                ),
                0.0001F
        );
        assertEquals(
                1.0F,
                SnapshotInterpolationTiming.progress(
                        start + hundredMilliseconds + expectedWindow,
                        start + hundredMilliseconds,
                        expectedWindow
                )
        );
    }

    @Test
    void lowTpsAndJitterExpandInsteadOfShorteningTheWindow() {
        SnapshotInterpolationTiming timing = new SnapshotInterpolationTiming();
        long now = TimeUnit.SECONDS.toNanos(1L);
        long baseline = timing.recordArrival(now, 2);
        now += TimeUnit.MILLISECONDS.toNanos(200L);
        long lowTpsWindow = timing.recordArrival(now, 2);
        assertTrue(lowTpsWindow > baseline);

        now += TimeUnit.MILLISECONDS.toNanos(50L);
        long jitterWindow = timing.recordArrival(now, 2);
        assertTrue(jitterWindow >= baseline);
    }

    @Test
    void longArrivalGapResetsAdaptiveDelay() {
        SnapshotInterpolationTiming timing = new SnapshotInterpolationTiming();
        long now = TimeUnit.SECONDS.toNanos(1L);
        long baseline = timing.recordArrival(now, 2);

        now += TimeUnit.MILLISECONDS.toNanos(200L);
        assertTrue(timing.recordArrival(now, 2) > baseline);

        now += TimeUnit.SECONDS.toNanos(5L);
        assertEquals(baseline, timing.recordArrival(now, 2));
        now += TimeUnit.MILLISECONDS.toNanos(100L);
        assertEquals(baseline, timing.recordArrival(now, 2));
    }

    @Test
    void snapsAtThirtyTwoBlocksAndRejectsStaleSequences() {
        assertFalse(SnapshotInterpolationTiming.shouldSnap(0.0D, 0.0D, 0.0D, 31.99D, 0.0D, 0.0D));
        assertTrue(SnapshotInterpolationTiming.shouldSnap(0.0D, 0.0D, 0.0D, 32.0D, 0.0D, 0.0D));

        PacketSequenceGate gate = new PacketSequenceGate();
        assertTrue(gate.accept(1L));
        assertFalse(gate.accept(1L));
        assertFalse(gate.accept(0L));
        assertTrue(gate.accept(2L));
        gate.reset();
        assertTrue(gate.accept(1L));
    }
}
