package dev.keryeshka.voxyseeu.fabric.server;

import dev.keryeshka.voxyseeu.common.SharedDefaults;
import dev.keryeshka.voxyseeu.common.protocol.ClientHelloPacket;
import dev.keryeshka.voxyseeu.common.protocol.FarItemSnapshot;
import dev.keryeshka.voxyseeu.common.protocol.FarPlayerMetadata;
import dev.keryeshka.voxyseeu.common.protocol.FarPlayerMetadataDelta;
import dev.keryeshka.voxyseeu.common.protocol.FarPlayerSnapshot;
import dev.keryeshka.voxyseeu.common.protocol.FarPlayersPacket;
import dev.keryeshka.voxyseeu.common.protocol.FarVehicleSnapshot;
import dev.keryeshka.voxyseeu.common.protocol.PacketCodec;
import dev.keryeshka.voxyseeu.fabric.config.VoxySeeUServerConfig;
import dev.keryeshka.voxyseeu.fabric.network.FarPlayersPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class FabricFarPlayerService {
    private static final VisibilityBridge VISIBILITY_BRIDGE = VisibilityBridge.create();

    private final VoxySeeUServerConfig config;
    private final Map<UUID, ClientHelloPacket> subscribers = new HashMap<>();
    private final Map<UUID, ViewerState> viewerStates = new HashMap<>();
    private final Set<UUID> forceNextFrame = new HashSet<>();
    private int tickCounter;

    public FabricFarPlayerService(VoxySeeUServerConfig config) {
        this.config = config;
    }

    public void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> remove(handler.getPlayer()));
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
    }

    public void handleHello(ServerPlayer player, ClientHelloPacket packet) {
        UUID viewerId = player.getUUID();
        if (!config.enabled || packet.protocolVersion() != SharedDefaults.PROTOCOL_VERSION) {
            remove(viewerId);
            return;
        }
        subscribers.put(viewerId, packet);
        forceNextFrame.add(viewerId);
    }

    private void remove(ServerPlayer player) {
        remove(player.getUUID());
    }

    private void remove(UUID viewerId) {
        subscribers.remove(viewerId);
        viewerStates.remove(viewerId);
        forceNextFrame.remove(viewerId);
    }

    private void onServerTick(MinecraftServer server) {
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
            if (!regularFrame && !forcedFrame) {
                continue;
            }

            ClientHelloPacket viewerSettings = subscribers.get(viewerId);
            if (viewerSettings == null || !viewerSettings.enabled()) {
                continue;
            }
            ServerPlayNetworking.send(
                    viewer,
                    new FarPlayersPayload(createPacket(viewer, targetFrames, viewerSettings))
            );
        }
    }

    private List<TargetFrame> buildTargetFrames(List<ServerPlayer> onlinePlayers) {
        List<TargetFrame> targetFrames = new ArrayList<>(onlinePlayers.size());
        for (ServerPlayer target : onlinePlayers) {
            if (isWaitingToRespawn(target)
                    || (!config.sendSpectators && target.isSpectator())
                    || target.isInvisible()) {
                continue;
            }

            FarPlayerMetadata metadata = new FarPlayerMetadata(
                    target.getGameProfile().name(),
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
            ClientHelloPacket viewerSettings
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
        String dimensionKey = viewer.level().dimension().identifier().toString();
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
                    || !VISIBILITY_BRIDGE.canSee(target, viewer)) {
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

    private static boolean isWaitingToRespawn(ServerPlayer player) {
        return !player.isAlive() || player.isRemoved();
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
                        Object result = method.invoke(null, target, viewer);
                        return Boolean.TRUE.equals(result);
                    } catch (IllegalAccessException | InvocationTargetException exception) {
                        return true;
                    }
                };
            } catch (ClassNotFoundException | NoSuchMethodException exception) {
                return (target, viewer) -> true;
            }
        }
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
