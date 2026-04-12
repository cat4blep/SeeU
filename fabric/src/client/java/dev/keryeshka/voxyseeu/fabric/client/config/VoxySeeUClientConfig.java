package dev.keryeshka.voxyseeu.fabric.client.config;

import dev.keryeshka.voxyseeu.common.SharedDefaults;
import dev.keryeshka.voxyseeu.fabric.config.JsonConfigIO;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class VoxySeeUClientConfig {
    private static final int CURRENT_CONFIG_VERSION = 3;
    private static final int LEGACY_GAP_DISTANCE_BLOCKS = 192;

    public int configVersion = CURRENT_CONFIG_VERSION;
    public boolean enabled = true;
    public int maximumRenderDistanceBlocks = SharedDefaults.DEFAULT_MAX_RENDER_DISTANCE_BLOCKS;
    public int minimumProxyDistanceBlocks = SharedDefaults.DEFAULT_MIN_PROXY_DISTANCE_BLOCKS;
    public boolean renderNameTags = SharedDefaults.DEFAULT_RENDER_NAME_TAGS;

    public static VoxySeeUClientConfig load() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path path = configDir.resolve("seeu-client.json");
        migrateLegacyConfigPath(configDir.resolve("voxyseeu-client.json"), path);
        VoxySeeUClientConfig config = JsonConfigIO.load(path, VoxySeeUClientConfig.class, VoxySeeUClientConfig::new);
        if (config.configVersion < CURRENT_CONFIG_VERSION
                && config.minimumProxyDistanceBlocks == LEGACY_GAP_DISTANCE_BLOCKS) {
            config.minimumProxyDistanceBlocks = SharedDefaults.DEFAULT_MIN_PROXY_DISTANCE_BLOCKS;
        }
        config.configVersion = CURRENT_CONFIG_VERSION;
        config.maximumRenderDistanceBlocks = Math.max(64, config.maximumRenderDistanceBlocks);
        config.minimumProxyDistanceBlocks = Math.max(0, config.minimumProxyDistanceBlocks);
        JsonConfigIO.save(path, config);
        return config;
    }

    private static void migrateLegacyConfigPath(Path legacyPath, Path path) {
        if (Files.exists(path) || !Files.exists(legacyPath)) {
            return;
        }
        try {
            Files.copy(legacyPath, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        }
    }
}
