package dev.keryeshka.voxyseeu.neoforge.client;

import dev.keryeshka.voxyseeu.neoforge.client.config.VoxySeeUClientConfig;
import dev.keryeshka.voxyseeu.common.SharedDefaults;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.option.SteppedValidator;
import net.caffeinemc.mods.sodium.api.config.structure.BooleanOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.IntegerOptionBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

final class SeeUSodiumOptions {
    private static final Identifier ENABLED = id("render_enabled");
    private static final Identifier MAX_DISTANCE = id("render_distance");
    private static final Identifier SHARE_SELF = id("share_self");

    private SeeUSodiumOptions() {
    }

    static void register(ConfigBuilder builder, VoxySeeUClientConfig config, Runnable saveAndSendHello) {
        VoxySeeUClientConfig defaults = new VoxySeeUClientConfig();
        StorageEventHandler storage = saveAndSendHello::run;

        builder.registerOwnModOptions()
                .setNonTintedIcon(Identifier.parse("seeu:icon.png"))
                .addPage(builder.createOptionPage()
                        .setName(text("page"))
                        .addOptionGroup(builder.createOptionGroup()
                                .setName(text("group.rendering"))
                                .addOption(toggle(builder, "render_enabled", defaults.enabled, storage,
                                        value -> config.enabled = value, () -> config.enabled))
                                .addOption(distance(builder, "render_distance", defaults.maximumRenderDistanceBlocks, storage,
                                        value -> config.maximumRenderDistanceBlocks = value, () -> config.maximumRenderDistanceBlocks)
                                        .setValidator(new ConfiguredRange(() -> config.maximumRenderDistanceBlocks,
                                                SharedDefaults.DEFAULT_MAX_RENDER_DISTANCE_BLOCKS))
                                        .setEnabledProvider(state -> state.readBooleanOption(ENABLED), ENABLED))
                                .addOption(distance(builder, "min_distance", defaults.minimumProxyDistanceBlocks, storage,
                                        value -> config.minimumProxyDistanceBlocks = value, () -> config.minimumProxyDistanceBlocks)
                                        .setRangeProvider(state -> new ClampedRange(state.readIntOption(MAX_DISTANCE)), MAX_DISTANCE)
                                        .setEnabledProvider(state -> state.readBooleanOption(ENABLED), ENABLED))
                                .addOption(distance(builder, "walk_animation_distance", defaults.maximumAnimationDistanceBlocks, storage,
                                        value -> config.maximumAnimationDistanceBlocks = value, () -> config.maximumAnimationDistanceBlocks)
                                        .setRangeProvider(state -> new ClampedRange(state.readIntOption(MAX_DISTANCE)), MAX_DISTANCE)
                                        .setDefaultProvider(state -> Math.min(defaults.maximumAnimationDistanceBlocks,
                                                state.readIntOption(MAX_DISTANCE)), MAX_DISTANCE)
                                        .setEnabledProvider(state -> state.readBooleanOption(ENABLED), ENABLED))
                                .addOption(toggle(builder, "name_tags", defaults.renderNameTags, storage,
                                        value -> config.renderNameTags = value, () -> config.renderNameTags)
                                        .setEnabledProvider(state -> state.readBooleanOption(ENABLED), ENABLED))
                                .addOption(toggle(builder, "disable_vanilla_fog", defaults.disableVanillaFog, storage,
                                        value -> config.disableVanillaFog = value, () -> config.disableVanillaFog)
                                        .setEnabledProvider(state -> state.readBooleanOption(ENABLED), ENABLED)))
                        .addOptionGroup(builder.createOptionGroup()
                                .setName(text("group.sharing"))
                                .addOption(toggle(builder, "share_self", defaults.shareSelf, storage,
                                        value -> config.shareSelf = value, () -> config.shareSelf))
                                .addOption(distance(builder, "share_distance", defaults.shareMaximumDistanceBlocks, storage,
                                        value -> config.shareMaximumDistanceBlocks = value, () -> config.shareMaximumDistanceBlocks)
                                        .setValidator(new ConfiguredRange(() -> config.shareMaximumDistanceBlocks,
                                                SharedDefaults.DEFAULT_SHARE_MAX_DISTANCE_BLOCKS))
                                        .setEnabledProvider(state -> state.readBooleanOption(SHARE_SELF), SHARE_SELF))));
    }

    private static BooleanOptionBuilder toggle(
            ConfigBuilder builder, String name, boolean defaultValue, StorageEventHandler storage,
            Consumer<Boolean> save, Supplier<Boolean> load
    ) {
        return builder.createBooleanOption(id(name))
                .setName(text(name))
                .setTooltip(text(name + ".tooltip"))
                .setStorageHandler(storage)
                .setBinding(save, load)
                .setDefaultValue(defaultValue);
    }

    private static IntegerOptionBuilder distance(
            ConfigBuilder builder, String name, int defaultValue, StorageEventHandler storage,
            Consumer<Integer> save, Supplier<Integer> load
    ) {
        return builder.createIntegerOption(id(name))
                .setName(text(name))
                .setTooltip(text(name + ".tooltip"))
                .setStorageHandler(storage)
                .setBinding(save, load)
                .setDefaultValue(defaultValue)
                .setValueFormatter(value -> Component.translatable("sodium.seeu.blocks", value));
    }

    private static Identifier id(String name) {
        return Identifier.parse("seeu:" + name);
    }

    private static Component text(String name) {
        return Component.translatable("sodium.seeu." + name);
    }

    private record ConfiguredRange(IntSupplier currentValue, int defaultMaximum) implements SteppedValidator {
        @Override
        public int min() {
            return 64;
        }

        @Override
        public int max() {
            return Math.max(defaultMaximum, currentValue.getAsInt());
        }

        @Override
        public int step() {
            return 1;
        }
    }

    private record ClampedRange(int max) implements SteppedValidator {
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
            if (isValueValid(value)) {
                return value;
            }
            return Math.max(0, Math.min(value, max));
        }
    }
}
