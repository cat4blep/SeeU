package dev.keryeshka.voxyseeu.fabric.client;

import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;

public final class SeeUSodiumConfig implements ConfigEntryPoint {
    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        SeeUSodiumOptions.register(builder, VoxySeeUClient.clientConfig(), VoxySeeUClient::saveClientConfig);
    }
}
