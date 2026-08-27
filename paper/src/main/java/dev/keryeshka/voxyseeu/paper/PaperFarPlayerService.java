package dev.keryeshka.voxyseeu.paper;

import dev.keryeshka.voxyseeu.common.SharedDefaults;
import dev.keryeshka.voxyseeu.common.protocol.ClientHelloPacket;
import dev.keryeshka.voxyseeu.common.protocol.FarItemSnapshot;
import dev.keryeshka.voxyseeu.common.protocol.FarPlayerMetadata;
import dev.keryeshka.voxyseeu.common.protocol.FarPlayerMetadataDelta;
import dev.keryeshka.voxyseeu.common.protocol.FarPlayerSnapshot;
import dev.keryeshka.voxyseeu.common.protocol.FarPlayersPacket;
import dev.keryeshka.voxyseeu.common.protocol.FarVehicleSnapshot;
import dev.keryeshka.voxyseeu.common.protocol.PacketCodec;
import dev.keryeshka.voxyseeu.common.protocol.ProtocolConstants;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class PaperFarPlayerService {
    private final VoxySeeUPaperPlugin plugin;
    private final int updateIntervalTicks;
    private final Map<UUID, ClientSettings> subscribers = new ConcurrentHashMap<>();
    private final Map<UUID, ViewerState> viewerStates = new HashMap<>();
    private final Set<UUID> pendingInitialFrames = new HashSet<>();

    PaperFarPlayerService(VoxySeeUPaperPlugin plugin, int updateIntervalTicks) {
        this.plugin = plugin;
        this.updateIntervalTicks = updateIntervalTicks;
    }

    void acceptHello(Player player, byte[] bytes) {
        ClientHelloPacket packet;
        ByteBuf buf = Unpooled.wrappedBuffer(bytes);
        try {
            packet = PacketCodec.decodeClientHello(buf);
            if (buf.isReadable()) {
                throw new IllegalArgumentException("trailing data in hello payload");
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().warning(String.format(
                    "Ignoring malformed SeeU hello from %s: %s",
                    player.getName(),
                    exception.getMessage()
            ));
            return;
        } finally {
            buf.release();
        }

        UUID viewerId = player.getUniqueId();
        if (packet.protocolVersion() != SharedDefaults.PROTOCOL_VERSION) {
            subscribers.remove(viewerId);
            viewerStates.remove(viewerId);
            pendingInitialFrames.remove(viewerId);
            plugin.getLogger().warning(String.format(
                    "Ignoring SeeU hello from %s because protocol %d does not match %d",
                    player.getName(),
                    packet.protocolVersion(),
                    SharedDefaults.PROTOCOL_VERSION
            ));
            return;
        }
        ClientSettings settings = new ClientSettings(
                packet.enabled(),
                Math.max(0, packet.maximumRenderDistanceBlocks()),
                Math.max(0, packet.minimumProxyDistanceBlocks()),
                packet.shareSelf(),
                Math.max(64, packet.shareMaximumDistanceBlocks())
        );
        subscribers.put(viewerId, settings);
        if (settings.enabled()) {
            pendingInitialFrames.add(viewerId);
            Bukkit.getScheduler().runTask(plugin, () -> sendPendingInitialFrame(player, settings));
        } else {
            pendingInitialFrames.remove(viewerId);
        }
        plugin.getLogger().info(String.format(
                "Received SeeU hello from %s: enabled=%s, maxDistance=%d, minDistance=%d, shareSelf=%s, shareMaxDistance=%d",
                player.getName(),
                packet.enabled(),
                packet.maximumRenderDistanceBlocks(),
                packet.minimumProxyDistanceBlocks(),
                packet.shareSelf(),
                packet.shareMaximumDistanceBlocks()
        ));
    }

    void remove(Player player) {
        UUID viewerId = player.getUniqueId();
        subscribers.remove(viewerId);
        viewerStates.remove(viewerId);
        pendingInitialFrames.remove(viewerId);
    }

    void broadcast() {
        if (!plugin.getConfig().getBoolean("enabled", true)
                || subscribers.isEmpty()
                || subscribers.values().stream().noneMatch(ClientSettings::enabled)) {
            return;
        }

        BroadcastConfig config = readConfig();
        List<TargetFrame> targetFrames = buildTargetFrames(config.sendSpectators());
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            ClientSettings settings = subscribers.get(viewer.getUniqueId());
            if (settings == null || !settings.enabled()) {
                continue;
            }
            pendingInitialFrames.remove(viewer.getUniqueId());
            sendPacket(viewer, createPacket(viewer, targetFrames, settings, config));
        }
    }

    private void sendPendingInitialFrame(Player player, ClientSettings expectedSettings) {
        UUID viewerId = player.getUniqueId();
        if (!player.isOnline()
                || !pendingInitialFrames.remove(viewerId)
                || !Objects.equals(subscribers.get(viewerId), expectedSettings)
                || !plugin.getConfig().getBoolean("enabled", true)) {
            return;
        }
        BroadcastConfig config = readConfig();
        sendPacket(
                player,
                createPacket(player, buildTargetFrames(config.sendSpectators()), expectedSettings, config)
        );
    }

    private BroadcastConfig readConfig() {
        return new BroadcastConfig(
                Math.max(0, plugin.getConfig().getInt(
                        "minimum-proxy-distance-blocks",
                        SharedDefaults.DEFAULT_MIN_PROXY_DISTANCE_BLOCKS
                )),
                Math.max(64, plugin.getConfig().getInt(
                        "max-render-distance-blocks",
                        SharedDefaults.DEFAULT_MAX_RENDER_DISTANCE_BLOCKS
                )),
                plugin.getConfig().getBoolean(
                        "send-spectators",
                        SharedDefaults.DEFAULT_SEND_SPECTATORS
                )
        );
    }

    private List<TargetFrame> buildTargetFrames(boolean sendSpectators) {
        List<TargetFrame> targetFrames = new ArrayList<>();
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (isWaitingToRespawn(target)
                    || (!sendSpectators && target.getGameMode() == GameMode.SPECTATOR)
                    || target.isInvisible()) {
                continue;
            }

            EntityEquipment equipment = target.getEquipment();
            FarPlayerMetadata metadata = new FarPlayerMetadata(
                    target.getName(),
                    toItemSnapshot(equipment.getItemInMainHand()),
                    toItemSnapshot(equipment.getItemInOffHand()),
                    toItemSnapshot(equipment.getBoots()),
                    toItemSnapshot(equipment.getLeggings()),
                    toItemSnapshot(equipment.getChestplate()),
                    toItemSnapshot(equipment.getHelmet())
            );
            FarPlayerSnapshot withMetadata = new FarPlayerSnapshot(
                    target.getUniqueId(),
                    target.getX(),
                    target.getY(),
                    target.getZ(),
                    target.getYaw(),
                    target.getYaw(),
                    target.getPitch(),
                    target.isSneaking(),
                    target.isGliding(),
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
            Player viewer,
            List<TargetFrame> targetFrames,
            ClientSettings settings,
            BroadcastConfig config
    ) {
        double minimumDistance = Math.max(
                config.minimumProxyDistanceBlocks(),
                settings.minimumProxyDistanceBlocks()
        );
        double maximumDistance = settings.maximumRenderDistanceBlocks() > 0
                ? Math.min(config.maximumRenderDistanceBlocks(), settings.maximumRenderDistanceBlocks())
                : config.maximumRenderDistanceBlocks();
        double minimumDistanceSquared = minimumDistance * minimumDistance;
        double maximumDistanceSquared = maximumDistance * maximumDistance;
        String dimensionKey = viewer.getWorld().getKey().toString();
        double viewerX = viewer.getX();
        double viewerY = viewer.getY();
        double viewerZ = viewer.getZ();

        ViewerState state = viewerStates.computeIfAbsent(viewer.getUniqueId(), ignored -> new ViewerState());
        state.metadataDelta.beginFrame(dimensionKey);

        List<FarPlayerSnapshot> snapshots = new ArrayList<>();
        for (TargetFrame frame : targetFrames) {
            Player target = frame.player();
            if (target.equals(viewer)
                    || !target.getWorld().equals(viewer.getWorld())
                    || !viewer.canSee(target)) {
                continue;
            }

            double deltaX = viewerX - frame.withMetadata().x();
            double deltaY = viewerY - frame.withMetadata().y();
            double deltaZ = viewerZ - frame.withMetadata().z();
            double distanceSquared = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
            if (distanceSquared < minimumDistanceSquared || distanceSquared > maximumDistanceSquared) {
                continue;
            }
            ClientSettings targetSettings = subscribers.get(target.getUniqueId());
            if (targetSettings != null) {
                if (!targetSettings.shareSelf()) {
                    continue;
                }
                double shareDistance = Math.min(
                        config.maximumRenderDistanceBlocks(),
                        targetSettings.shareMaximumDistanceBlocks()
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
                updateIntervalTicks,
                List.copyOf(snapshots)
        );
    }

    private void sendPacket(Player viewer, FarPlayersPacket packet) {
        ByteBuf buf = Unpooled.buffer();
        try {
            PacketCodec.encodeFarPlayers(buf, packet);
            byte[] payload = new byte[buf.readableBytes()];
            buf.readBytes(payload);
            viewer.sendPluginMessage(plugin, ProtocolConstants.PLAYERS_CHANNEL, payload);
        } finally {
            buf.release();
        }
    }

    private record ClientSettings(
            boolean enabled,
            int maximumRenderDistanceBlocks,
            int minimumProxyDistanceBlocks,
            boolean shareSelf,
            int shareMaximumDistanceBlocks
    ) {
    }

    private record BroadcastConfig(
            int minimumProxyDistanceBlocks,
            int maximumRenderDistanceBlocks,
            boolean sendSpectators
    ) {
    }

    private record TargetFrame(
            Player player,
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

    private static boolean isWaitingToRespawn(Player player) {
        return player.isDead() || player.getHealth() <= 0.0D || !player.isValid();
    }

    private static FarItemSnapshot toItemSnapshot(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return FarItemSnapshot.EMPTY;
        }
        return new FarItemSnapshot(stack.getType().getKey().toString(), stack.getAmount());
    }

    private static FarVehicleSnapshot toVehicleSnapshot(Entity entity) {
        if (entity == null) {
            return null;
        }
        return new FarVehicleSnapshot(
                entity.getUniqueId(),
                entity.getType().getKey().toString(),
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                entity.getYaw(),
                entity.getPitch()
        );
    }
}
