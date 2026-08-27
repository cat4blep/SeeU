package dev.keryeshka.voxyseeu.fabric;

import dev.keryeshka.voxyseeu.api.addon.AddonTransport;
import dev.keryeshka.voxyseeu.api.addon.SeeUServerAddons;
import dev.keryeshka.voxyseeu.api.addon.protocol.AddonControlMessage;
import dev.keryeshka.voxyseeu.api.addon.protocol.AddonEnvelope;
import dev.keryeshka.voxyseeu.common.server.SeeUServerConfig;
import dev.keryeshka.voxyseeu.fabric.network.AddonControlPayload;
import dev.keryeshka.voxyseeu.fabric.network.AddonDataPayload;
import dev.keryeshka.voxyseeu.fabric.network.ClientHelloPayload;
import dev.keryeshka.voxyseeu.fabric.network.FabricAddonFragmentAssembler;
import dev.keryeshka.voxyseeu.fabric.network.FabricAddonFragmenter;
import dev.keryeshka.voxyseeu.fabric.network.FabricAddonWireCodec;
import dev.keryeshka.voxyseeu.fabric.network.FabricAddonWireLimits;
import dev.keryeshka.voxyseeu.fabric.network.FabricPayloads;
import dev.keryeshka.voxyseeu.fabric.server.FabricFarPlayerService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class VoxySeeUFabric implements ModInitializer {
    private final SeeUServerAddons serverAddons = SeeUServerAddons.getInstance();
    private final Map<UUID, FabricAddonFragmentAssembler> controlAssemblies = new HashMap<>();
    private final Map<UUID, FabricAddonFragmentAssembler> dataAssemblies = new HashMap<>();
    private final Set<UUID> rejectedAddonPeers = new HashSet<>();

    @Override
    public void onInitialize() {
        FabricPayloads.register();

        FabricFarPlayerService service = new FabricFarPlayerService(
                SeeUServerConfig.load(FabricLoader.getInstance().getConfigDir())
        );
        service.register();

        ServerPlayNetworking.registerGlobalReceiver(ClientHelloPayload.TYPE, (payload, context) ->
                service.acceptHello(context.player(), payload.packet()));
        ServerPlayNetworking.registerGlobalReceiver(AddonControlPayload.TYPE, (payload, context) ->
                receiveControl(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(AddonDataPayload.TYPE, (payload, context) ->
                receiveData(context.player(), payload));
        ServerTickEvents.END_SERVER_TICK.register(this::expireAssemblies);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                ensureAddonConnection(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID playerId = handler.getPlayer().getUUID();
            clearAssemblies(playerId);
            rejectedAddonPeers.remove(playerId);
            serverAddons.disconnect(playerId);
        });
    }

    private void receiveControl(ServerPlayer player, AddonControlPayload payload) {
        UUID playerId = player.getUUID();
        if (player.isRemoved() || rejectedAddonPeers.contains(playerId)) {
            clearAssemblies(playerId);
            return;
        }
        FabricAddonFragmentAssembler assembler = controlAssemblies.computeIfAbsent(
                playerId,
                ignored -> new FabricAddonFragmentAssembler(FabricAddonWireLimits.CONTROL_BYTES)
        );
        try {
            assembler.accept(payload.fragment()).ifPresent(bytes -> {
                AddonControlMessage message;
                try {
                    message = FabricAddonWireCodec.decodeControl(bytes);
                } catch (RuntimeException ignored) {
                    rejectAddonTraffic(player);
                    return;
                }
                ensureAddonConnection(player);
                serverAddons.receiveControl(playerId, message);
            });
        } catch (IllegalArgumentException ignored) {
            rejectAddonTraffic(player);
        }
    }

    private void receiveData(ServerPlayer player, AddonDataPayload payload) {
        UUID playerId = player.getUUID();
        if (player.isRemoved() || rejectedAddonPeers.contains(playerId)) {
            clearAssemblies(playerId);
            return;
        }
        FabricAddonFragmentAssembler assembler = dataAssemblies.computeIfAbsent(
                playerId,
                ignored -> new FabricAddonFragmentAssembler(FabricAddonWireLimits.DATA_BYTES)
        );
        try {
            assembler.accept(payload.fragment()).ifPresent(bytes -> {
                AddonEnvelope envelope;
                try {
                    envelope = FabricAddonWireCodec.decodeData(bytes);
                } catch (RuntimeException ignored) {
                    rejectAddonTraffic(player);
                    return;
                }
                ensureAddonConnection(player);
                serverAddons.receiveData(playerId, envelope);
            });
        } catch (IllegalArgumentException ignored) {
            rejectAddonTraffic(player);
        }
    }

    private void expireAssemblies(MinecraftServer server) {
        long nowNanos = System.nanoTime();
        Set<UUID> expiredPlayers = new HashSet<>();
        controlAssemblies.forEach((playerId, assembler) -> {
            if (assembler.isExpired(nowNanos)) {
                expiredPlayers.add(playerId);
            }
        });
        dataAssemblies.forEach((playerId, assembler) -> {
            if (assembler.isExpired(nowNanos)) {
                expiredPlayers.add(playerId);
            }
        });
        for (UUID playerId : expiredPlayers) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                rejectAddonTraffic(player);
            } else {
                clearAssemblies(playerId);
                serverAddons.disconnect(playerId);
            }
        }
    }

    private void ensureAddonConnection(ServerPlayer player) {
        if (rejectedAddonPeers.contains(player.getUUID())
                || serverAddons.isConnected(player.getUUID())
                || !supportsAddonBus(player)) {
            return;
        }
        serverAddons.connect(player.getUUID(), new AddonTransport() {
            private final FabricAddonFragmenter controlFragmenter = new FabricAddonFragmenter();
            private final FabricAddonFragmenter dataFragmenter = new FabricAddonFragmenter();

            @Override
            public void sendControl(AddonControlMessage message) {
                if (ServerPlayNetworking.canSend(player, AddonControlPayload.TYPE)) {
                    for (var fragment : controlFragmenter.fragment(
                            FabricAddonWireCodec.encodeControl(message),
                            FabricAddonWireLimits.CONTROL_BYTES
                    )) {
                        ServerPlayNetworking.send(player, new AddonControlPayload(fragment));
                    }
                }
            }

            @Override
            public void sendData(AddonEnvelope envelope) {
                if (ServerPlayNetworking.canSend(player, AddonDataPayload.TYPE)) {
                    for (var fragment : dataFragmenter.fragment(
                            FabricAddonWireCodec.encodeData(envelope),
                            FabricAddonWireLimits.DATA_BYTES
                    )) {
                        ServerPlayNetworking.send(player, new AddonDataPayload(fragment));
                    }
                }
            }

            @Override
            public void disconnectForProtocolViolation() {
                rejectedAddonPeers.add(player.getUUID());
                clearAssemblies(player.getUUID());
                player.connection.disconnect(Component.literal("Invalid SeeU addon traffic"));
            }
        });
    }

    private void rejectAddonTraffic(ServerPlayer player) {
        UUID playerId = player.getUUID();
        ensureAddonConnection(player);
        rejectedAddonPeers.add(playerId);
        clearAssemblies(playerId);
        if (serverAddons.isConnected(playerId)) {
            serverAddons.rejectProtocolViolation(playerId);
        } else {
            player.connection.disconnect(Component.literal("Invalid SeeU addon traffic"));
        }
    }

    private void clearAssemblies(UUID playerId) {
        controlAssemblies.remove(playerId);
        dataAssemblies.remove(playerId);
    }

    private static boolean supportsAddonBus(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, AddonControlPayload.TYPE)
                && ServerPlayNetworking.canSend(player, AddonDataPayload.TYPE);
    }
}
