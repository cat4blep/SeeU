package dev.keryeshka.voxyseeu.paper;

import dev.keryeshka.voxyseeu.common.SharedDefaults;
import dev.keryeshka.voxyseeu.common.protocol.ClientHelloPacket;
import dev.keryeshka.voxyseeu.common.protocol.FarItemSnapshot;
import dev.keryeshka.voxyseeu.common.protocol.FarPlayerSnapshot;
import dev.keryeshka.voxyseeu.common.protocol.FarPlayersPacket;
import dev.keryeshka.voxyseeu.common.protocol.FarVehicleSnapshot;
import dev.keryeshka.voxyseeu.common.protocol.PacketCodec;
import dev.keryeshka.voxyseeu.common.protocol.ProtocolConstants;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class PaperFarPlayerService {
    private final VoxySeeUPaperPlugin plugin;
    private final Map<UUID, ClientSettings> subscribers = new ConcurrentHashMap<>();

    PaperFarPlayerService(VoxySeeUPaperPlugin plugin) {
        this.plugin = plugin;
    }

    void handleHello(Player player, byte[] bytes) {
        ByteBuf buf = Unpooled.wrappedBuffer(bytes);
        ClientHelloPacket packet = PacketCodec.decodeClientHello(buf);
        if (packet.protocolVersion() != SharedDefaults.PROTOCOL_VERSION) {
            subscribers.remove(player.getUniqueId());
            plugin.getLogger().warning(String.format(
                    "Ignoring SeeU hello from %s because protocol %d does not match %d",
                    player.getName(),
                    packet.protocolVersion(),
                    SharedDefaults.PROTOCOL_VERSION
            ));
            return;
        }
        subscribers.put(player.getUniqueId(), new ClientSettings(
                packet.enabled(),
                Math.max(0, packet.maximumRenderDistanceBlocks()),
                Math.max(0, packet.minimumProxyDistanceBlocks()),
                packet.shareSelf(),
                Math.max(64, packet.shareMaximumDistanceBlocks())
        ));
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
        subscribers.remove(player.getUniqueId());
    }

    void broadcast() {
        if (!plugin.getConfig().getBoolean("enabled", true) || subscribers.isEmpty()) {
            return;
        }

        int configuredMinDistance = plugin.getConfig().getInt(
                "minimum-proxy-distance-blocks",
                SharedDefaults.DEFAULT_MIN_PROXY_DISTANCE_BLOCKS
        );
        int configuredMaxDistance = plugin.getConfig().getInt(
                "max-render-distance-blocks",
                SharedDefaults.DEFAULT_MAX_RENDER_DISTANCE_BLOCKS
        );
        boolean sendSpectators = plugin.getConfig().getBoolean(
                "send-spectators",
                SharedDefaults.DEFAULT_SEND_SPECTATORS
        );

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            ClientSettings settings = subscribers.get(viewer.getUniqueId());
            if (settings == null || !settings.enabled()) {
                continue;
            }

            double minimumDistance = Math.max(configuredMinDistance, settings.minimumProxyDistanceBlocks());
            double maximumDistance = settings.maximumRenderDistanceBlocks() > 0
                    ? Math.min(configuredMaxDistance, settings.maximumRenderDistanceBlocks())
                    : configuredMaxDistance;
            double minimumDistanceSquared = minimumDistance * minimumDistance;
            double maximumDistanceSquared = maximumDistance * maximumDistance;

            List<FarPlayerSnapshot> snapshots = new ArrayList<>();
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (target.equals(viewer)) {
                    continue;
                }
                if (!target.getWorld().equals(viewer.getWorld())) {
                    continue;
                }
                if (!sendSpectators && target.getGameMode() == GameMode.SPECTATOR) {
                    continue;
                }
                if (!viewer.canSee(target) || target.isInvisible()) {
                    continue;
                }

                double distanceSquared = viewer.getLocation().distanceSquared(target.getLocation());
                if (distanceSquared < minimumDistanceSquared || distanceSquared > maximumDistanceSquared) {
                    continue;
                }
                ClientSettings targetSettings = subscribers.get(target.getUniqueId());
                if (targetSettings != null) {
                    if (!targetSettings.shareSelf()) {
                        continue;
                    }
                    double shareDistance = Math.min(configuredMaxDistance, targetSettings.shareMaximumDistanceBlocks());
                    if (distanceSquared > shareDistance * shareDistance) {
                        continue;
                    }
                }

                Location location = target.getLocation();
                EntityEquipment equipment = target.getEquipment();
                snapshots.add(new FarPlayerSnapshot(
                        target.getUniqueId(),
                        target.getName(),
                        location.getX(),
                        location.getY(),
                        location.getZ(),
                        location.getYaw(),
                        location.getYaw(),
                        location.getPitch(),
                        target.isSneaking(),
                        target.isGliding(),
                        target.isSwimming(),
                        toItemSnapshot(equipment == null ? null : equipment.getItemInMainHand()),
                        toItemSnapshot(equipment == null ? null : equipment.getItemInOffHand()),
                        toItemSnapshot(equipment == null ? null : equipment.getBoots()),
                        toItemSnapshot(equipment == null ? null : equipment.getLeggings()),
                        toItemSnapshot(equipment == null ? null : equipment.getChestplate()),
                        toItemSnapshot(equipment == null ? null : equipment.getHelmet()),
                        toVehicleSnapshot(target.getVehicle())
                ));
            }

            FarPlayersPacket packet = new FarPlayersPacket(viewer.getWorld().getKey().toString(), List.copyOf(snapshots));
            ByteBuf buf = Unpooled.buffer();
            PacketCodec.encodeFarPlayers(buf, packet);

            byte[] payload = new byte[buf.readableBytes()];
            buf.readBytes(payload);
            viewer.sendPluginMessage(plugin, ProtocolConstants.PLAYERS_CHANNEL, payload);
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
        Location location = entity.getLocation();
        return new FarVehicleSnapshot(
                entity.getUniqueId(),
                entity.getType().getKey().toString(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
        );
    }
}
