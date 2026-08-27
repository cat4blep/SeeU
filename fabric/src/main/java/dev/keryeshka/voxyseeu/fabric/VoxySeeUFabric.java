package dev.keryeshka.voxyseeu.fabric;

import dev.keryeshka.voxyseeu.api.addon.AddonTransport;
import dev.keryeshka.voxyseeu.api.addon.SeeUServerAddons;
import dev.keryeshka.voxyseeu.api.addon.protocol.AddonControlMessage;
import dev.keryeshka.voxyseeu.api.addon.protocol.AddonEnvelope;
import dev.keryeshka.voxyseeu.fabric.config.VoxySeeUServerConfig;
import dev.keryeshka.voxyseeu.fabric.network.AddonControlPayload;
import dev.keryeshka.voxyseeu.fabric.network.AddonDataPayload;
import dev.keryeshka.voxyseeu.fabric.network.ClientHelloPayload;
import dev.keryeshka.voxyseeu.fabric.network.FabricPayloads;
import dev.keryeshka.voxyseeu.fabric.server.FabricFarPlayerService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class VoxySeeUFabric implements ModInitializer {
    private final SeeUServerAddons serverAddons = SeeUServerAddons.getInstance();

    @Override
    public void onInitialize() {
        FabricPayloads.register();

        FabricFarPlayerService service = new FabricFarPlayerService(VoxySeeUServerConfig.load());
        service.register();

        ServerPlayNetworking.registerGlobalReceiver(ClientHelloPayload.TYPE, (payload, context) ->
                service.handleHello(context.player(), payload.packet()));
        ServerPlayNetworking.registerGlobalReceiver(AddonControlPayload.TYPE, (payload, context) -> {
            ensureAddonConnection(context.player());
            serverAddons.receiveControl(context.player().getUUID(), payload.message());
        });
        ServerPlayNetworking.registerGlobalReceiver(AddonDataPayload.TYPE, (payload, context) -> {
            ensureAddonConnection(context.player());
            serverAddons.receiveData(context.player().getUUID(), payload.envelope());
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                ensureAddonConnection(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                serverAddons.disconnect(handler.getPlayer().getUUID()));
    }

    private void ensureAddonConnection(ServerPlayer player) {
        if (serverAddons.isConnected(player.getUUID()) || !supportsAddonBus(player)) {
            return;
        }
        serverAddons.connect(player.getUUID(), new AddonTransport() {
            @Override
            public void sendControl(AddonControlMessage message) {
                if (ServerPlayNetworking.canSend(player, AddonControlPayload.TYPE)) {
                    ServerPlayNetworking.send(player, new AddonControlPayload(message));
                }
            }

            @Override
            public void sendData(AddonEnvelope envelope) {
                if (ServerPlayNetworking.canSend(player, AddonDataPayload.TYPE)) {
                    ServerPlayNetworking.send(player, new AddonDataPayload(envelope));
                }
            }

            @Override
            public void disconnectForProtocolViolation() {
                player.connection.disconnect(Component.literal("Invalid SeeU addon traffic"));
            }
        });
    }

    private static boolean supportsAddonBus(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, AddonControlPayload.TYPE)
                && ServerPlayNetworking.canSend(player, AddonDataPayload.TYPE);
    }
}
