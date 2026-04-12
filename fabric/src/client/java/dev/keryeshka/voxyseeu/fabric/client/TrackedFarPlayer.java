package dev.keryeshka.voxyseeu.fabric.client;

import dev.keryeshka.voxyseeu.common.protocol.FarPlayerSnapshot;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

final class TrackedFarPlayer {
    private static final long INTERPOLATION_WINDOW_NANOS = TimeUnit.MILLISECONDS.toNanos(550L);

    private final UUID uuid;
    private String name;
    private Vec3 fromPosition;
    private Vec3 toPosition;
    private long snapshotNanos;
    private float fromBodyYaw;
    private float toBodyYaw;
    private float fromHeadYaw;
    private float toHeadYaw;
    private float fromPitch;
    private float toPitch;
    private boolean sneaking;
    private boolean gliding;
    private boolean swimming;
    private int generation;

    TrackedFarPlayer(FarPlayerSnapshot snapshot, int generation) {
        this.uuid = snapshot.uuid();
        this.name = snapshot.name();
        this.fromPosition = new Vec3(snapshot.x(), snapshot.y(), snapshot.z());
        this.toPosition = this.fromPosition;
        this.snapshotNanos = System.nanoTime();
        this.fromBodyYaw = snapshot.bodyYaw();
        this.toBodyYaw = snapshot.bodyYaw();
        this.fromHeadYaw = snapshot.headYaw();
        this.toHeadYaw = snapshot.headYaw();
        this.fromPitch = snapshot.pitch();
        this.toPitch = snapshot.pitch();
        this.sneaking = snapshot.sneaking();
        this.gliding = snapshot.gliding();
        this.swimming = snapshot.swimming();
        this.generation = generation;
    }

    UUID uuid() {
        return uuid;
    }

    String name() {
        return name;
    }

    boolean sneaking() {
        return sneaking;
    }

    boolean gliding() {
        return gliding;
    }

    boolean swimming() {
        return swimming;
    }

    int generation() {
        return generation;
    }

    void apply(FarPlayerSnapshot snapshot, int generation) {
        long now = System.nanoTime();
        this.fromPosition = renderPosition(now);
        this.toPosition = new Vec3(snapshot.x(), snapshot.y(), snapshot.z());
        this.name = snapshot.name();
        this.fromBodyYaw = renderBodyYaw(now);
        this.toBodyYaw = snapshot.bodyYaw();
        this.fromHeadYaw = renderHeadYaw(now);
        this.toHeadYaw = snapshot.headYaw();
        this.fromPitch = renderPitch(now);
        this.toPitch = snapshot.pitch();
        this.sneaking = snapshot.sneaking();
        this.gliding = snapshot.gliding();
        this.swimming = snapshot.swimming();
        this.snapshotNanos = now;
        this.generation = generation;
    }

    Vec3 renderPosition(long now) {
        return fromPosition.lerp(toPosition, progress(now));
    }

    float renderBodyYaw(long now) {
        return Mth.rotLerp(progress(now), fromBodyYaw, toBodyYaw);
    }

    float renderHeadYaw(long now) {
        return Mth.rotLerp(progress(now), fromHeadYaw, toHeadYaw);
    }

    float renderPitch(long now) {
        return Mth.lerp(progress(now), fromPitch, toPitch);
    }

    private float progress(long now) {
        return Mth.clamp((float) (now - snapshotNanos) / (float) INTERPOLATION_WINDOW_NANOS, 0.0F, 1.0F);
    }
}
