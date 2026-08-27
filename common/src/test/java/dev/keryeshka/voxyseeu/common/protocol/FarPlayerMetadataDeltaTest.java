package dev.keryeshka.voxyseeu.common.protocol;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class FarPlayerMetadataDeltaTest {
    private static final UUID PLAYER_ID = UUID.fromString("69a2d2f2-747d-412e-b655-a7f377cab818");

    @Test
    void sendsMetadataOnlyOnFirstVisibilityChangeOrReentry() {
        FarPlayerMetadataDelta delta = new FarPlayerMetadataDelta();
        FarPlayerMetadata initial = metadata("Player");
        FarPlayerSnapshot initialSnapshot = snapshot(initial);

        delta.beginFrame("minecraft:overworld");
        assertNotNull(delta.apply(initialSnapshot).metadata());
        delta.endFrame();

        delta.beginFrame("minecraft:overworld");
        assertNull(delta.apply(initialSnapshot).metadata());
        delta.endFrame();

        FarPlayerMetadata changed = metadata("Renamed");
        delta.beginFrame("minecraft:overworld");
        assertEquals(changed, delta.apply(snapshot(changed)).metadata());
        delta.endFrame();

        delta.beginFrame("minecraft:overworld");
        delta.endFrame();
        delta.beginFrame("minecraft:overworld");
        assertNotNull(delta.apply(snapshot(changed)).metadata());
        delta.endFrame();

        delta.beginFrame("minecraft:the_nether");
        assertNotNull(delta.apply(snapshot(changed)).metadata());
    }

    private static FarPlayerMetadata metadata(String name) {
        return new FarPlayerMetadata(
                name,
                FarItemSnapshot.EMPTY,
                FarItemSnapshot.EMPTY,
                FarItemSnapshot.EMPTY,
                FarItemSnapshot.EMPTY,
                FarItemSnapshot.EMPTY,
                FarItemSnapshot.EMPTY
        );
    }

    private static FarPlayerSnapshot snapshot(FarPlayerMetadata metadata) {
        return new FarPlayerSnapshot(
                PLAYER_ID,
                0.0D,
                64.0D,
                0.0D,
                0.0F,
                0.0F,
                0.0F,
                false,
                false,
                false,
                null,
                metadata
        );
    }
}
