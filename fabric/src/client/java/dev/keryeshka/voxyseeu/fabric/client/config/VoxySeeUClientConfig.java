package dev.keryeshka.voxyseeu.fabric.client.config;

import dev.keryeshka.voxyseeu.common.SharedDefaults;
import dev.keryeshka.voxyseeu.fabric.config.JsonConfigIO;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public final class VoxySeeUClientConfig {
    private static final int CURRENT_CONFIG_VERSION = 3;
    private static final int LEGACY_GAP_DISTANCE_BLOCKS = 192;

    public int configVersion = CURRENT_CONFIG_VERSION;
    public boolean enabled = true;
    public int maximumRenderDistanceBlocks = SharedDefaults.DEFAULT_MAX_RENDER_DISTANCE_BLOCKS;
    public int minimumProxyDistanceBlocks = SharedDefaults.DEFAULT_MIN_PROXY_DISTANCE_BLOCKS;
    public boolean renderNameTags = SharedDefaults.DEFAULT_RENDER_NAME_TAGS;

    public static VoxySeeUClientConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("voxyseeu-client.json");
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
}
