package dev.keryeshka.voxyseeu.common.protocol;

public record ClientHelloPacket(
        int protocolVersion,
        boolean enabled,
        int maximumRenderDistanceBlocks,
        int minimumProxyDistanceBlocks,
        boolean renderNameTags
) {
}
