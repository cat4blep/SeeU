package dev.keryeshka.voxyseeu.common.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.keryeshka.voxyseeu.common.ConfigMigrations;
import dev.keryeshka.voxyseeu.common.SharedDefaults;
import dev.keryeshka.voxyseeu.common.UpdateIntervalMigration;
import dev.keryeshka.voxyseeu.common.protocol.PacketCodec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class SeeUServerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CURRENT_CONFIG_VERSION = 4;

    public int configVersion = CURRENT_CONFIG_VERSION;
    public boolean enabled = true;
    public int updateIntervalTicks = SharedDefaults.DEFAULT_UPDATE_INTERVAL_TICKS;
    public int maxRenderDistanceBlocks = SharedDefaults.DEFAULT_MAX_RENDER_DISTANCE_BLOCKS;
    public int minimumProxyDistanceBlocks = SharedDefaults.DEFAULT_MIN_PROXY_DISTANCE_BLOCKS;
    public boolean sendSpectators = SharedDefaults.DEFAULT_SEND_SPECTATORS;

    public static SeeUServerConfig load(Path configDirectory) {
        Path configPath = configDirectory.resolve("seeu-server.json");
        Path legacyConfigPath = configDirectory.resolve("voxyseeu-server.json");
        try {
            Files.createDirectories(configDirectory);
            if (Files.notExists(configPath) && Files.exists(legacyConfigPath)) {
                Files.copy(legacyConfigPath, configPath, StandardCopyOption.REPLACE_EXISTING);
            }

            SeeUServerConfig config = Files.exists(configPath)
                    ? GSON.fromJson(Files.readString(configPath), SeeUServerConfig.class)
                    : new SeeUServerConfig();
            if (config == null) {
                throw new IllegalStateException("Config is empty: " + configPath);
            }
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
            Files.writeString(configPath, GSON.toJson(config));
            return config;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load config: " + configPath, exception);
        }
    }
}
