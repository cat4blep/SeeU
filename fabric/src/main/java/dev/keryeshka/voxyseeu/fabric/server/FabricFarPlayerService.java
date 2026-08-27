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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class FabricFarPlayerService {
    private static final VisibilityBridge VISIBILITY_BRIDGE = VisibilityBridge.create();

    private final FarPlayerBroadcaster broadcaster;

    public FabricFarPlayerService(SeeUServerConfig config) {
        broadcaster = new FarPlayerBroadcaster(config);
    }

    public void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((connection, server) ->
                broadcaster.remove(connection.getPlayer()));
        ServerTickEvents.END_SERVER_TICK.register(server -> broadcaster.broadcast(
                server,
                VISIBILITY_BRIDGE::canSee,
                (viewer, packet) -> ServerPlayNetworking.send(viewer, new FarPlayersPayload(packet))
        ));
    }

    public void acceptHello(ServerPlayer player, ClientHelloPacket packet) {
        broadcaster.acceptHello(player, packet);
    }

    private interface VisibilityBridge {
        boolean canSee(ServerPlayer target, ServerPlayer viewer);

        static VisibilityBridge create() {
            if (!FabricLoader.getInstance().isModLoaded("melius-vanish")) {
                return (target, viewer) -> true;
            }
            try {
                Method method = Class.forName("me.drex.vanish.api.VanishAPI")
                        .getMethod("canSeePlayer", ServerPlayer.class, ServerPlayer.class);
                return (target, viewer) -> {
                    try {
                        return Boolean.TRUE.equals(method.invoke(null, target, viewer));
                    } catch (IllegalAccessException | InvocationTargetException exception) {
                        return true;
                    }
                };
            } catch (ClassNotFoundException | NoSuchMethodException exception) {
                return (target, viewer) -> true;
            }
        }
    }
}
