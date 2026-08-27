package dev.keryeshka.seeu.extra.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.keryeshka.seeu.extra.config.ExtraServerSettings;
import dev.keryeshka.seeu.extra.config.SelectionMode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SeeUExtraServerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "seeu-extra-server.json";

    public int configVersion = ExtraServerSettings.CONFIG_VERSION;
    public SelectionMode mode = SelectionMode.DISABLED;
    public List<String> types = new ArrayList<>();
    public List<String> namespaces = new ArrayList<>();
    public List<String> excludedTypes = new ArrayList<>();
    public List<String> excludedNamespaces = new ArrayList<>();
    public int maximumDistanceBlocks = ExtraServerSettings.DEFAULT_MAXIMUM_DISTANCE_BLOCKS;
    public int minimumDistanceBlocks = ExtraServerSettings.DEFAULT_MINIMUM_DISTANCE_BLOCKS;
    public int entityCap = ExtraServerSettings.DEFAULT_ENTITY_CAP;
    public int updateIntervalTicks = ExtraServerSettings.DEFAULT_UPDATE_INTERVAL_TICKS;

    public static SeeUExtraServerConfig load(Path configDirectory) {
        Path configPath = configDirectory.resolve(FILE_NAME);
        try {
            Files.createDirectories(configDirectory);
            SeeUExtraServerConfig config = Files.exists(configPath)
                    ? GSON.fromJson(Files.readString(configPath), SeeUExtraServerConfig.class)
                    : new SeeUExtraServerConfig();
            if (config == null) {
                throw new IllegalStateException("Config is empty: " + configPath);
            }
            config.apply(config.settings());
            Files.writeString(configPath, GSON.toJson(config));
            return config;
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Failed to load config: " + configPath, exception);
        }
    }

    public ExtraServerSettings settings() {
        return new ExtraServerSettings(
                configVersion,
                mode,
                copy(types),
                copy(namespaces),
                copy(excludedTypes),
                copy(excludedNamespaces),
                maximumDistanceBlocks,
                minimumDistanceBlocks,
                entityCap,
                updateIntervalTicks
        );
    }

    private void apply(ExtraServerSettings settings) {
        configVersion = settings.configVersion();
        mode = settings.mode();
        types = new ArrayList<>(settings.types());
        namespaces = new ArrayList<>(settings.namespaces());
        excludedTypes = new ArrayList<>(settings.excludedTypes());
        excludedNamespaces = new ArrayList<>(settings.excludedNamespaces());
        maximumDistanceBlocks = settings.maximumDistanceBlocks();
        minimumDistanceBlocks = settings.minimumDistanceBlocks();
        entityCap = settings.entityCap();
        updateIntervalTicks = settings.updateIntervalTicks();
    }

    private static Set<String> copy(List<String> values) {
        return values == null ? Set.of() : new HashSet<>(values);
    }
}
