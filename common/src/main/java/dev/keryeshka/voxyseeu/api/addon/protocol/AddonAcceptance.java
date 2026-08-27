package dev.keryeshka.voxyseeu.api.addon.protocol;

import dev.keryeshka.voxyseeu.api.addon.AddonDecision;
import dev.keryeshka.voxyseeu.api.addon.AddonDescriptor;

import java.util.Objects;

/** Server decision for one offered addon. */
public record AddonAcceptance(AddonDescriptor descriptor, AddonDecision decision) {
    public AddonAcceptance {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(decision, "decision");
    }
}
