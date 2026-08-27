package dev.keryeshka.seeu.extra.server;

import dev.keryeshka.seeu.extra.config.EntitySelector;
import dev.keryeshka.seeu.extra.config.ExtraServerSettings;
import dev.keryeshka.seeu.extra.protocol.ClientOffer;
import dev.keryeshka.seeu.extra.protocol.EntityFlags;
import dev.keryeshka.seeu.extra.protocol.EntitySnapshot;
import dev.keryeshka.seeu.extra.protocol.EntitySnapshotPacket;
import dev.keryeshka.seeu.extra.protocol.EquipmentSnapshot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;

public final class ExtraEntityBroadcaster {
    private static final Logger LOGGER = LoggerFactory.getLogger("SeeU Extra");
    private static final Comparator<RankedCandidate> NEAREST_FIRST = Comparator
            .comparingDouble(RankedCandidate::distanceSquared)
            .thenComparing(candidate -> candidate.candidate().entity().getUUID());

    private ExtraServerSettings settings;
    private final Set<String> quarantinedTypes = new HashSet<>();
    private int elapsedTicks;
    private long sequence;

    public ExtraEntityBroadcaster(ExtraServerSettings settings) {
        this.settings = settings;
    }

    public void updateSettings(ExtraServerSettings settings) {
        this.settings = settings;
        this.elapsedTicks = 0;
        this.quarantinedTypes.clear();
    }

    public boolean isEnabled() {
        return settings.enabled();
    }

    public void tick(MinecraftServer server, Collection<? extends ExtraViewerSession> sessions) {
        ExtraServerSettings currentSettings = settings;
        if (!currentSettings.enabled()) {
            elapsedTicks = 0;
            return;
        }
        if (sessions.isEmpty()) {
            return;
        }

        elapsedTicks++;
        if (elapsedTicks < currentSettings.updateIntervalTicks()) {
            return;
        }
        elapsedTicks = 0;

        Map<ServerLevel, List<ExtraViewerSession>> sessionsByLevel = groupSessions(sessions);
        if (sessionsByLevel.isEmpty()) {
            return;
        }

        EntitySelector selector = currentSettings.selector();
        long packetSequence = nextSequence();
        for (Map.Entry<ServerLevel, List<ExtraViewerSession>> entry : sessionsByLevel.entrySet()) {
            ServerLevel level = entry.getKey();
            SpatialCellIndex<EntityCandidate> candidates = scanLevel(server, level, selector);
            Map<UUID, EntitySnapshot> snapshotCache = new HashMap<>();
            Set<UUID> failedSnapshots = new HashSet<>();
            String dimensionKey = level.dimension().identifier().toString();
            for (ExtraViewerSession session : entry.getValue()) {
                sendSnapshot(
                        server,
                        session,
                        dimensionKey,
                        packetSequence,
                        candidates,
                        snapshotCache,
                        failedSnapshots,
                        currentSettings
                );
            }
        }
    }

    private static Map<ServerLevel, List<ExtraViewerSession>> groupSessions(
            Collection<? extends ExtraViewerSession> sessions
    ) {
        Map<ServerLevel, List<ExtraViewerSession>> grouped = new IdentityHashMap<>();
        for (ExtraViewerSession session : sessions) {
            if (session == null || session.offer() == null || !session.offer().enabled()) {
                continue;
            }
            ServerPlayer player = session.player();
            if (player == null || player.isRemoved()) {
                continue;
            }
            ServerLevel level = player.level();
            grouped.computeIfAbsent(level, ignored -> new ArrayList<>()).add(session);
        }
        return grouped;
    }

    private SpatialCellIndex<EntityCandidate> scanLevel(
            MinecraftServer server,
            ServerLevel level,
            EntitySelector selector
    ) {
        SpatialCellIndex<EntityCandidate> candidates = new SpatialCellIndex<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity.isRemoved() || !entity.isAlive()) {
                continue;
            }

            Identifier typeIdentifier = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            if (typeIdentifier == null) {
                continue;
            }
            String typeId = typeIdentifier.toString();
            if (quarantinedTypes.contains(typeId)
                    || !selector.selects(typeId)
                    || containsPlayer(entity)) {
                continue;
            }
            EntityCandidate candidate = new EntityCandidate(
                    entity,
                    typeId,
                    vanillaTrackingRangeBlocks(server, entity)
            );
            candidates.add(candidate, entity.getX(), entity.getY(), entity.getZ());
        }
        return candidates;
    }

    private static boolean containsPlayer(Entity entity) {
        if (entity instanceof Player) {
            return true;
        }
        for (Entity passenger : entity.getPassengers()) {
            if (containsPlayer(passenger)) {
                return true;
            }
        }
        return false;
    }

    private void sendSnapshot(
            MinecraftServer server,
            ExtraViewerSession session,
            String dimensionKey,
            long sequence,
            SpatialCellIndex<EntityCandidate> candidates,
            Map<UUID, EntitySnapshot> snapshotCache,
            Set<UUID> failedSnapshots,
            ExtraServerSettings settings
    ) {
        ServerPlayer viewer = session.player();
        ClientOffer offer = session.offer();
        int maximumDistance = Math.min(settings.maximumDistanceBlocks(), offer.maximumDistanceBlocks());
        int minimumDistance = Math.max(settings.minimumDistanceBlocks(), offer.minimumDistanceBlocks());

        List<EntitySnapshot> snapshots;
        if (minimumDistance > maximumDistance) {
            snapshots = List.of();
        } else {
            List<EntityCandidate> nearest = selectNearest(
                    server,
                    viewer,
                    candidates,
                    minimumDistance,
                    maximumDistance,
                    settings.entityCap()
            );
            snapshots = new ArrayList<>(nearest.size());
            for (EntityCandidate candidate : nearest) {
                UUID uuid = candidate.entity().getUUID();
                if (failedSnapshots.contains(uuid) || quarantinedTypes.contains(candidate.typeId())) {
                    continue;
                }
                EntitySnapshot snapshot = snapshotCache.get(uuid);
                if (snapshot == null) {
                    try {
                        snapshot = createSnapshot(candidate);
                        snapshotCache.put(uuid, snapshot);
                    } catch (RuntimeException | LinkageError failure) {
                        failedSnapshots.add(uuid);
                        quarantine(candidate.typeId(), failure);
                        continue;
                    }
                }
                snapshots.add(snapshot);
            }
        }

        try {
            session.send(new EntitySnapshotPacket(
                    dimensionKey,
                    sequence,
                    settings.updateIntervalTicks(),
                    true,
                    snapshots
            ));
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to send an entity snapshot to {}", viewer.getGameProfile().name(), exception);
        }
    }

    private static List<EntityCandidate> selectNearest(
            MinecraftServer server,
            ServerPlayer viewer,
            SpatialCellIndex<EntityCandidate> candidates,
            int minimumDistanceBlocks,
            int maximumDistanceBlocks,
            int cap
    ) {
        if (cap <= 0) {
            return List.of();
        }

        PriorityQueue<RankedCandidate> nearest = new PriorityQueue<>(cap, NEAREST_FIRST.reversed());
        double minimumDistanceSquared = square(minimumDistanceBlocks);
        double maximumDistanceSquared = square(maximumDistanceBlocks);
        int requestedViewDistance = viewer.requestedViewDistance();
        int serverViewDistance = server.getPlayerList().getViewDistance();
        int vanillaViewDistanceBlocks = Math.max(2, Math.min(requestedViewDistance, serverViewDistance)) * 16;
        candidates.visitNearby(
                viewer.getX(),
                viewer.getZ(),
                maximumDistanceBlocks,
                () -> nearest.size() == cap
                        ? nearest.peek().distanceSquared()
                        : maximumDistanceSquared,
                cellCandidates -> {
                    for (EntityCandidate candidate : cellCandidates) {
                        Entity entity = candidate.entity();
                        if (!entity.broadcastToPlayer(viewer)
                                || isTrackedByVanilla(viewer, candidate, vanillaViewDistanceBlocks)) {
                            continue;
                        }

                        double distanceSquared = viewer.distanceToSqr(entity);
                        if (distanceSquared < minimumDistanceSquared || distanceSquared > maximumDistanceSquared) {
                            continue;
                        }

                        if (nearest.size() < cap) {
                            nearest.add(new RankedCandidate(candidate, distanceSquared));
                        } else if (isNearer(candidate, distanceSquared, nearest.peek())) {
                            nearest.remove();
                            nearest.add(new RankedCandidate(candidate, distanceSquared));
                        }
                    }
                }
        );

        List<RankedCandidate> ranked = new ArrayList<>(nearest);
        ranked.sort(NEAREST_FIRST);
        List<EntityCandidate> result = new ArrayList<>(ranked.size());
        for (RankedCandidate candidate : ranked) {
            result.add(candidate.candidate());
        }
        return result;
    }

    private static boolean isTrackedByVanilla(
            ServerPlayer viewer,
            EntityCandidate candidate,
            int vanillaViewDistanceBlocks
    ) {
        int trackingDistance = Math.min(
                candidate.vanillaTrackingRangeBlocks(),
                vanillaViewDistanceBlocks
        );
        if (trackingDistance <= 0
                || !viewer.getChunkTrackingView().contains(candidate.entity().chunkPosition())) {
            return false;
        }

        double deltaX = viewer.getX() - candidate.entity().getX();
        double deltaZ = viewer.getZ() - candidate.entity().getZ();
        return deltaX * deltaX + deltaZ * deltaZ <= square(trackingDistance);
    }

    private static boolean isNearer(
            EntityCandidate candidate,
            double distanceSquared,
            RankedCandidate currentFarthest
    ) {
        int distanceComparison = Double.compare(distanceSquared, currentFarthest.distanceSquared());
        if (distanceComparison != 0) {
            return distanceComparison < 0;
        }
        return candidate.entity().getUUID()
                .compareTo(currentFarthest.candidate().entity().getUUID()) < 0;
    }

    private static int vanillaTrackingRangeBlocks(MinecraftServer server, Entity entity) {
        int rangeBlocks = clientTrackingRangeBlocks(entity);
        return server.getScaledTrackingDistance(rangeBlocks);
    }

    private static int clientTrackingRangeBlocks(Entity entity) {
        int rangeBlocks = safeBlocks(entity.getType().clientTrackingRange());
        for (Entity passenger : entity.getPassengers()) {
            rangeBlocks = Math.max(rangeBlocks, clientTrackingRangeBlocks(passenger));
        }
        return rangeBlocks;
    }

    private static int safeBlocks(int chunkDistance) {
        if (chunkDistance <= 0) {
            return 0;
        }
        if (chunkDistance > Integer.MAX_VALUE / 16) {
            return Integer.MAX_VALUE;
        }
        return chunkDistance * 16;
    }

    private static EntitySnapshot createSnapshot(EntityCandidate candidate) {
        Entity entity = candidate.entity();
        Vec3 velocity = entity.getDeltaMovement();
        LivingEntity living = entity instanceof LivingEntity value ? value : null;
        int flags = flags(entity, living);
        return new EntitySnapshot(
                entity.getUUID(),
                candidate.typeId(),
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                entity.getPreciseBodyRotation(1.0F),
                entity.getYHeadRot(),
                entity.getXRot(),
                velocity.x,
                velocity.y,
                velocity.z,
                entity.tickCount,
                entity.getPose().name(),
                flags,
                equipment(living, EquipmentSlot.MAINHAND),
                equipment(living, EquipmentSlot.OFFHAND),
                equipment(living, EquipmentSlot.FEET),
                equipment(living, EquipmentSlot.LEGS),
                equipment(living, EquipmentSlot.CHEST),
                equipment(living, EquipmentSlot.HEAD)
        );
    }

    private static int flags(Entity entity, LivingEntity living) {
        int flags = 0;
        if (entity.isOnFire()) {
            flags |= EntityFlags.ON_FIRE;
        }
        if (entity.isInvisible()) {
            flags |= EntityFlags.INVISIBLE;
        }
        if (entity.isCurrentlyGlowing()) {
            flags |= EntityFlags.GLOWING;
        }
        if (entity.isShiftKeyDown()) {
            flags |= EntityFlags.CROUCHING;
        }
        if (entity.isSprinting()) {
            flags |= EntityFlags.SPRINTING;
        }
        if (entity.isSwimming()) {
            flags |= EntityFlags.SWIMMING;
        }
        if (living != null && living.isFallFlying()) {
            flags |= EntityFlags.FALL_FLYING;
        }
        if (living != null && living.isBaby()) {
            flags |= EntityFlags.BABY;
        }
        return flags;
    }

    private static EquipmentSnapshot equipment(LivingEntity living, EquipmentSlot slot) {
        if (living == null) {
            return EquipmentSnapshot.EMPTY;
        }
        ItemStack stack = living.getItemBySlot(slot);
        if (stack.isEmpty()) {
            return EquipmentSnapshot.EMPTY;
        }
        Identifier itemIdentifier = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemIdentifier == null) {
            return EquipmentSnapshot.EMPTY;
        }
        return new EquipmentSnapshot(itemIdentifier.toString(), stack.getCount());
    }

    private long nextSequence() {
        if (sequence == Long.MAX_VALUE) {
            sequence = 0;
        } else {
            sequence++;
        }
        return sequence;
    }

    private void quarantine(String typeId, Throwable failure) {
        if (quarantinedTypes.add(typeId)) {
            LOGGER.warn("Quarantined server entity type {} after snapshot extraction failed", typeId, failure);
        }
    }

    private static double square(int value) {
        return (double) value * value;
    }

    private record EntityCandidate(Entity entity, String typeId, int vanillaTrackingRangeBlocks) {
    }

    private record RankedCandidate(EntityCandidate candidate, double distanceSquared) {
    }
}
