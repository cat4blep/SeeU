package dev.keryeshka.voxyseeu.fabric.client;

import dev.keryeshka.voxyseeu.common.client.SeeUSodiumOptions;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;

public final class SeeUSodiumConfig implements ConfigEntryPoint {
    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        SeeUSodiumOptions.register(builder, VoxySeeUClient.getConfig(), VoxySeeUClient::saveConfig);
    }
}
