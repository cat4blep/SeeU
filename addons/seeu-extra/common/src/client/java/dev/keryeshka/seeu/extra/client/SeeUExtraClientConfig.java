package dev.keryeshka.seeu.extra.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.keryeshka.seeu.extra.config.ExtraServerSettings;
import dev.keryeshka.seeu.extra.protocol.ClientOffer;
import dev.keryeshka.seeu.extra.protocol.ExtraProtocol;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SeeUExtraClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "seeu-extra-client.json";

    public int configVersion = 1;
    public boolean enabled = true;
    public int maximumDistanceBlocks = ExtraServerSettings.DEFAULT_MAXIMUM_DISTANCE_BLOCKS;
    public int minimumDistanceBlocks = ExtraServerSettings.DEFAULT_MINIMUM_DISTANCE_BLOCKS;

    public static SeeUExtraClientConfig load(Path configDirectory) {
        Path configPath = configDirectory.resolve(FILE_NAME);
        try {
            Files.createDirectories(configDirectory);
            SeeUExtraClientConfig config = Files.exists(configPath)
                    ? GSON.fromJson(Files.readString(configPath), SeeUExtraClientConfig.class)
                    : new SeeUExtraClientConfig();
            if (config == null) {
                throw new IllegalStateException("Config is empty: " + configPath);
            }
            config.sanitize();
            Files.writeString(configPath, GSON.toJson(config));
            return config;
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Failed to load config: " + configPath, exception);
        }
    }

    public ClientOffer offer() {
        sanitize();
        return new ClientOffer(
                ExtraProtocol.VERSION,
                enabled,
                maximumDistanceBlocks,
                minimumDistanceBlocks
        );
    }

    private void sanitize() {
        configVersion = 1;
        maximumDistanceBlocks = clamp(
                maximumDistanceBlocks,
                0,
                ExtraProtocol.MAXIMUM_DISTANCE_BLOCKS
        );
        minimumDistanceBlocks = clamp(minimumDistanceBlocks, 0, maximumDistanceBlocks);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }
}
