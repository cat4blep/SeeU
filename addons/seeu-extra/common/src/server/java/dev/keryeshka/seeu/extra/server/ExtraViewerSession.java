package dev.keryeshka.seeu.extra.server;

import dev.keryeshka.seeu.extra.protocol.ClientOffer;
import dev.keryeshka.seeu.extra.protocol.EntitySnapshotPacket;
import net.minecraft.server.level.ServerPlayer;

public interface ExtraViewerSession {
    ServerPlayer player();

    ClientOffer offer();

    void send(EntitySnapshotPacket packet);
}
