package dev.keryeshka.seeu.extra.config;

import dev.keryeshka.seeu.extra.protocol.ExtraProtocol;

import java.util.Set;

public record ExtraServerSettings(
        int configVersion,
        SelectionMode mode,
        Set<String> types,
        Set<String> namespaces,
        Set<String> excludedTypes,
        Set<String> excludedNamespaces,
        int maximumDistanceBlocks,
        int minimumDistanceBlocks,
        int entityCap,
        int updateIntervalTicks
) {
    public static final int CONFIG_VERSION = 1;
    public static final int DEFAULT_MAXIMUM_DISTANCE_BLOCKS = 8192;
    public static final int DEFAULT_MINIMUM_DISTANCE_BLOCKS = 0;
    public static final int DEFAULT_ENTITY_CAP = 128;
    public static final int DEFAULT_UPDATE_INTERVAL_TICKS = 4;

    public ExtraServerSettings {
        configVersion = CONFIG_VERSION;
        mode = mode == null ? SelectionMode.DISABLED : mode;
        types = EntitySelector.normalizeTypeIds(types);
        namespaces = EntitySelector.normalizeNamespaces(namespaces);
        excludedTypes = EntitySelector.normalizeTypeIds(excludedTypes);
        excludedNamespaces = EntitySelector.normalizeNamespaces(excludedNamespaces);
        maximumDistanceBlocks = clamp(maximumDistanceBlocks, 0, ExtraProtocol.MAXIMUM_DISTANCE_BLOCKS);
        minimumDistanceBlocks = clamp(minimumDistanceBlocks, 0, maximumDistanceBlocks);
        entityCap = clamp(entityCap, 1, ExtraProtocol.MAX_SNAPSHOTS);
        updateIntervalTicks = clamp(updateIntervalTicks, 1, ExtraProtocol.MAX_UPDATE_INTERVAL_TICKS);
    }

    public static ExtraServerSettings defaults() {
        return new ExtraServerSettings(
                CONFIG_VERSION,
                SelectionMode.DISABLED,
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                DEFAULT_MAXIMUM_DISTANCE_BLOCKS,
                DEFAULT_MINIMUM_DISTANCE_BLOCKS,
                DEFAULT_ENTITY_CAP,
                DEFAULT_UPDATE_INTERVAL_TICKS
        );
    }

    public EntitySelector selector() {
        return new EntitySelector(mode, types, namespaces, excludedTypes, excludedNamespaces);
    }

    public boolean enabled() {
        return mode != SelectionMode.DISABLED;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }
}
