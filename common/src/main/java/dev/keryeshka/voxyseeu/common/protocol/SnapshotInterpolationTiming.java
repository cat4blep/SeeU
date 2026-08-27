package dev.keryeshka.voxyseeu.common.protocol;

import java.util.concurrent.TimeUnit;

public final class SnapshotInterpolationTiming {
    public static final long TICK_NANOS = TimeUnit.MILLISECONDS.toNanos(50L);
    public static final long TELEPORT_SNAP_DISTANCE_SQUARED = 32L * 32L;

    private static final long MAX_ADAPTIVE_WINDOW_NANOS = TimeUnit.SECONDS.toNanos(5L);
    private static final int DISCONTINUITY_INTERVAL_MULTIPLIER = 4;
    private static final int EWMA_OLD_WEIGHT = 3;
    private static final int EWMA_TOTAL_WEIGHT = 4;

    private long previousArrivalNanos = Long.MIN_VALUE;
    private long arrivalIntervalEwmaNanos = -1L;
    private long arrivalJitterEwmaNanos;

    public void reset() {
        previousArrivalNanos = Long.MIN_VALUE;
        arrivalIntervalEwmaNanos = -1L;
        arrivalJitterEwmaNanos = 0L;
    }

    public long recordArrival(long nowNanos, int expectedIntervalTicks) {
        long expectedIntervalNanos = Math.max(1L, expectedIntervalTicks) * TICK_NANOS;
        long baselineWindowNanos = expectedIntervalNanos + TICK_NANOS;
        if (previousArrivalNanos != Long.MIN_VALUE && nowNanos > previousArrivalNanos) {
            long observedIntervalNanos = nowNanos - previousArrivalNanos;
            if (observedIntervalNanos > saturatingMultiply(
                    expectedIntervalNanos,
                    DISCONTINUITY_INTERVAL_MULTIPLIER
            )) {
                previousArrivalNanos = nowNanos;
                arrivalIntervalEwmaNanos = -1L;
                arrivalJitterEwmaNanos = 0L;
                return baselineWindowNanos;
            }
            if (arrivalIntervalEwmaNanos < 0L) {
                arrivalIntervalEwmaNanos = observedIntervalNanos;
                arrivalJitterEwmaNanos = 0L;
            } else {
                long priorEwmaNanos = arrivalIntervalEwmaNanos;
                arrivalIntervalEwmaNanos = weightedAverage(priorEwmaNanos, observedIntervalNanos);
                arrivalJitterEwmaNanos = weightedAverage(
                        arrivalJitterEwmaNanos,
                        Math.abs(observedIntervalNanos - priorEwmaNanos)
                );
            }
        }
        previousArrivalNanos = nowNanos;
        if (arrivalIntervalEwmaNanos < 0L) {
            return baselineWindowNanos;
        }
        long adaptiveWindowNanos = saturatingAdd(
                saturatingAdd(arrivalIntervalEwmaNanos, TICK_NANOS),
                saturatingMultiply(arrivalJitterEwmaNanos, 2L)
        );
        return Math.max(
                baselineWindowNanos,
                Math.min(adaptiveWindowNanos, Math.max(baselineWindowNanos, MAX_ADAPTIVE_WINDOW_NANOS))
        );
    }

    public static float progress(long nowNanos, long snapshotNanos, long interpolationWindowNanos) {
        if (nowNanos <= snapshotNanos) {
            return 0.0F;
        }
        if (interpolationWindowNanos <= 0L || nowNanos - snapshotNanos >= interpolationWindowNanos) {
            return 1.0F;
        }
        return (float) (nowNanos - snapshotNanos) / (float) interpolationWindowNanos;
    }

    public static boolean shouldSnap(
            double previousX,
            double previousY,
            double previousZ,
            double nextX,
            double nextY,
            double nextZ
    ) {
        double deltaX = nextX - previousX;
        double deltaY = nextY - previousY;
        double deltaZ = nextZ - previousZ;
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ
                >= TELEPORT_SNAP_DISTANCE_SQUARED;
    }

    private static long weightedAverage(long previous, long sample) {
        return previous / EWMA_TOTAL_WEIGHT * EWMA_OLD_WEIGHT
                + sample / EWMA_TOTAL_WEIGHT
                + (previous % EWMA_TOTAL_WEIGHT * EWMA_OLD_WEIGHT + sample % EWMA_TOTAL_WEIGHT)
                / EWMA_TOTAL_WEIGHT;
    }

    private static long saturatingAdd(long left, long right) {
        if (Long.MAX_VALUE - left < right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static long saturatingMultiply(long value, long factor) {
        if (value > Long.MAX_VALUE / factor) {
            return Long.MAX_VALUE;
        }
        return value * factor;
    }
}
