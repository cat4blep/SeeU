package dev.keryeshka.seeu.extra.config;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntitySelectorTest {
    @Test
    void disabledModeSelectsNothing() {
        EntitySelector selector = new EntitySelector(
                SelectionMode.DISABLED,
                Set.of("minecraft:zombie"),
                Set.of("example"),
                Set.of(),
                Set.of()
        );

        assertFalse(selector.selects("minecraft:zombie"));
        assertFalse(selector.selects("example:target"));
    }

    @Test
    void selectedModeCombinesTypesAndNamespaces() {
        EntitySelector selector = new EntitySelector(
                SelectionMode.SELECTED,
                Set.of("minecraft:zombie"),
                Set.of("example"),
                Set.of(),
                Set.of()
        );

        assertTrue(selector.selects("minecraft:zombie"));
        assertTrue(selector.selects("example:target"));
        assertFalse(selector.selects("minecraft:skeleton"));
    }

    @Test
    void exclusionsOverrideAllModeAndSelections() {
        EntitySelector selector = new EntitySelector(
                SelectionMode.ALL,
                Set.of("minecraft:zombie"),
                Set.of("example"),
                Set.of("minecraft:zombie"),
                Set.of("example")
        );

        assertFalse(selector.selects("minecraft:zombie"));
        assertFalse(selector.selects("example:target"));
        assertTrue(selector.selects("minecraft:skeleton"));
    }

    @Test
    void invalidAndDuplicateEntriesAreRemoved() {
        EntitySelector selector = new EntitySelector(
                SelectionMode.SELECTED,
                Set.of(" minecraft:zombie ", "INVALID"),
                Set.of(" example ", "Bad Namespace"),
                Set.of(),
                Set.of()
        );

        assertTrue(selector.selects("minecraft:zombie"));
        assertTrue(selector.selects("example:target"));
        assertFalse(selector.selects("INVALID"));
    }
}
