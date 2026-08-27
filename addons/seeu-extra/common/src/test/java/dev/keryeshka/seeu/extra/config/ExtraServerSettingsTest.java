package dev.keryeshka.seeu.extra.config;

import dev.keryeshka.seeu.extra.protocol.ExtraProtocol;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ExtraServerSettingsTest {
    @Test
    void defaultsDisableScanningAndUseRequiredCadenceAndCap() {
        ExtraServerSettings settings = ExtraServerSettings.defaults();

        assertFalse(settings.enabled());
        assertEquals(SelectionMode.DISABLED, settings.mode());
        assertEquals(128, settings.entityCap());
        assertEquals(4, settings.updateIntervalTicks());
    }

    @Test
    void constructorClampsUntrustedConfigValues() {
        ExtraServerSettings settings = new ExtraServerSettings(
                99,
                SelectionMode.ALL,
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Integer.MAX_VALUE,
                -20,
                Integer.MAX_VALUE,
                0
        );

        assertEquals(ExtraServerSettings.CONFIG_VERSION, settings.configVersion());
        assertEquals(ExtraProtocol.MAXIMUM_DISTANCE_BLOCKS, settings.maximumDistanceBlocks());
        assertEquals(0, settings.minimumDistanceBlocks());
        assertEquals(ExtraProtocol.MAX_SNAPSHOTS, settings.entityCap());
        assertEquals(1, settings.updateIntervalTicks());
    }

    @Test
    void minimumDistanceCannotExceedMaximumDistance() {
        ExtraServerSettings settings = new ExtraServerSettings(
                1,
                SelectionMode.ALL,
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                100,
                200,
                32,
                4
        );

        assertEquals(100, settings.minimumDistanceBlocks());
    }
}
