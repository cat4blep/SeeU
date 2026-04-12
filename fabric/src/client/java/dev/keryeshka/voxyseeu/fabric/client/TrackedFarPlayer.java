package dev.keryeshka.voxyseeu.fabric.client;

import dev.keryeshka.voxyseeu.common.protocol.FarItemSnapshot;
import dev.keryeshka.voxyseeu.common.protocol.FarPlayerSnapshot;
import dev.keryeshka.voxyseeu.common.protocol.FarVehicleSnapshot;
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
    private FarItemSnapshot mainHand;
    private FarItemSnapshot offHand;
    private FarItemSnapshot feet;
    private FarItemSnapshot legs;
    private FarItemSnapshot chest;
    private FarItemSnapshot head;
    private UUID vehicleUuid;
    private String vehicleTypeId;
    private Vec3 fromVehiclePosition;
    private Vec3 toVehiclePosition;
    private float fromVehicleYaw;
    private float toVehicleYaw;
    private float fromVehiclePitch;
    private float toVehiclePitch;
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
        this.mainHand = snapshot.mainHand();
        this.offHand = snapshot.offHand();
        this.feet = snapshot.feet();
        this.legs = snapshot.legs();
        this.chest = snapshot.chest();
        this.head = snapshot.head();
        applyVehicleSnapshot(snapshot.vehicle(), false);
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

    FarItemSnapshot mainHand() {
        return mainHand;
    }

    FarItemSnapshot offHand() {
        return offHand;
    }

    FarItemSnapshot feet() {
        return feet;
    }

    FarItemSnapshot legs() {
        return legs;
    }

    FarItemSnapshot chest() {
        return chest;
    }

    FarItemSnapshot head() {
        return head;
    }

    boolean hasVehicle() {
        return vehicleUuid != null && vehicleTypeId != null && toVehiclePosition != null;
    }

    UUID vehicleUuid() {
        return vehicleUuid;
    }

    String vehicleTypeId() {
        return vehicleTypeId;
    }

    Vec3 renderVehiclePosition(long now) {
        if (toVehiclePosition == null) {
            return Vec3.ZERO;
        }
        return fromVehiclePosition == null ? toVehiclePosition : fromVehiclePosition.lerp(toVehiclePosition, progress(now));
    }

    float renderVehicleYaw(long now) {
        return Mth.rotLerp(progress(now), fromVehicleYaw, toVehicleYaw);
    }

    float renderVehiclePitch(long now) {
        return Mth.lerp(progress(now), fromVehiclePitch, toVehiclePitch);
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
        this.mainHand = snapshot.mainHand();
        this.offHand = snapshot.offHand();
        this.feet = snapshot.feet();
        this.legs = snapshot.legs();
        this.chest = snapshot.chest();
        this.head = snapshot.head();
        applyVehicleSnapshot(snapshot.vehicle(), true);
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

    private void applyVehicleSnapshot(FarVehicleSnapshot vehicle, boolean interpolateFromCurrent) {
        if (vehicle == null) {
            this.vehicleUuid = null;
            this.vehicleTypeId = null;
            this.fromVehiclePosition = null;
            this.toVehiclePosition = null;
            this.fromVehicleYaw = 0.0F;
            this.toVehicleYaw = 0.0F;
            this.fromVehiclePitch = 0.0F;
            this.toVehiclePitch = 0.0F;
            return;
        }

        long now = System.nanoTime();
        Vec3 nextPosition = new Vec3(vehicle.x(), vehicle.y(), vehicle.z());
        boolean sameVehicle = vehicle.uuid().equals(this.vehicleUuid) && vehicle.entityTypeId().equals(this.vehicleTypeId);
        if (interpolateFromCurrent && sameVehicle && this.toVehiclePosition != null) {
            this.fromVehiclePosition = renderVehiclePosition(now);
            this.fromVehicleYaw = renderVehicleYaw(now);
            this.fromVehiclePitch = renderVehiclePitch(now);
        } else {
            this.fromVehiclePosition = nextPosition;
            this.fromVehicleYaw = vehicle.yaw();
            this.fromVehiclePitch = vehicle.pitch();
        }

        this.vehicleUuid = vehicle.uuid();
        this.vehicleTypeId = vehicle.entityTypeId();
        this.toVehiclePosition = nextPosition;
        this.toVehicleYaw = vehicle.yaw();
        this.toVehiclePitch = vehicle.pitch();
    }
}
