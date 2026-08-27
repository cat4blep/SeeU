package dev.keryeshka.voxyseeu.fabric.client;

import dev.keryeshka.voxyseeu.common.protocol.FarPlayerSnapshot;
import dev.keryeshka.voxyseeu.common.protocol.FarPlayersPacket;
import dev.keryeshka.voxyseeu.common.protocol.PacketSequenceGate;
import dev.keryeshka.voxyseeu.common.protocol.SnapshotInterpolationTiming;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class FarPlayerTracker {
    private final Map<UUID, TrackedFarPlayer> trackedPlayers = new HashMap<>();
    private final PacketSequenceGate sequenceGate = new PacketSequenceGate();
    private final SnapshotInterpolationTiming interpolationTiming = new SnapshotInterpolationTiming();
    private String dimensionKey = "";
    private int generation;
    private boolean receivedPacket;

    public void clear() {
        trackedPlayers.clear();
        dimensionKey = "";
        generation = 0;
        receivedPacket = false;
        sequenceGate.reset();
        interpolationTiming.reset();
    }

    public boolean apply(FarPlayersPacket packet) {
        return apply(packet, System.nanoTime());
    }

    boolean apply(FarPlayersPacket packet, long nowNanos) {
        if (!sequenceGate.accept(packet.sequence())) {
            return false;
        }
        if (receivedPacket && !dimensionKey.equals(packet.dimensionKey())) {
            trackedPlayers.clear();
            generation = 0;
            interpolationTiming.reset();
        }

        long interpolationWindowNanos = interpolationTiming.recordArrival(
                nowNanos,
                packet.updateIntervalTicks()
        );
        int nextGeneration = generation + 1;
        generation = nextGeneration;
        dimensionKey = packet.dimensionKey();
        receivedPacket = true;

        for (FarPlayerSnapshot snapshot : packet.players()) {
            trackedPlayers.compute(snapshot.uuid(), (uuid, current) -> {
                if (current == null) {
                    if (snapshot.metadata() == null) {
                        return null;
                    }
                    return new TrackedFarPlayer(
                            snapshot,
                            nextGeneration,
                            nowNanos,
                            interpolationWindowNanos
                    );
                }
                current.apply(snapshot, nextGeneration, nowNanos, interpolationWindowNanos);
                return current;
            });
        }

        trackedPlayers.entrySet().removeIf(entry -> entry.getValue().generation() != nextGeneration);
        return true;
    }

    String dimensionKey() {
        return dimensionKey;
    }

    Collection<TrackedFarPlayer> players() {
        return trackedPlayers.values();
    }

    public boolean hasReceivedPacket() {
        return receivedPacket;
    }
}
