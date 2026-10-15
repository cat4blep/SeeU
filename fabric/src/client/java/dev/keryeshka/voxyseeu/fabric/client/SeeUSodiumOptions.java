package dev.keryeshka.voxyseeu.fabric.client;

import dev.keryeshka.voxyseeu.common.SharedDefaults;
import dev.keryeshka.voxyseeu.fabric.client.config.VoxySeeUClientConfig;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.option.SteppedValidator;
import net.caffeinemc.mods.sodium.api.config.structure.BooleanOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.IntegerOptionBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

final class SeeUSodiumOptions {
    private static final ResourceLocation ENABLED = id("render_enabled");
    private static final ResourceLocation RENDER_DISTANCE = id("render_distance");
    private static final ResourceLocation SHARE_SELF = id("share_self");

    private SeeUSodiumOptions() {
    }

    static void register(ConfigBuilder builder, VoxySeeUClientConfig config, Runnable saveAndSendHello) {
        VoxySeeUClientConfig defaults = new VoxySeeUClientConfig();
        // Sodium flushes each shared handler once, after applying all changed bindings.
        StorageEventHandler storage = saveAndSendHello::run;

        builder.registerOwnModOptions()
                .setNonTintedIcon(ResourceLocation.parse("seeu:icon.png"))
                .addPage(builder.createOptionPage()
                .setName(text("page"))
                .addOptionGroup(builder.createOptionGroup()
                        .setName(text("group.rendering"))
                        .addOption(toggle(builder, "render_enabled", value -> config.enabled = value,
                                () -> config.enabled, defaults.enabled, storage))
                        .addOption(distance(builder, "render_distance", value -> config.maximumRenderDistanceBlocks = value,
                                () -> config.maximumRenderDistanceBlocks, defaults.maximumRenderDistanceBlocks, storage)
                                .setValidator(new ConfiguredRange(() -> config.maximumRenderDistanceBlocks))
                                .setEnabledProvider(state -> state.readBooleanOption(ENABLED), ENABLED))
                        // Keep the maximum before its dependents so Discard restores their bounds first.
                        .addOption(distance(builder, "min_distance", value -> config.minimumProxyDistanceBlocks = value,
                                () -> config.minimumProxyDistanceBlocks, defaults.minimumProxyDistanceBlocks, storage)
                                .setRangeProvider(state -> new ClampedRange(state.readIntOption(RENDER_DISTANCE)), RENDER_DISTANCE)
                                .setEnabledProvider(state -> state.readBooleanOption(ENABLED), ENABLED))
                        .addOption(distance(builder, "walk_animation_distance", value -> config.maximumAnimationDistanceBlocks = value,
                                () -> config.maximumAnimationDistanceBlocks, defaults.maximumAnimationDistanceBlocks, storage)
                                .setRangeProvider(state -> new ClampedRange(state.readIntOption(RENDER_DISTANCE)), RENDER_DISTANCE)
                                .setDefaultProvider(state -> Math.min(defaults.maximumAnimationDistanceBlocks,
                                        state.readIntOption(RENDER_DISTANCE)), RENDER_DISTANCE)
                                .setEnabledProvider(state -> state.readBooleanOption(ENABLED), ENABLED))
                        .addOption(toggle(builder, "name_tags", value -> config.renderNameTags = value,
                                () -> config.renderNameTags, defaults.renderNameTags, storage)
                                .setEnabledProvider(state -> state.readBooleanOption(ENABLED), ENABLED))
                        .addOption(toggle(builder, "disable_vanilla_fog", value -> config.disableVanillaFog = value,
                                () -> config.disableVanillaFog, defaults.disableVanillaFog, storage)
                                .setEnabledProvider(state -> state.readBooleanOption(ENABLED), ENABLED)))
                .addOptionGroup(builder.createOptionGroup()
                        .setName(text("group.sharing"))
                        .addOption(toggle(builder, "share_self", value -> config.shareSelf = value,
                                () -> config.shareSelf, defaults.shareSelf, storage))
                        .addOption(distance(builder, "share_distance", value -> config.shareMaximumDistanceBlocks = value,
                                () -> config.shareMaximumDistanceBlocks, defaults.shareMaximumDistanceBlocks, storage)
                                .setValidator(new ConfiguredRange(() -> config.shareMaximumDistanceBlocks))
                                .setEnabledProvider(state -> state.readBooleanOption(SHARE_SELF), SHARE_SELF))));
    }

    private static BooleanOptionBuilder toggle(ConfigBuilder builder, String name, Consumer<Boolean> save,
                                                Supplier<Boolean> load, boolean defaultValue, StorageEventHandler storage) {
        return builder.createBooleanOption(id(name))
                .setName(text(name))
                .setTooltip(text(name + ".tooltip"))
                .setStorageHandler(storage)
                .setBinding(save, load)
                .setDefaultValue(defaultValue);
    }

    private static IntegerOptionBuilder distance(ConfigBuilder builder, String name, Consumer<Integer> save,
                                                  Supplier<Integer> load, int defaultValue, StorageEventHandler storage) {
        return builder.createIntegerOption(id(name))
                .setName(text(name))
                .setTooltip(text(name + ".tooltip"))
                .setStorageHandler(storage)
                .setBinding(save, load)
                .setDefaultValue(defaultValue)
                .setValueFormatter(value -> Component.translatable("sodium.seeu.blocks", value));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("seeu", path);
    }

    private static Component text(String key) {
        return Component.translatable("sodium.seeu." + key);
    }

    // Read the live value even before Sodium rebuilds the screen's dependent providers.
    private record ConfiguredRange(IntSupplier current) implements SteppedValidator {
        @Override
        public int min() {
            return 64;
        }

        @Override
        public int max() {
            return Math.max(SharedDefaults.DEFAULT_MAX_RENDER_DISTANCE_BLOCKS, current.getAsInt());
        }

        @Override
        public int step() {
            return 1;
        }
    }

    // Clamp a dependent distance when the render limit shrinks, while keeping Reset's factory defaults.
    record ClampedRange(int max) implements SteppedValidator {
        @Override
        public int min() {
            return 0;
        }

        @Override
        public int step() {
            return 1;
        }

        @Override
        public Integer getValidatedValue(Integer value, Supplier<Integer> defaultValueSupplier) {
            if (value < 0) {
                return 0;
            }
            if (value > max) {
                return max;
            }
            // Sodium compares boxed values by identity when tracking changes.
            return value;
        }
    }
}
