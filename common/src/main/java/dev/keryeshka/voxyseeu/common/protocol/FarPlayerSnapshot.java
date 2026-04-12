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
        boolean swimming
) {
}
