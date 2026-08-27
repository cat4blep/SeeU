package dev.keryeshka.voxyseeu.fabric.config;

import dev.keryeshka.voxyseeu.common.ConfigMigrations;
import dev.keryeshka.voxyseeu.common.SharedDefaults;
import dev.keryeshka.voxyseeu.common.UpdateIntervalMigration;
import dev.keryeshka.voxyseeu.common.protocol.PacketCodec;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class VoxySeeUServerConfig {
    private static final int CURRENT_CONFIG_VERSION = 4;

    public int configVersion = CURRENT_CONFIG_VERSION;
    public boolean enabled = true;
    public int updateIntervalTicks = SharedDefaults.DEFAULT_UPDATE_INTERVAL_TICKS;
    public int maxRenderDistanceBlocks = SharedDefaults.DEFAULT_MAX_RENDER_DISTANCE_BLOCKS;
    public int minimumProxyDistanceBlocks = SharedDefaults.DEFAULT_MIN_PROXY_DISTANCE_BLOCKS;
    public boolean sendSpectators = SharedDefaults.DEFAULT_SEND_SPECTATORS;

    public static VoxySeeUServerConfig load() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path path = configDir.resolve("seeu-server.json");
        migrateLegacyConfigPath(configDir.resolve("voxyseeu-server.json"), path);
        VoxySeeUServerConfig config = JsonConfigIO.load(path, VoxySeeUServerConfig.class, VoxySeeUServerConfig::new);
        config.minimumProxyDistanceBlocks = ConfigMigrations.minimumProxyDistance(
                config.configVersion,
                config.minimumProxyDistanceBlocks
        );
        config.updateIntervalTicks = UpdateIntervalMigration.migrate(
                config.configVersion,
                CURRENT_CONFIG_VERSION,
                config.updateIntervalTicks
        );
        config.configVersion = CURRENT_CONFIG_VERSION;
        config.updateIntervalTicks = Math.min(
                PacketCodec.MAX_UPDATE_INTERVAL_TICKS,
                Math.max(1, config.updateIntervalTicks)
        );
        config.maxRenderDistanceBlocks = Math.max(64, config.maxRenderDistanceBlocks);
        config.minimumProxyDistanceBlocks = Math.max(0, config.minimumProxyDistanceBlocks);
        JsonConfigIO.save(path, config);
        return config;
    }

    private static void migrateLegacyConfigPath(Path legacyPath, Path path) {
        if (Files.exists(path) || !Files.exists(legacyPath)) {
            return;
        }
        try {
            Files.createDirectories(path.getParent());
            Files.copy(legacyPath, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to migrate config: " + legacyPath, exception);
        }
    }
}
