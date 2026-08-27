package dev.keryeshka.voxyseeu.common.protocol;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class FarPlayerMetadataDelta {
    private final Map<UUID, FarPlayerMetadata> metadataByPlayer = new HashMap<>();
    private final Set<UUID> visiblePlayers = new HashSet<>();
    private String dimensionKey = "";

    public void beginFrame(String nextDimensionKey) {
        Objects.requireNonNull(nextDimensionKey, "nextDimensionKey");
        if (!dimensionKey.equals(nextDimensionKey)) {
            dimensionKey = nextDimensionKey;
            metadataByPlayer.clear();
        }
        visiblePlayers.clear();
    }

    public FarPlayerSnapshot apply(FarPlayerSnapshot snapshotWithMetadata) {
        return apply(snapshotWithMetadata, snapshotWithMetadata.withoutMetadata());
    }

    public FarPlayerSnapshot apply(
            FarPlayerSnapshot snapshotWithMetadata,
            FarPlayerSnapshot snapshotWithoutMetadata
    ) {
        FarPlayerMetadata metadata = Objects.requireNonNull(
                snapshotWithMetadata.metadata(),
                "snapshotWithMetadata.metadata"
        );
        UUID playerId = snapshotWithMetadata.uuid();
        FarPlayerMetadata previousMetadata = metadataByPlayer.put(playerId, metadata);
        visiblePlayers.add(playerId);
        return Objects.equals(previousMetadata, metadata)
                ? snapshotWithoutMetadata
                : snapshotWithMetadata;
    }

    public void endFrame() {
        metadataByPlayer.keySet().retainAll(visiblePlayers);
    }

    public void clear() {
        dimensionKey = "";
        metadataByPlayer.clear();
        visiblePlayers.clear();
    }
}
