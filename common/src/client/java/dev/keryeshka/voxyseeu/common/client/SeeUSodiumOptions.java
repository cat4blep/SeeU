package dev.keryeshka.voxyseeu.common.client;

import dev.keryeshka.voxyseeu.common.SharedDefaults;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.option.SteppedValidator;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** SeeU's optional page in Sodium's Config API. */
public final class SeeUSodiumOptions {
    private static final Identifier ENABLED = id("render_enabled");
    private static final Identifier RENDER_DISTANCE = id("render_distance");
    private static final Identifier SHARE_SELF = id("share_self");

    private SeeUSodiumOptions() {
    }

    public static void register(ConfigBuilder builder, SeeUClientConfig config, Runnable saveConfig) {
        // Sodium flushes a shared handler once, after all changed bindings have been written.
        StorageEventHandler storage = saveConfig::run;
        builder.registerOwnModOptions()
                .setNonTintedIcon(Identifier.parse("seeu:icon.png"))
                .addPage(builder.createOptionPage()
                        .setName(text("page"))
                        .addOptionGroup(builder.createOptionGroup()
                                .setName(text("group.rendering"))
                                .addOption(builder.createBooleanOption(ENABLED)
                                        .setName(text("render_enabled"))
                                        .setTooltip(text("render_enabled.tooltip"))
                                        .setStorageHandler(storage)
                                        .setBinding(value -> config.enabled = value, () -> config.enabled)
                                        .setDefaultValue(true))
                                .addOption(builder.createIntegerOption(RENDER_DISTANCE)
                                        .setName(text("render_distance"))
                                        .setTooltip(text("render_distance.tooltip"))
                                        .setStorageHandler(storage)
                                        .setBinding(value -> config.maximumRenderDistanceBlocks = value,
                                                () -> config.maximumRenderDistanceBlocks)
                                        .setDefaultValue(SharedDefaults.DEFAULT_MAX_RENDER_DISTANCE_BLOCKS)
                                        .setValidator(new ConfiguredRange(() -> config.maximumRenderDistanceBlocks,
                                                SharedDefaults.DEFAULT_MAX_RENDER_DISTANCE_BLOCKS))
                                        .setValueFormatter(SeeUSodiumOptions::blocks)
                                        .setEnabledProvider(state -> state.readBooleanOption(ENABLED), ENABLED))
                                .addOption(builder.createIntegerOption(id("min_distance"))
                                        .setName(text("min_distance"))
                                        .setTooltip(text("min_distance.tooltip"))
                                        .setStorageHandler(storage)
                                        .setBinding(value -> config.minimumProxyDistanceBlocks = value,
                                                () -> config.minimumProxyDistanceBlocks)
                                        .setDefaultValue(SharedDefaults.DEFAULT_MIN_PROXY_DISTANCE_BLOCKS)
                                        .setRangeProvider(state -> new ClampedRange(0,
                                                state.readIntOption(RENDER_DISTANCE)), RENDER_DISTANCE)
                                        .setValueFormatter(SeeUSodiumOptions::blocks)
                                        .setEnabledProvider(state -> state.readBooleanOption(ENABLED), ENABLED))
                                .addOption(builder.createIntegerOption(id("walk_animation_distance"))
                                        .setName(text("walk_animation_distance"))
                                        .setTooltip(text("walk_animation_distance.tooltip"))
                                        .setStorageHandler(storage)
                                        .setBinding(value -> config.maximumAnimationDistanceBlocks = value,
                                                () -> config.maximumAnimationDistanceBlocks)
                                        .setDefaultProvider(state -> Math.min(
                                                SharedDefaults.DEFAULT_MAX_ANIMATION_DISTANCE_BLOCKS,
                                                state.readIntOption(RENDER_DISTANCE)), RENDER_DISTANCE)
                                        .setRangeProvider(state -> new ClampedRange(0,
                                                state.readIntOption(RENDER_DISTANCE)), RENDER_DISTANCE)
                                        .setValueFormatter(SeeUSodiumOptions::blocks)
                                        .setEnabledProvider(state -> state.readBooleanOption(ENABLED), ENABLED))
                                .addOption(builder.createBooleanOption(id("name_tags"))
                                        .setName(text("name_tags"))
                                        .setTooltip(text("name_tags.tooltip"))
                                        .setStorageHandler(storage)
                                        .setBinding(value -> config.renderNameTags = value, () -> config.renderNameTags)
                                        .setDefaultValue(SharedDefaults.DEFAULT_RENDER_NAME_TAGS)
                                        .setEnabledProvider(state -> state.readBooleanOption(ENABLED), ENABLED))
                                .addOption(builder.createBooleanOption(id("disable_vanilla_fog"))
                                        .setName(text("disable_vanilla_fog"))
                                        .setTooltip(text("disable_vanilla_fog.tooltip"))
                                        .setStorageHandler(storage)
                                        .setBinding(value -> config.disableVanillaFog = value, () -> config.disableVanillaFog)
                                        .setDefaultValue(SharedDefaults.DEFAULT_DISABLE_VANILLA_FOG)
                                        .setEnabledProvider(state -> state.readBooleanOption(ENABLED), ENABLED)))
                        .addOptionGroup(builder.createOptionGroup()
                                .setName(text("group.sharing"))
                                .addOption(builder.createBooleanOption(SHARE_SELF)
                                        .setName(text("share_self"))
                                        .setTooltip(text("share_self.tooltip"))
                                        .setStorageHandler(storage)
                                        .setBinding(value -> config.shareSelf = value, () -> config.shareSelf)
                                        .setDefaultValue(SharedDefaults.DEFAULT_SHARE_SELF))
                                .addOption(builder.createIntegerOption(id("share_distance"))
                                        .setName(text("share_distance"))
                                        .setTooltip(text("share_distance.tooltip"))
                                        .setStorageHandler(storage)
                                        .setBinding(value -> config.shareMaximumDistanceBlocks = value,
                                                () -> config.shareMaximumDistanceBlocks)
                                        .setDefaultValue(SharedDefaults.DEFAULT_SHARE_MAX_DISTANCE_BLOCKS)
                                        .setValidator(new ConfiguredRange(() -> config.shareMaximumDistanceBlocks,
                                                SharedDefaults.DEFAULT_SHARE_MAX_DISTANCE_BLOCKS))
                                        .setValueFormatter(SeeUSodiumOptions::blocks)
                                        .setEnabledProvider(state -> state.readBooleanOption(SHARE_SELF), SHARE_SELF))));
    }

    private static Identifier id(String name) {
        return Identifier.fromNamespaceAndPath("seeu", name);
    }

    private static Component text(String name) {
        return Component.translatable("sodium.seeu." + name);
    }

    private static Component blocks(int value) {
        return Component.translatable("sodium.seeu.blocks", value);
    }

    // Sodium reloads bindings before rebuilding the screen, so these bounds must read live values.
    private record ConfiguredRange(IntSupplier current, int defaultMax) implements SteppedValidator {
        @Override
        public int min() {
            return 64;
        }

        @Override
        public int max() {
            return Math.max(defaultMax, current.getAsInt());
        }

        @Override
        public int step() {
            return 1;
        }
    }

    private record ClampedRange(int min, int max) implements SteppedValidator {
        @Override
        public int step() {
            return 1;
        }

        @Override
        public Integer getValidatedValue(Integer value, Supplier<Integer> defaultValueSupplier) {
            // Preserve identity for valid values: Sodium uses it to detect binding corrections.
            if (isValueValid(value)) {
                return value;
            }
            return Math.max(min, Math.min(max, value));
        }
    }
}
