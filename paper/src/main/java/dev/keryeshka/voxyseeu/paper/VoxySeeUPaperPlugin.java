package dev.keryeshka.voxyseeu.paper;

import dev.keryeshka.voxyseeu.common.SharedDefaults;
import dev.keryeshka.voxyseeu.common.protocol.ProtocolConstants;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class VoxySeeUPaperPlugin extends JavaPlugin implements PluginMessageListener, Listener {
    private static final int CURRENT_CONFIG_VERSION = 3;
    private static final int LEGACY_GAP_DISTANCE_BLOCKS = 192;

    private PaperFarPlayerService service;

    @Override
    public void onEnable() {
        migrateLegacyDataFolder();
        saveDefaultConfig();
        migrateLegacyConfig();

        this.service = new PaperFarPlayerService(this);

        Bukkit.getMessenger().registerIncomingPluginChannel(this, ProtocolConstants.HELLO_CHANNEL, this);
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, ProtocolConstants.PLAYERS_CHANNEL);
        Bukkit.getPluginManager().registerEvents(this, this);

        int interval = Math.max(1, getConfig().getInt("update-interval-ticks", SharedDefaults.DEFAULT_UPDATE_INTERVAL_TICKS));
        Bukkit.getScheduler().runTaskTimer(this, service::broadcast, interval, interval);
    }

    @Override
    public void onDisable() {
        Bukkit.getMessenger().unregisterIncomingPluginChannel(this);
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(this);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!ProtocolConstants.HELLO_CHANNEL.equals(channel)) {
            return;
        }
        service.handleHello(player, message);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.remove(event.getPlayer());
    }

    private void migrateLegacyConfig() {
        int configVersion = getConfig().getInt("config-version", 1);
        int minimumProxyDistance = getConfig().getInt(
                "minimum-proxy-distance-blocks",
                LEGACY_GAP_DISTANCE_BLOCKS
        );

        if (configVersion < CURRENT_CONFIG_VERSION && minimumProxyDistance == LEGACY_GAP_DISTANCE_BLOCKS) {
            getLogger().info("Migrating legacy minimum-proxy-distance-blocks from 192 to 0 to remove the handoff gap.");
            getConfig().set("minimum-proxy-distance-blocks", SharedDefaults.DEFAULT_MIN_PROXY_DISTANCE_BLOCKS);
        }

        getConfig().set("config-version", CURRENT_CONFIG_VERSION);
        saveConfig();
    }

    private void migrateLegacyDataFolder() {
        Path currentFolder = getDataFolder().toPath();
        Path legacyFolder = currentFolder.resolveSibling("VoxySeeU");
        Path currentConfig = currentFolder.resolve("config.yml");
        Path legacyConfig = legacyFolder.resolve("config.yml");
        if (Files.exists(currentConfig) || !Files.exists(legacyConfig)) {
            return;
        }
        try {
            Files.createDirectories(currentFolder);
            Files.copy(legacyConfig, currentConfig, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            getLogger().warning("Failed to migrate legacy VoxySeeU config into SeeU folder: " + exception.getMessage());
        }
    }
}
