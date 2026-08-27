package dev.keryeshka.voxyseeu.api.addon.protocol;

import dev.keryeshka.voxyseeu.api.addon.AddonCloseReason;
import dev.keryeshka.voxyseeu.api.addon.AddonDescriptor;

import java.util.Objects;

/** Closes one accepted addon without affecting the connection or other addons. */
public record AddonCloseMessage(
        long generation,
        String addonId,
        AddonCloseReason reason
) implements AddonControlMessage {
    public AddonCloseMessage {
        if (generation <= 0) {
            throw new IllegalArgumentException("Addon negotiation generation must be positive");
        }
        Objects.requireNonNull(addonId, "addonId");
        Objects.requireNonNull(reason, "reason");
        if (!AddonDescriptor.isValidId(addonId)) {
            throw new IllegalArgumentException("Invalid addon id: " + addonId);
        }
    }
}
