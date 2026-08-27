package dev.keryeshka.voxyseeu.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMigrationsTest {
    @Test
    void gapDistanceMigrationStopsAtVersionThree() {
        assertEquals(0, ConfigMigrations.minimumProxyDistance(2, 192));
        assertEquals(192, ConfigMigrations.minimumProxyDistance(3, 192));
        assertEquals(191, ConfigMigrations.minimumProxyDistance(2, 191));
    }

    @Test
    void animationDistanceMigrationStopsAtVersionFive() {
        assertEquals(4096, ConfigMigrations.maximumAnimationDistance(4, 0, 4096));
        assertEquals(0, ConfigMigrations.maximumAnimationDistance(5, 0, 4096));
        assertEquals(256, ConfigMigrations.maximumAnimationDistance(4, 256, 4096));
    }

    @Test
    void fogMigrationStopsAtVersionSix() {
        assertEquals(
                SharedDefaults.DEFAULT_DISABLE_VANILLA_FOG,
                ConfigMigrations.disableVanillaFog(5, !SharedDefaults.DEFAULT_DISABLE_VANILLA_FOG)
        );
        assertFalse(ConfigMigrations.disableVanillaFog(6, false));
        assertTrue(ConfigMigrations.disableVanillaFog(6, true));
    }
}
