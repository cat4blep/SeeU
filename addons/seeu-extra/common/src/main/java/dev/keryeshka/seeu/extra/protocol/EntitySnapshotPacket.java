package dev.keryeshka.seeu.extra.protocol;

import dev.keryeshka.seeu.extra.ResourceIdentifier;

import java.util.List;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public record EntitySnapshotPacket(
        String dimensionKey,
        long sequence,
        int updateIntervalTicks,
        boolean full,
        List<EntitySnapshot> snapshots
) {
    public EntitySnapshotPacket {
        if (!ResourceIdentifier.isValid(dimensionKey)) {
            throw new IllegalArgumentException("Invalid dimension identifier: " + dimensionKey);
        }
        if (sequence < 0) {
            throw new IllegalArgumentException("Sequence must not be negative");
        }
        if (updateIntervalTicks < 1 || updateIntervalTicks > ExtraProtocol.MAX_UPDATE_INTERVAL_TICKS) {
            throw new IllegalArgumentException("Update interval is outside the supported range");
        }
        Objects.requireNonNull(snapshots, "snapshots");
        if (snapshots.size() > ExtraProtocol.MAX_SNAPSHOTS) {
            throw new IllegalArgumentException("Snapshot count exceeds " + ExtraProtocol.MAX_SNAPSHOTS);
        }
        snapshots = List.copyOf(snapshots);
        Set<java.util.UUID> seen = new HashSet<>(snapshots.size());
        for (EntitySnapshot snapshot : snapshots) {
            Objects.requireNonNull(snapshot, "snapshot");
            if (!seen.add(snapshot.uuid())) {
                throw new IllegalArgumentException("Snapshot packet contains a duplicate UUID: " + snapshot.uuid());
            }
        }
    }
}
