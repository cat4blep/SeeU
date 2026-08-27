package dev.keryeshka.voxyseeu.common;

public final class ConfigMigrations {
    public static final int LEGACY_GAP_DISTANCE_BLOCKS = 192;

    private static final int GAP_REMOVAL_CONFIG_VERSION = 3;
    private static final int ANIMATION_DISTANCE_CONFIG_VERSION = 5;
    private static final int FOG_SETTING_CONFIG_VERSION = 6;

    private ConfigMigrations() {
    }

    public static int minimumProxyDistance(int storedConfigVersion, int distanceBlocks) {
        if (storedConfigVersion < GAP_REMOVAL_CONFIG_VERSION
                && distanceBlocks == LEGACY_GAP_DISTANCE_BLOCKS) {
            return SharedDefaults.DEFAULT_MIN_PROXY_DISTANCE_BLOCKS;
        }
        return distanceBlocks;
    }

    public static int maximumAnimationDistance(
            int storedConfigVersion,
            int distanceBlocks,
            int maximumRenderDistanceBlocks
    ) {
        if (storedConfigVersion >= ANIMATION_DISTANCE_CONFIG_VERSION || distanceBlocks > 0) {
            return distanceBlocks;
        }
        return Math.max(
                64,
                maximumRenderDistanceBlocks > 0
                        ? maximumRenderDistanceBlocks
                        : SharedDefaults.DEFAULT_MAX_ANIMATION_DISTANCE_BLOCKS
        );
    }

    public static boolean disableVanillaFog(int storedConfigVersion, boolean disabled) {
        return storedConfigVersion < FOG_SETTING_CONFIG_VERSION
                ? SharedDefaults.DEFAULT_DISABLE_VANILLA_FOG
                : disabled;
    }
}
