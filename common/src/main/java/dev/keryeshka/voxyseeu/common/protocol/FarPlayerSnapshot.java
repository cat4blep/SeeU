package dev.keryeshka.voxyseeu.common.protocol;

import java.util.UUID;

public record FarPlayerSnapshot(
        UUID uuid,
        double x,
        double y,
        double z,
        float bodyYaw,
        float headYaw,
        float pitch,
        boolean sneaking,
        boolean gliding,
        boolean swimming,
        FarVehicleSnapshot vehicle,
        FarPlayerMetadata metadata
) {
    public FarPlayerSnapshot withoutMetadata() {
        return metadata == null ? this : new FarPlayerSnapshot(
                uuid,
                x,
                y,
                z,
                bodyYaw,
                headYaw,
                pitch,
                sneaking,
                gliding,
                swimming,
                vehicle,
                null
        );
    }
}
