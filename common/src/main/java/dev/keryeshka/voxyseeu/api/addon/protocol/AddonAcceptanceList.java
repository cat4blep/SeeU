package dev.keryeshka.voxyseeu.api.addon.protocol;

import dev.keryeshka.voxyseeu.api.addon.AddonLimits;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Server-to-client addon decisions for one negotiation generation. */
public record AddonAcceptanceList(
        long generation,
        List<AddonAcceptance> acceptances
) implements AddonControlMessage {
    public AddonAcceptanceList {
        if (generation <= 0) {
            throw new IllegalArgumentException("Addon negotiation generation must be positive");
        }
        Objects.requireNonNull(acceptances, "acceptances");
        if (acceptances.size() > AddonLimits.MAX_OFFERS) {
            throw new IllegalArgumentException("Too many addon acceptances");
        }
        acceptances = List.copyOf(acceptances);
        Set<String> ids = new HashSet<>();
        for (AddonAcceptance acceptance : acceptances) {
            Objects.requireNonNull(acceptance, "acceptance");
            if (!ids.add(acceptance.descriptor().id())) {
                throw new IllegalArgumentException("Duplicate addon acceptance: " + acceptance.descriptor().id());
            }
        }
    }
}
