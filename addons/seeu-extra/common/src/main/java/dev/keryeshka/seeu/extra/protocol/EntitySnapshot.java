package dev.keryeshka.seeu.extra.protocol;

import dev.keryeshka.seeu.extra.ResourceIdentifier;

import java.util.Objects;
import java.util.UUID;

public record EntitySnapshot(
        UUID uuid,
        String typeId,
        double x,
        double y,
        double z,
        float bodyYaw,
        float headYaw,
        float pitch,
        double velocityX,
        double velocityY,
        double velocityZ,
        int age,
        String pose,
        int flags,
        EquipmentSnapshot mainHand,
        EquipmentSnapshot offHand,
        EquipmentSnapshot feet,
        EquipmentSnapshot legs,
        EquipmentSnapshot chest,
        EquipmentSnapshot head
) {
    public EntitySnapshot {
        Objects.requireNonNull(uuid, "uuid");
        if (!ResourceIdentifier.isValid(typeId)) {
            throw new IllegalArgumentException("Invalid entity type identifier: " + typeId);
        }
        requireBounded(x, ExtraProtocol.MAX_ABSOLUTE_COORDINATE, "x");
        requireBounded(y, ExtraProtocol.MAX_ABSOLUTE_COORDINATE, "y");
        requireBounded(z, ExtraProtocol.MAX_ABSOLUTE_COORDINATE, "z");
        requireFinite(bodyYaw, "body yaw");
        requireFinite(headYaw, "head yaw");
        requireFinite(pitch, "pitch");
        requireBounded(velocityX, ExtraProtocol.MAX_ABSOLUTE_VELOCITY, "velocity x");
        requireBounded(velocityY, ExtraProtocol.MAX_ABSOLUTE_VELOCITY, "velocity y");
        requireBounded(velocityZ, ExtraProtocol.MAX_ABSOLUTE_VELOCITY, "velocity z");
        if (!isValidPose(pose)) {
            throw new IllegalArgumentException("Invalid pose name: " + pose);
        }
        EntityFlags.requireValid(flags);
        Objects.requireNonNull(mainHand, "mainHand");
        Objects.requireNonNull(offHand, "offHand");
        Objects.requireNonNull(feet, "feet");
        Objects.requireNonNull(legs, "legs");
        Objects.requireNonNull(chest, "chest");
        Objects.requireNonNull(head, "head");
    }

    private static boolean isValidPose(String pose) {
        if (pose == null || pose.isEmpty()) {
            return false;
        }
        for (int index = 0; index < pose.length(); index++) {
            char character = pose.charAt(index);
            if ((character < 'A' || character > 'Z') && (character < '0' || character > '9') && character != '_') {
                return false;
            }
        }
        return true;
    }

    private static void requireFinite(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Entity " + field + " must be finite");
        }
    }

    private static void requireBounded(double value, double maximumMagnitude, String field) {
        requireFinite(value, field);
        if (Math.abs(value) > maximumMagnitude) {
            throw new IllegalArgumentException("Entity " + field + " exceeds the supported magnitude");
        }
    }

    private static void requireFinite(float value, String field) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("Entity " + field + " must be finite");
        }
    }
}
