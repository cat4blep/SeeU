package dev.keryeshka.voxyseeu.common.client;

import dev.keryeshka.voxyseeu.common.ConfigMigrations;
import dev.keryeshka.voxyseeu.common.SharedDefaults;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class SeeUClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CURRENT_CONFIG_VERSION = 6;

    public int configVersion = CURRENT_CONFIG_VERSION;
    public boolean enabled = true;
    public int maximumRenderDistanceBlocks = SharedDefaults.DEFAULT_MAX_RENDER_DISTANCE_BLOCKS;
    public int minimumProxyDistanceBlocks = SharedDefaults.DEFAULT_MIN_PROXY_DISTANCE_BLOCKS;
    public int maximumAnimationDistanceBlocks = SharedDefaults.DEFAULT_MAX_ANIMATION_DISTANCE_BLOCKS;
    public boolean renderNameTags = SharedDefaults.DEFAULT_RENDER_NAME_TAGS;
    public boolean disableVanillaFog = SharedDefaults.DEFAULT_DISABLE_VANILLA_FOG;
    public boolean shareSelf = SharedDefaults.DEFAULT_SHARE_SELF;
    public int shareMaximumDistanceBlocks = SharedDefaults.DEFAULT_SHARE_MAX_DISTANCE_BLOCKS;

    private transient Path configPath;

    public static SeeUClientConfig load(Path configDirectory) {
        Path configPath = configDirectory.resolve("seeu-client.json");
        migrateLegacyConfigPath(configDirectory.resolve("voxyseeu-client.json"), configPath);
        SeeUClientConfig config;
        try {
            Files.createDirectories(configDirectory);
            config = Files.exists(configPath)
                    ? GSON.fromJson(Files.readString(configPath), SeeUClientConfig.class)
                    : new SeeUClientConfig();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load config: " + configPath, exception);
        }
        if (config == null) {
            throw new IllegalStateException("Config is empty: " + configPath);
        }
        config.configPath = configPath;
        config.minimumProxyDistanceBlocks = ConfigMigrations.minimumProxyDistance(
                config.configVersion,
                config.minimumProxyDistanceBlocks
        );
        config.maximumAnimationDistanceBlocks = ConfigMigrations.maximumAnimationDistance(
                config.configVersion,
                config.maximumAnimationDistanceBlocks,
                config.maximumRenderDistanceBlocks
        );
        config.disableVanillaFog = ConfigMigrations.disableVanillaFog(
                config.configVersion,
                config.disableVanillaFog
        );
        config.save();
        return config;
    }

    public void save() {
        this.configVersion = CURRENT_CONFIG_VERSION;
        this.maximumRenderDistanceBlocks = Math.max(64, maximumRenderDistanceBlocks);
        this.minimumProxyDistanceBlocks = Math.max(
                0,
                Math.min(minimumProxyDistanceBlocks, maximumRenderDistanceBlocks)
        );
        this.maximumAnimationDistanceBlocks = Math.max(
                0,
                Math.min(maximumAnimationDistanceBlocks, maximumRenderDistanceBlocks)
        );
        this.shareMaximumDistanceBlocks = Math.max(64, shareMaximumDistanceBlocks);
        try {
            Files.writeString(configPath, GSON.toJson(this));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save config: " + configPath, exception);
        }
    }

    public SeeUClientConfig copy() {
        SeeUClientConfig copy = new SeeUClientConfig();
        copy.enabled = this.enabled;
        copy.maximumRenderDistanceBlocks = this.maximumRenderDistanceBlocks;
        copy.minimumProxyDistanceBlocks = this.minimumProxyDistanceBlocks;
        copy.maximumAnimationDistanceBlocks = this.maximumAnimationDistanceBlocks;
        copy.renderNameTags = this.renderNameTags;
        copy.disableVanillaFog = this.disableVanillaFog;
        copy.shareSelf = this.shareSelf;
        copy.shareMaximumDistanceBlocks = this.shareMaximumDistanceBlocks;
        return copy;
    }

    public void copyFrom(SeeUClientConfig other) {
        this.enabled = other.enabled;
        this.maximumRenderDistanceBlocks = other.maximumRenderDistanceBlocks;
        this.minimumProxyDistanceBlocks = other.minimumProxyDistanceBlocks;
        this.maximumAnimationDistanceBlocks = other.maximumAnimationDistanceBlocks;
        this.renderNameTags = other.renderNameTags;
        this.disableVanillaFog = other.disableVanillaFog;
        this.shareSelf = other.shareSelf;
        this.shareMaximumDistanceBlocks = other.shareMaximumDistanceBlocks;
    }

    private static void migrateLegacyConfigPath(Path legacyPath, Path configPath) {
        if (Files.exists(configPath) || !Files.exists(legacyPath)) {
            return;
        }
        try {
            Files.createDirectories(configPath.getParent());
            Files.copy(legacyPath, configPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to migrate config: " + legacyPath, exception);
        }
    }
}
