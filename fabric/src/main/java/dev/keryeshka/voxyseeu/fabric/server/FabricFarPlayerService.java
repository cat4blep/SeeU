package dev.keryeshka.voxyseeu.fabric.server;

import dev.keryeshka.voxyseeu.common.protocol.ClientHelloPacket;
import dev.keryeshka.voxyseeu.common.server.FarPlayerBroadcaster;
import dev.keryeshka.voxyseeu.common.server.SeeUServerConfig;
import dev.keryeshka.voxyseeu.fabric.network.FarPlayersPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;

public final class FabricFarPlayerService {
    private static final boolean VANISH_LOADED = FabricLoader.getInstance().isModLoaded("melius-vanish");
    private static final Method VANISH_CAN_SEE = findVanishCanSee();

    private final FarPlayerBroadcaster broadcaster;

    public FabricFarPlayerService(SeeUServerConfig config) {
        broadcaster = new FarPlayerBroadcaster(config);
    }

    public void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((connection, server) ->
                broadcaster.remove(connection.getPlayer()));
        ServerTickEvents.END_SERVER_TICK.register(server -> broadcaster.broadcast(
                server,
                FabricFarPlayerService::canSeePlayer,
                (viewer, packet) -> ServerPlayNetworking.send(viewer, new FarPlayersPayload(packet))
        ));
    }

    public void acceptHello(ServerPlayer player, ClientHelloPacket packet) {
        broadcaster.acceptHello(player, packet);
    }

    private static boolean canSeePlayer(ServerPlayer target, ServerPlayer viewer) {
        if (!VANISH_LOADED) {
            return true;
        }
        if (VANISH_CAN_SEE == null) {
            return false;
        }
        try {
            return (boolean) VANISH_CAN_SEE.invoke(null, target, viewer);
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static Method findVanishCanSee() {
        if (!VANISH_LOADED) {
            return null;
        }
        try {
            return Class.forName("me.drex.vanish.api.VanishAPI")
                    .getMethod("canSeePlayer", ServerPlayer.class, ServerPlayer.class);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
