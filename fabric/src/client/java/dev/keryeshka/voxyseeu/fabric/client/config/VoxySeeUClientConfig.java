package dev.keryeshka.voxyseeu.fabric.client.config;

import dev.keryeshka.voxyseeu.common.SharedDefaults;
import dev.keryeshka.voxyseeu.fabric.config.JsonConfigIO;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class VoxySeeUClientConfig {
    private static final int CURRENT_CONFIG_VERSION = 4;
    private static final int LEGACY_GAP_DISTANCE_BLOCKS = 192;

    public int configVersion = CURRENT_CONFIG_VERSION;
    public boolean enabled = true;
    public int maximumRenderDistanceBlocks = SharedDefaults.DEFAULT_MAX_RENDER_DISTANCE_BLOCKS;
    public int minimumProxyDistanceBlocks = SharedDefaults.DEFAULT_MIN_PROXY_DISTANCE_BLOCKS;
    public boolean renderNameTags = SharedDefaults.DEFAULT_RENDER_NAME_TAGS;
    public boolean shareSelf = SharedDefaults.DEFAULT_SHARE_SELF;
    public int shareMaximumDistanceBlocks = SharedDefaults.DEFAULT_SHARE_MAX_DISTANCE_BLOCKS;

    public static VoxySeeUClientConfig load() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path path = configDir.resolve("seeu-client.json");
        migrateLegacyConfigPath(configDir.resolve("voxyseeu-client.json"), path);
        VoxySeeUClientConfig config = JsonConfigIO.load(path, VoxySeeUClientConfig.class, VoxySeeUClientConfig::new);
        if (config.configVersion < CURRENT_CONFIG_VERSION
                && config.minimumProxyDistanceBlocks == LEGACY_GAP_DISTANCE_BLOCKS) {
            config.minimumProxyDistanceBlocks = SharedDefaults.DEFAULT_MIN_PROXY_DISTANCE_BLOCKS;
        }
        config.clamp();
        config.save();
        return config;
    }

    public void save() {
        this.configVersion = CURRENT_CONFIG_VERSION;
        clamp();
        JsonConfigIO.save(configPath(), this);
    }

    public void clamp() {
        this.configVersion = CURRENT_CONFIG_VERSION;
        this.maximumRenderDistanceBlocks = Math.max(64, maximumRenderDistanceBlocks);
        this.minimumProxyDistanceBlocks = Math.max(0, Math.min(minimumProxyDistanceBlocks, maximumRenderDistanceBlocks));
        this.shareMaximumDistanceBlocks = Math.max(64, shareMaximumDistanceBlocks);
    }

    public VoxySeeUClientConfig copy() {
        VoxySeeUClientConfig copy = new VoxySeeUClientConfig();
        copy.configVersion = this.configVersion;
        copy.enabled = this.enabled;
        copy.maximumRenderDistanceBlocks = this.maximumRenderDistanceBlocks;
        copy.minimumProxyDistanceBlocks = this.minimumProxyDistanceBlocks;
        copy.renderNameTags = this.renderNameTags;
        copy.shareSelf = this.shareSelf;
        copy.shareMaximumDistanceBlocks = this.shareMaximumDistanceBlocks;
        return copy;
    }

    public void copyFrom(VoxySeeUClientConfig other) {
        this.configVersion = other.configVersion;
        this.enabled = other.enabled;
        this.maximumRenderDistanceBlocks = other.maximumRenderDistanceBlocks;
        this.minimumProxyDistanceBlocks = other.minimumProxyDistanceBlocks;
        this.renderNameTags = other.renderNameTags;
        this.shareSelf = other.shareSelf;
        this.shareMaximumDistanceBlocks = other.shareMaximumDistanceBlocks;
        clamp();
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("seeu-client.json");
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
