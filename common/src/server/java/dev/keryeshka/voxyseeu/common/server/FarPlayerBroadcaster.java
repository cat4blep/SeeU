package dev.keryeshka.voxyseeu.common.server;

import dev.keryeshka.voxyseeu.common.SharedDefaults;
import dev.keryeshka.voxyseeu.common.protocol.ClientHelloPacket;
import dev.keryeshka.voxyseeu.common.protocol.FarItemSnapshot;
import dev.keryeshka.voxyseeu.common.protocol.FarPlayerMetadata;
import dev.keryeshka.voxyseeu.common.protocol.FarPlayerMetadataDelta;
import dev.keryeshka.voxyseeu.common.protocol.FarPlayerSnapshot;
import dev.keryeshka.voxyseeu.common.protocol.FarPlayersPacket;
import dev.keryeshka.voxyseeu.common.protocol.FarVehicleSnapshot;
import dev.keryeshka.voxyseeu.common.protocol.PacketCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

public final class FarPlayerBroadcaster {
    private final SeeUServerConfig config;
    private final Map<UUID, ClientHelloPacket> subscribers = new HashMap<>();
    private final Map<UUID, ViewerState> viewerStates = new HashMap<>();
    private final Set<UUID> forceNextFrame = new HashSet<>();
    private int tickCounter;

    public FarPlayerBroadcaster(SeeUServerConfig config) {
        this.config = config;
    }

    public void acceptHello(ServerPlayer player, ClientHelloPacket packet) {
        UUID viewerId = player.getUUID();
        if (!config.enabled || packet.protocolVersion() != SharedDefaults.PROTOCOL_VERSION) {
            subscribers.remove(viewerId);
            viewerStates.remove(viewerId);
            forceNextFrame.remove(viewerId);
            return;
        }
        subscribers.put(viewerId, packet);
        forceNextFrame.add(viewerId);
    }

    public void remove(ServerPlayer player) {
        UUID viewerId = player.getUUID();
        subscribers.remove(viewerId);
        viewerStates.remove(viewerId);
        forceNextFrame.remove(viewerId);
    }

    public void broadcast(
            MinecraftServer server,
            BiPredicate<ServerPlayer, ServerPlayer> canSeePlayer,
            BiConsumer<ServerPlayer, FarPlayersPacket> sendPacket
    ) {
        if (!config.enabled
                || subscribers.isEmpty()
                || subscribers.values().stream().noneMatch(ClientHelloPacket::enabled)) {
            return;
        }

        tickCounter++;
        boolean regularFrame = tickCounter >= config.updateIntervalTicks;
        if (regularFrame) {
            tickCounter = 0;
        } else if (forceNextFrame.isEmpty()) {
            return;
        }

        List<ServerPlayer> onlinePlayers = server.getPlayerList().getPlayers();
        List<TargetFrame> targetFrames = buildTargetFrames(onlinePlayers);
        for (ServerPlayer viewer : onlinePlayers) {
            UUID viewerId = viewer.getUUID();
            boolean forcedFrame = forceNextFrame.remove(viewerId);
            boolean shouldSend = regularFrame || forcedFrame;
            if (!shouldSend) {
                continue;
            }

            ClientHelloPacket viewerSettings = subscribers.get(viewerId);
            if (viewerSettings == null || !viewerSettings.enabled()) {
                continue;
            }
            sendPacket.accept(viewer, createPacket(viewer, targetFrames, viewerSettings, canSeePlayer));
        }
    }

    private List<TargetFrame> buildTargetFrames(List<ServerPlayer> onlinePlayers) {
        List<TargetFrame> targetFrames = new ArrayList<>(onlinePlayers.size());
        for (ServerPlayer target : onlinePlayers) {
            if (!target.isAlive()
                    || target.isRemoved()
                    || (!config.sendSpectators && target.isSpectator())
                    || target.isInvisible()) {
                continue;
            }

            FarPlayerMetadata metadata = new FarPlayerMetadata(
                    target.getGameProfile().getName(),
                    toItemSnapshot(target.getMainHandItem()),
                    toItemSnapshot(target.getOffhandItem()),
                    toItemSnapshot(target.getItemBySlot(EquipmentSlot.FEET)),
                    toItemSnapshot(target.getItemBySlot(EquipmentSlot.LEGS)),
                    toItemSnapshot(target.getItemBySlot(EquipmentSlot.CHEST)),
                    toItemSnapshot(target.getItemBySlot(EquipmentSlot.HEAD))
            );
            FarPlayerSnapshot withMetadata = new FarPlayerSnapshot(
                    target.getUUID(),
                    target.getX(),
                    target.getY(),
                    target.getZ(),
                    target.getYRot(),
                    target.getYHeadRot(),
                    target.getXRot(),
                    target.isShiftKeyDown(),
                    target.isFallFlying(),
                    target.isSwimming(),
                    toVehicleSnapshot(target.getVehicle()),
                    metadata
            );
            targetFrames.add(new TargetFrame(
                    target,
                    withMetadata,
                    withMetadata.withoutMetadata()
            ));
        }
        return List.copyOf(targetFrames);
    }

    private FarPlayersPacket createPacket(
            ServerPlayer viewer,
            List<TargetFrame> targetFrames,
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
        String dimensionKey = viewer.level().dimension().location().toString();
        double viewerX = viewer.getX();
        double viewerY = viewer.getY();
        double viewerZ = viewer.getZ();

        ViewerState state = viewerStates.computeIfAbsent(viewer.getUUID(), ignored -> new ViewerState());
        state.metadataDelta.beginFrame(dimensionKey);

        List<FarPlayerSnapshot> snapshots = new ArrayList<>();
        for (TargetFrame frame : targetFrames) {
            ServerPlayer target = frame.player();
            if (target == viewer
                    || target.level() != viewer.level()
                    || !canSeePlayer.test(target, viewer)) {
                continue;
            }

            double deltaX = viewerX - frame.withMetadata().x();
            double deltaY = viewerY - frame.withMetadata().y();
            double deltaZ = viewerZ - frame.withMetadata().z();
            double distanceSquared = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
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

            snapshots.add(state.metadataDelta.apply(frame.withMetadata(), frame.withoutMetadata()));
            if (snapshots.size() == PacketCodec.MAX_PLAYERS_PER_PACKET) {
                break;
            }
        }
        state.metadataDelta.endFrame();

        return new FarPlayersPacket(
                dimensionKey,
                state.nextSequence(),
                config.updateIntervalTicks,
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

    private record TargetFrame(
            ServerPlayer player,
            FarPlayerSnapshot withMetadata,
            FarPlayerSnapshot withoutMetadata
    ) {
    }

    private static final class ViewerState {
        private final FarPlayerMetadataDelta metadataDelta = new FarPlayerMetadataDelta();
        private long sequence;

        private long nextSequence() {
            if (sequence == Long.MAX_VALUE) {
                throw new IllegalStateException("SeeU packet sequence exhausted");
            }
            return ++sequence;
        }
    }
}
