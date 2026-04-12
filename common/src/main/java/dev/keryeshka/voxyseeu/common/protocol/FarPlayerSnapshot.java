package dev.keryeshka.voxyseeu.common.protocol;

import java.util.UUID;

public record FarPlayerSnapshot(
        UUID uuid,
        String name,
        double x,
        double y,
        double z,
        float bodyYaw,
        float headYaw,
        float pitch,
        boolean sneaking,
        boolean gliding,
        boolean swimming,
        FarItemSnapshot mainHand,
        FarItemSnapshot offHand,
        FarItemSnapshot feet,
        FarItemSnapshot legs,
        FarItemSnapshot chest,
        FarItemSnapshot head,
        FarVehicleSnapshot vehicle
) {
    public FarPlayerSnapshot {
        mainHand = sanitize(mainHand);
        offHand = sanitize(offHand);
        feet = sanitize(feet);
        legs = sanitize(legs);
        chest = sanitize(chest);
        head = sanitize(head);
    }

    private static FarItemSnapshot sanitize(FarItemSnapshot snapshot) {
        return snapshot == null ? FarItemSnapshot.EMPTY : snapshot;
    }
}
