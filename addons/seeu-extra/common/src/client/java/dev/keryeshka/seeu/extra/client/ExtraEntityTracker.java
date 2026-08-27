package dev.keryeshka.seeu.extra.client;

import dev.keryeshka.seeu.extra.protocol.EntitySnapshot;
import dev.keryeshka.seeu.extra.protocol.EntitySnapshotPacket;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ExtraEntityTracker {
    private final Map<UUID, TrackedExtraEntity> entities = new HashMap<>();
    private String dimensionKey = "";
    private long lastSequence = -1;

    public void clear() {
        entities.clear();
        dimensionKey = "";
        lastSequence = -1;
    }

    public void apply(EntitySnapshotPacket packet) {
        long now = System.nanoTime();
        if (!dimensionKey.equals(packet.dimensionKey())) {
            entities.clear();
            dimensionKey = packet.dimensionKey();
            lastSequence = -1;
        }
        if (!isNewer(packet.sequence())) {
            return;
        }
        lastSequence = packet.sequence();

        Set<UUID> present = packet.full() ? new HashSet<>(packet.snapshots().size()) : null;
        for (EntitySnapshot snapshot : packet.snapshots()) {
            if (present != null) {
                present.add(snapshot.uuid());
            }
            entities.compute(snapshot.uuid(), (uuid, current) -> {
                if (current == null) {
                    return new TrackedExtraEntity(snapshot, packet.updateIntervalTicks(), now);
                }
                current.apply(snapshot, packet.updateIntervalTicks(), now);
                return current;
            });
        }
        if (present != null) {
            entities.keySet().removeIf(uuid -> !present.contains(uuid));
        }
    }

    String dimensionKey() {
        return dimensionKey;
    }

    Collection<TrackedExtraEntity> entities() {
        return entities.values();
    }

    private boolean isNewer(long sequence) {
        return lastSequence < 0 || sequence > lastSequence || (lastSequence == Long.MAX_VALUE && sequence == 0);
    }
}
