package dev.keryeshka.voxyseeu.common.client;

import dev.keryeshka.voxyseeu.common.protocol.FarPlayerSnapshot;
import dev.keryeshka.voxyseeu.common.protocol.FarPlayersPacket;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class FarPlayerTracker {
    private final Map<UUID, TrackedFarPlayer> trackedPlayers = new HashMap<>();
    private String dimensionKey = "";
    private int generation;
    private boolean receivedPacket;

    public void clear() {
        trackedPlayers.clear();
        dimensionKey = "";
        generation = 0;
        receivedPacket = false;
    }

    public void apply(FarPlayersPacket packet) {
        int nextGeneration = generation + 1;
        generation = nextGeneration;
        dimensionKey = packet.dimensionKey();
        receivedPacket = true;

        for (FarPlayerSnapshot snapshot : packet.players()) {
            trackedPlayers.compute(snapshot.uuid(), (uuid, current) -> {
                if (current == null) {
                    return new TrackedFarPlayer(snapshot, nextGeneration);
                }
                current.apply(snapshot, nextGeneration);
                return current;
            });
        }

        trackedPlayers.entrySet().removeIf(entry -> entry.getValue().generation() != nextGeneration);
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
