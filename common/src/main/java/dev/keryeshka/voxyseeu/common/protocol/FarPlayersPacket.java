package dev.keryeshka.voxyseeu.common.protocol;

import java.util.List;

public record FarPlayersPacket(
        String dimensionKey,
        long sequence,
        int updateIntervalTicks,
        List<FarPlayerSnapshot> players
) {
}
