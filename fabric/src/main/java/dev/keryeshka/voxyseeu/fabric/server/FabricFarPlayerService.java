package dev.keryeshka.voxyseeu.fabric.server;

import dev.keryeshka.voxyseeu.common.protocol.ClientHelloPacket;
import dev.keryeshka.voxyseeu.common.server.FarPlayerBroadcaster;
import dev.keryeshka.voxyseeu.common.server.SeeUServerConfig;
import dev.keryeshka.voxyseeu.fabric.network.FarPlayersPayload;
import me.drex.vanish.api.VanishAPI;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

public final class FabricFarPlayerService {
    private static final boolean VANISH_LOADED = FabricLoader.getInstance().isModLoaded("melius-vanish");

    private final FarPlayerBroadcaster broadcaster;

    public FabricFarPlayerService(SeeUServerConfig config) {
        broadcaster = new FarPlayerBroadcaster(config);
    }

    public void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((connection, server) ->
                broadcaster.remove(connection.getPlayer()));
        ServerTickEvents.END_SERVER_TICK.register(server -> broadcaster.broadcast(
                server,
                (target, viewer) -> !VANISH_LOADED || VanishAPI.canSeePlayer(target, viewer),
                (viewer, packet) -> ServerPlayNetworking.send(viewer, new FarPlayersPayload(packet))
        ));
    }

    public void acceptHello(ServerPlayer player, ClientHelloPacket packet) {
        broadcaster.acceptHello(player, packet);
    }
}
