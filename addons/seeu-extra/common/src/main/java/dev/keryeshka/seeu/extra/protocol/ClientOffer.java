package dev.keryeshka.seeu.extra.protocol;

public record ClientOffer(
        int protocolVersion,
        boolean enabled,
        int maximumDistanceBlocks,
        int minimumDistanceBlocks
) {
    public ClientOffer {
        if (protocolVersion != ExtraProtocol.VERSION) {
            throw new IllegalArgumentException("Unsupported protocol version: " + protocolVersion);
        }
        if (maximumDistanceBlocks < 0 || maximumDistanceBlocks > ExtraProtocol.MAXIMUM_DISTANCE_BLOCKS) {
            throw new IllegalArgumentException("Maximum distance is outside the supported range");
        }
        if (minimumDistanceBlocks < 0 || minimumDistanceBlocks > maximumDistanceBlocks) {
            throw new IllegalArgumentException("Minimum distance exceeds the maximum distance");
        }
    }
}
