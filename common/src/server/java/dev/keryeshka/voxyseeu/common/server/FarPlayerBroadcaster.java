package dev.keryeshka.voxyseeu.common.server;

import dev.keryeshka.voxyseeu.common.SharedDefaults;
import dev.keryeshka.voxyseeu.common.protocol.ClientHelloPacket;
import dev.keryeshka.voxyseeu.common.protocol.FarItemSnapshot;
import dev.keryeshka.voxyseeu.common.protocol.FarPlayerSnapshot;
import dev.keryeshka.voxyseeu.common.protocol.FarPlayersPacket;
import dev.keryeshka.voxyseeu.common.protocol.FarVehicleSnapshot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

public final class FarPlayerBroadcaster {
    private final SeeUServerConfig config;
    private final Map<UUID, ClientHelloPacket> subscribers = new HashMap<>();
    private int tickCounter;

    public FarPlayerBroadcaster(SeeUServerConfig config) {
        this.config = config;
    }

    public void acceptHello(ServerPlayer player, ClientHelloPacket packet) {
        if (!config.enabled || packet.protocolVersion() != SharedDefaults.PROTOCOL_VERSION) {
            subscribers.remove(player.getUUID());
            return;
        }
        subscribers.put(player.getUUID(), packet);
    }

    public void remove(ServerPlayer player) {
        subscribers.remove(player.getUUID());
    }

    public void broadcast(
            MinecraftServer server,
            BiPredicate<ServerPlayer, ServerPlayer> canSeePlayer,
            BiConsumer<ServerPlayer, FarPlayersPacket> sendPacket
    ) {
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
            ClientHelloPacket viewerSettings = subscribers.get(viewer.getUUID());
            if (viewerSettings == null || !viewerSettings.enabled()) {
                continue;
            }
            sendPacket.accept(viewer, createPacket(viewer, onlinePlayers, viewerSettings, canSeePlayer));
        }
    }

    private FarPlayersPacket createPacket(
            ServerPlayer viewer,
            List<ServerPlayer> onlinePlayers,
            ClientHelloPacket viewerSettings,
            BiPredicate<ServerPlayer, ServerPlayer> canSeePlayer
    ) {
        double minimumDistance = Math.max(
                config.minimumProxyDistanceBlocks,
                Math.max(0, viewerSettings.minimumProxyDistanceBlocks())
        );
        int requestedMaximumDistance = Math.max(0, viewerSettings.maximumRenderDistanceBlocks());
        double maximumDistance = requestedMaximumDistance > 0
                ? Math.min(config.maxRenderDistanceBlocks, requestedMaximumDistance)
                : config.maxRenderDistanceBlocks;
        double minimumDistanceSquared = minimumDistance * minimumDistance;
        double maximumDistanceSquared = maximumDistance * maximumDistance;

        List<FarPlayerSnapshot> snapshots = new ArrayList<>();
        for (ServerPlayer target : onlinePlayers) {
            if (target == viewer
                    || target.level() != viewer.level()
                    || !target.isAlive()
                    || (!config.sendSpectators && target.isSpectator())
                    || target.isInvisible()
                    || !canSeePlayer.test(target, viewer)) {
                continue;
            }

            double distanceSquared = viewer.distanceToSqr(target);
            if (distanceSquared < minimumDistanceSquared || distanceSquared > maximumDistanceSquared) {
                continue;
            }
            ClientHelloPacket targetSettings = subscribers.get(target.getUUID());
            if (targetSettings != null) {
                if (!targetSettings.shareSelf()) {
                    continue;
                }
                double shareDistance = Math.min(
                        config.maxRenderDistanceBlocks,
                        Math.max(64, targetSettings.shareMaximumDistanceBlocks())
                );
                if (distanceSquared > shareDistance * shareDistance) {
                    continue;
                }
            }

            snapshots.add(new FarPlayerSnapshot(
                    target.getUUID(),
                    target.getGameProfile().name(),
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

        return new FarPlayersPacket(
                viewer.level().dimension().identifier().toString(),
                List.copyOf(snapshots)
        );
    }

    private static FarItemSnapshot toItemSnapshot(ItemStack stack) {
        if (stack.isEmpty()) {
            return FarItemSnapshot.EMPTY;
        }
        return new FarItemSnapshot(
                BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                stack.getCount()
        );
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
