package dev.keryeshka.seeu.extra.client;

import dev.keryeshka.seeu.extra.protocol.EntitySnapshot;
import net.minecraft.world.phys.Vec3;

public record InterpolatedEntityState(
        EntitySnapshot snapshot,
        Vec3 position,
        float bodyYaw,
        float headYaw,
        float pitch,
        int age,
        long revision
) {
}
