package dev.keryeshka.voxyseeu.neoforge.server;

import dev.keryeshka.voxyseeu.common.protocol.ClientHelloPacket;
import dev.keryeshka.voxyseeu.common.server.FarPlayerBroadcaster;
import dev.keryeshka.voxyseeu.common.server.SeeUServerConfig;
import dev.keryeshka.voxyseeu.neoforge.network.FarPlayersPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class NeoForgeFarPlayerService {
    private final FarPlayerBroadcaster broadcaster;

    public NeoForgeFarPlayerService(SeeUServerConfig config) {
        broadcaster = new FarPlayerBroadcaster(config);
    }

    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        broadcaster.remove((ServerPlayer) event.getEntity());
    }

    public void onServerTick(ServerTickEvent.Post event) {
        broadcaster.broadcast(
                event.getServer(),
                (target, viewer) -> true,
                (viewer, packet) -> PacketDistributor.sendToPlayer(viewer, new FarPlayersPayload(packet))
        );
    }

    public void acceptHello(ServerPlayer player, ClientHelloPacket packet) {
        broadcaster.acceptHello(player, packet);
    }
}
