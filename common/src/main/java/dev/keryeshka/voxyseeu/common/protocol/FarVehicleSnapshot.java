package dev.keryeshka.voxyseeu.common.protocol;

import java.util.UUID;

public record FarVehicleSnapshot(
        UUID uuid,
        String entityTypeId,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) {
    public FarVehicleSnapshot {
        entityTypeId = entityTypeId == null ? "" : entityTypeId;
    }
}
