package dev.keryeshka.voxyseeu.fabric.client;

import dev.keryeshka.voxyseeu.common.protocol.FarItemSnapshot;
import dev.keryeshka.voxyseeu.common.protocol.FarPlayerMetadata;
import dev.keryeshka.voxyseeu.common.protocol.FarPlayerSnapshot;
import dev.keryeshka.voxyseeu.common.protocol.FarVehicleSnapshot;
import dev.keryeshka.voxyseeu.common.protocol.SnapshotInterpolationTiming;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

final class TrackedFarPlayer {
    private final UUID uuid;
    private FarPlayerMetadata metadata;
    private Vec3 fromPosition;
    private Vec3 toPosition;
    private long snapshotNanos;
    private long interpolationWindowNanos;
    private float fromBodyYaw;
    private float toBodyYaw;
    private float fromHeadYaw;
    private float toHeadYaw;
    private float fromPitch;
    private float toPitch;
    private boolean sneaking;
    private boolean gliding;
    private boolean swimming;
    private UUID vehicleUuid;
    private String vehicleTypeId;
    private Vec3 fromVehiclePosition;
    private Vec3 toVehiclePosition;
    private float fromVehicleYaw;
    private float toVehicleYaw;
    private float fromVehiclePitch;
    private float toVehiclePitch;
    private int generation;

    TrackedFarPlayer(
            FarPlayerSnapshot snapshot,
            int generation,
            long nowNanos,
            long interpolationWindowNanos
    ) {
        this.uuid = snapshot.uuid();
        this.metadata = snapshot.metadata();
        this.fromPosition = new Vec3(snapshot.x(), snapshot.y(), snapshot.z());
        this.toPosition = this.fromPosition;
        this.snapshotNanos = nowNanos;
        this.interpolationWindowNanos = interpolationWindowNanos;
        this.fromBodyYaw = snapshot.bodyYaw();
        this.toBodyYaw = snapshot.bodyYaw();
        this.fromHeadYaw = snapshot.headYaw();
        this.toHeadYaw = snapshot.headYaw();
        this.fromPitch = snapshot.pitch();
        this.toPitch = snapshot.pitch();
        this.sneaking = snapshot.sneaking();
        this.gliding = snapshot.gliding();
        this.swimming = snapshot.swimming();
        applyVehicleSnapshot(snapshot.vehicle(), false, nowNanos);
        this.generation = generation;
    }

    UUID uuid() {
        return uuid;
    }

    FarPlayerMetadata metadata() {
        return metadata;
    }

    String name() {
        return metadata.name();
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
        return metadata.mainHand();
    }

    FarItemSnapshot offHand() {
        return metadata.offHand();
    }

    FarItemSnapshot feet() {
        return metadata.feet();
    }

    FarItemSnapshot legs() {
        return metadata.legs();
    }

    FarItemSnapshot chest() {
        return metadata.chest();
    }

    FarItemSnapshot head() {
        return metadata.head();
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

    Vec3 renderVehiclePosition(long nowNanos) {
        if (toVehiclePosition == null) {
            return Vec3.ZERO;
        }
        return fromVehiclePosition == null
                ? toVehiclePosition
                : fromVehiclePosition.lerp(toVehiclePosition, progress(nowNanos));
    }

    float renderVehicleYaw(long nowNanos) {
        return Mth.rotLerp(progress(nowNanos), fromVehicleYaw, toVehicleYaw);
    }

    float renderVehiclePitch(long nowNanos) {
        return Mth.lerp(progress(nowNanos), fromVehiclePitch, toVehiclePitch);
    }

    int generation() {
        return generation;
    }

    void apply(
            FarPlayerSnapshot snapshot,
            int generation,
            long nowNanos,
            long nextInterpolationWindowNanos
    ) {
        Vec3 nextPosition = new Vec3(snapshot.x(), snapshot.y(), snapshot.z());
        boolean teleport = SnapshotInterpolationTiming.shouldSnap(
                toPosition.x,
                toPosition.y,
                toPosition.z,
                nextPosition.x,
                nextPosition.y,
                nextPosition.z
        );
        if (teleport) {
            this.fromPosition = nextPosition;
            this.fromBodyYaw = snapshot.bodyYaw();
            this.fromHeadYaw = snapshot.headYaw();
            this.fromPitch = snapshot.pitch();
        } else {
            this.fromPosition = renderPosition(nowNanos);
            this.fromBodyYaw = renderBodyYaw(nowNanos);
            this.fromHeadYaw = renderHeadYaw(nowNanos);
            this.fromPitch = renderPitch(nowNanos);
        }
        this.toPosition = nextPosition;
        this.toBodyYaw = snapshot.bodyYaw();
        this.toHeadYaw = snapshot.headYaw();
        this.toPitch = snapshot.pitch();
        this.sneaking = snapshot.sneaking();
        this.gliding = snapshot.gliding();
        this.swimming = snapshot.swimming();
        if (snapshot.metadata() != null) {
            this.metadata = snapshot.metadata();
        }
        applyVehicleSnapshot(snapshot.vehicle(), true, nowNanos);
        this.snapshotNanos = nowNanos;
        this.interpolationWindowNanos = nextInterpolationWindowNanos;
        this.generation = generation;
    }

    Vec3 renderPosition(long nowNanos) {
        return fromPosition.lerp(toPosition, progress(nowNanos));
    }

    float renderBodyYaw(long nowNanos) {
        return Mth.rotLerp(progress(nowNanos), fromBodyYaw, toBodyYaw);
    }

    float renderHeadYaw(long nowNanos) {
        return Mth.rotLerp(progress(nowNanos), fromHeadYaw, toHeadYaw);
    }

    float renderPitch(long nowNanos) {
        return Mth.lerp(progress(nowNanos), fromPitch, toPitch);
    }

    private float progress(long nowNanos) {
        return SnapshotInterpolationTiming.progress(
                nowNanos,
                snapshotNanos,
                interpolationWindowNanos
        );
    }

    private void applyVehicleSnapshot(
            FarVehicleSnapshot vehicle,
            boolean interpolateFromCurrent,
            long nowNanos
    ) {
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

        Vec3 nextPosition = new Vec3(vehicle.x(), vehicle.y(), vehicle.z());
        boolean sameVehicle = vehicle.uuid().equals(this.vehicleUuid)
                && vehicle.entityTypeId().equals(this.vehicleTypeId);
        boolean teleport = this.toVehiclePosition != null && SnapshotInterpolationTiming.shouldSnap(
                this.toVehiclePosition.x,
                this.toVehiclePosition.y,
                this.toVehiclePosition.z,
                nextPosition.x,
                nextPosition.y,
                nextPosition.z
        );
        if (interpolateFromCurrent && sameVehicle && this.toVehiclePosition != null && !teleport) {
            this.fromVehiclePosition = renderVehiclePosition(nowNanos);
            this.fromVehicleYaw = renderVehicleYaw(nowNanos);
            this.fromVehiclePitch = renderVehiclePitch(nowNanos);
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
