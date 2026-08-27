package dev.keryeshka.seeu.extra.client;

import dev.keryeshka.seeu.extra.protocol.EntitySnapshot;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

final class TrackedExtraEntity {
    private static final long NANOS_PER_TICK = TimeUnit.MILLISECONDS.toNanos(50);
    private static final long MIN_CORRECTION_NANOS = TimeUnit.MILLISECONDS.toNanos(50);
    private static final long MAX_CORRECTION_NANOS = TimeUnit.MILLISECONDS.toNanos(100);
    private static final long MIN_EXTRAPOLATION_NANOS = TimeUnit.MILLISECONDS.toNanos(250);

    private final UUID uuid;
    private EntitySnapshot snapshot;
    private Vec3 correctionOrigin;
    private float correctionBodyYaw;
    private float correctionHeadYaw;
    private float correctionPitch;
    private long receivedNanos;
    private long correctionWindowNanos;
    private long extrapolationLimitNanos;
    private long revision;

    TrackedExtraEntity(EntitySnapshot snapshot, int updateIntervalTicks, long now) {
        this.uuid = snapshot.uuid();
        this.snapshot = snapshot;
        this.correctionOrigin = position(snapshot);
        this.correctionBodyYaw = snapshot.bodyYaw();
        this.correctionHeadYaw = snapshot.headYaw();
        this.correctionPitch = snapshot.pitch();
        this.receivedNanos = now;
        updateWindows(updateIntervalTicks);
        this.revision = 1;
    }

    UUID uuid() {
        return uuid;
    }

    void apply(EntitySnapshot next, int updateIntervalTicks, long now) {
        InterpolatedEntityState current = sample(now);
        boolean sameType = snapshot.typeId().equals(next.typeId());
        this.snapshot = next;
        this.receivedNanos = now;
        updateWindows(updateIntervalTicks);
        this.revision++;

        if (sameType) {
            this.correctionOrigin = current.position();
            this.correctionBodyYaw = current.bodyYaw();
            this.correctionHeadYaw = current.headYaw();
            this.correctionPitch = current.pitch();
        } else {
            this.correctionOrigin = position(next);
            this.correctionBodyYaw = next.bodyYaw();
            this.correctionHeadYaw = next.headYaw();
            this.correctionPitch = next.pitch();
        }
    }

    InterpolatedEntityState sample(long now) {
        long elapsed = Math.max(0, now - receivedNanos);
        long predictedNanos = Math.min(elapsed, extrapolationLimitNanos);
        double predictedTicks = (double) predictedNanos / NANOS_PER_TICK;
        Vec3 predictedPosition = position(snapshot).add(
                snapshot.velocityX() * predictedTicks,
                snapshot.velocityY() * predictedTicks,
                snapshot.velocityZ() * predictedTicks
        );
        float correctionProgress = correctionWindowNanos == 0
                ? 1.0F
                : Mth.clamp((float) elapsed / correctionWindowNanos, 0.0F, 1.0F);
        Vec3 renderedPosition = correctionOrigin.lerp(predictedPosition, correctionProgress);
        int renderedAge = addTicks(snapshot.age(), elapsed / NANOS_PER_TICK);
        return new InterpolatedEntityState(
                snapshot,
                renderedPosition,
                Mth.rotLerp(correctionProgress, correctionBodyYaw, snapshot.bodyYaw()),
                Mth.rotLerp(correctionProgress, correctionHeadYaw, snapshot.headYaw()),
                Mth.rotLerp(correctionProgress, correctionPitch, snapshot.pitch()),
                renderedAge,
                revision
        );
    }

    private void updateWindows(int updateIntervalTicks) {
        long intervalNanos = (long) updateIntervalTicks * NANOS_PER_TICK;
        correctionWindowNanos = Math.max(
                MIN_CORRECTION_NANOS,
                Math.min(intervalNanos / 2, MAX_CORRECTION_NANOS)
        );
        extrapolationLimitNanos = Math.max(MIN_EXTRAPOLATION_NANOS, intervalNanos + intervalNanos / 2);
    }

    private static Vec3 position(EntitySnapshot snapshot) {
        return new Vec3(snapshot.x(), snapshot.y(), snapshot.z());
    }

    private static int addTicks(int age, long ticks) {
        long result = age + ticks;
        if (result > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (result < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) result;
    }
}
