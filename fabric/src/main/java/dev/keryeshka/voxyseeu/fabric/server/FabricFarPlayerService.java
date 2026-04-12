package dev.keryeshka.voxyseeu.fabric.server;

import dev.keryeshka.voxyseeu.common.protocol.ClientHelloPacket;
import dev.keryeshka.voxyseeu.common.protocol.FarItemSnapshot;
import dev.keryeshka.voxyseeu.common.protocol.FarPlayerSnapshot;
import dev.keryeshka.voxyseeu.common.protocol.FarPlayersPacket;
import dev.keryeshka.voxyseeu.common.protocol.FarVehicleSnapshot;
import dev.keryeshka.voxyseeu.fabric.config.VoxySeeUServerConfig;
import dev.keryeshka.voxyseeu.fabric.network.FarPlayersPayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FabricFarPlayerService {
    private final VoxySeeUServerConfig config;
    private final Map<UUID, ClientSettings> subscribers = new ConcurrentHashMap<>();
    private int tickCounter;

    public FabricFarPlayerService(VoxySeeUServerConfig config) {
        this.config = config;
    }

    public void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                subscribers.remove(handler.getPlayer().getUUID()));

        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
    }

    public void handleHello(ServerPlayer player, ClientHelloPacket packet) {
        if (!config.enabled) {
            subscribers.remove(player.getUUID());
            return;
        }
        if (packet.protocolVersion() != dev.keryeshka.voxyseeu.common.SharedDefaults.PROTOCOL_VERSION) {
            subscribers.remove(player.getUUID());
            return;
        }

        subscribers.put(player.getUUID(), new ClientSettings(
                packet.enabled(),
                Math.max(0, packet.maximumRenderDistanceBlocks()),
                Math.max(0, packet.minimumProxyDistanceBlocks()),
                packet.shareSelf(),
                Math.max(64, packet.shareMaximumDistanceBlocks())
        ));
    }

    private void onServerTick(MinecraftServer server) {
        if (!config.enabled || subscribers.isEmpty()) {
            return;
        }

        tickCounter++;
        if (tickCounter < config.updateIntervalTicks) {
            return;
        }
        tickCounter = 0;

        List<ServerPlayer> onlinePlayers = server.getPlayerList().getPlayers();
        for (ServerPlayer viewer : onlinePlayers) {
            ClientSettings settings = subscribers.get(viewer.getUUID());
            if (settings == null || !settings.enabled()) {
                continue;
            }
            sendSnapshot(viewer, onlinePlayers, settings);
        }
    }

    private void sendSnapshot(ServerPlayer viewer, List<ServerPlayer> onlinePlayers, ClientSettings settings) {
        double minimumDistance = Math.max(config.minimumProxyDistanceBlocks, settings.minimumProxyDistanceBlocks());
        double maximumDistance = settings.maximumRenderDistanceBlocks() > 0
                ? Math.min(config.maxRenderDistanceBlocks, settings.maximumRenderDistanceBlocks())
                : config.maxRenderDistanceBlocks;

        double minimumDistanceSquared = minimumDistance * minimumDistance;
        double maximumDistanceSquared = maximumDistance * maximumDistance;

        List<FarPlayerSnapshot> snapshots = new ArrayList<>();
        for (ServerPlayer target : onlinePlayers) {
            if (target == viewer) {
                continue;
            }
            if (target.level() != viewer.level()) {
                continue;
            }
            if (!config.sendSpectators && target.isSpectator()) {
                continue;
            }
            if (target.isInvisible()) {
                continue;
            }

            double distanceSquared = viewer.distanceToSqr(target);
            if (distanceSquared < minimumDistanceSquared || distanceSquared > maximumDistanceSquared) {
                continue;
            }
            ClientSettings targetSettings = subscribers.get(target.getUUID());
            if (targetSettings != null) {
                if (!targetSettings.shareSelf()) {
                    continue;
                }
                double shareDistance = Math.min(config.maxRenderDistanceBlocks, targetSettings.shareMaximumDistanceBlocks());
                if (distanceSquared > shareDistance * shareDistance) {
                    continue;
                }
            }

            snapshots.add(new FarPlayerSnapshot(
                    target.getUUID(),
                    target.getGameProfile().getName(),
                    target.getX(),
                    target.getY(),
                    target.getZ(),
                    target.getYRot(),
                    target.getYHeadRot(),
                    target.getXRot(),
                    target.isShiftKeyDown(),
                    target.isFallFlying(),
                    target.isSwimming(),
                    toItemSnapshot(target.getMainHandItem()),
                    toItemSnapshot(target.getOffhandItem()),
                    toItemSnapshot(target.getItemBySlot(EquipmentSlot.FEET)),
                    toItemSnapshot(target.getItemBySlot(EquipmentSlot.LEGS)),
                    toItemSnapshot(target.getItemBySlot(EquipmentSlot.CHEST)),
                    toItemSnapshot(target.getItemBySlot(EquipmentSlot.HEAD)),
                    toVehicleSnapshot(target.getVehicle())
            ));
        }

        FarPlayersPacket packet = new FarPlayersPacket(viewer.level().dimension().location().toString(), List.copyOf(snapshots));
        ServerPlayNetworking.send(viewer, new FarPlayersPayload(packet));
    }

    private record ClientSettings(
            boolean enabled,
            int maximumRenderDistanceBlocks,
            int minimumProxyDistanceBlocks,
            boolean shareSelf,
            int shareMaximumDistanceBlocks
    ) {
    }

    private static FarItemSnapshot toItemSnapshot(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return FarItemSnapshot.EMPTY;
        }
        return new FarItemSnapshot(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount());
    }

    private static FarVehicleSnapshot toVehicleSnapshot(Entity entity) {
        if (entity == null) {
            return null;
        }
        return new FarVehicleSnapshot(
                entity.getUUID(),
                BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                entity.getYRot(),
                entity.getXRot()
        );
    }
}
