package dev.keryeshka.voxyseeu.fabric.client;

import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.minecraft.resources.Identifier;

public final class SeeUSodiumConfig implements ConfigEntryPoint {
    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        if (!supportsOptionsApi()) {
            return;
        }
        SeeUSodiumOptions.register(builder, VoxySeeUClient.clientConfig(), VoxySeeUClient::saveClientConfig);
    }

    private static boolean supportsOptionsApi() {
        ClassLoader loader = SeeUSodiumConfig.class.getClassLoader();
        try {
            Class.forName("net.caffeinemc.mods.sodium.api.config.option.SteppedValidator", false, loader);
            Class.forName("net.caffeinemc.mods.sodium.api.config.structure.ModOptionsBuilder", false, loader)
                    .getMethod("setNonTintedIcon", Identifier.class);
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError exception) {
            return false;
        }
    }
}
