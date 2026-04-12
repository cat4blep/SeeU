package dev.keryeshka.voxyseeu.fabric.config;

import dev.keryeshka.voxyseeu.common.SharedDefaults;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public final class VoxySeeUServerConfig {
    private static final int CURRENT_CONFIG_VERSION = 3;
    private static final int LEGACY_GAP_DISTANCE_BLOCKS = 192;

    public int configVersion = CURRENT_CONFIG_VERSION;
    public boolean enabled = true;
    public int updateIntervalTicks = SharedDefaults.DEFAULT_UPDATE_INTERVAL_TICKS;
    public int maxRenderDistanceBlocks = SharedDefaults.DEFAULT_MAX_RENDER_DISTANCE_BLOCKS;
    public int minimumProxyDistanceBlocks = SharedDefaults.DEFAULT_MIN_PROXY_DISTANCE_BLOCKS;
    public boolean sendSpectators = SharedDefaults.DEFAULT_SEND_SPECTATORS;

    public static VoxySeeUServerConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("voxyseeu-server.json");
        VoxySeeUServerConfig config = JsonConfigIO.load(path, VoxySeeUServerConfig.class, VoxySeeUServerConfig::new);
        if (config.configVersion < CURRENT_CONFIG_VERSION
                && config.minimumProxyDistanceBlocks == LEGACY_GAP_DISTANCE_BLOCKS) {
            config.minimumProxyDistanceBlocks = SharedDefaults.DEFAULT_MIN_PROXY_DISTANCE_BLOCKS;
        }
        config.configVersion = CURRENT_CONFIG_VERSION;
        config.updateIntervalTicks = Math.max(1, config.updateIntervalTicks);
        config.maxRenderDistanceBlocks = Math.max(64, config.maxRenderDistanceBlocks);
        config.minimumProxyDistanceBlocks = Math.max(0, config.minimumProxyDistanceBlocks);
        JsonConfigIO.save(path, config);
        return config;
    }
}
