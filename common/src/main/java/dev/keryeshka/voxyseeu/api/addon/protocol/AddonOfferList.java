package dev.keryeshka.voxyseeu.api.addon.protocol;

import dev.keryeshka.voxyseeu.api.addon.AddonLimits;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Client-to-server addon offers for one negotiation generation. */
public record AddonOfferList(long generation, List<AddonOffer> offers) implements AddonControlMessage {
    public AddonOfferList {
        if (generation <= 0) {
            throw new IllegalArgumentException("Addon negotiation generation must be positive");
        }
        Objects.requireNonNull(offers, "offers");
        if (offers.size() > AddonLimits.MAX_OFFERS) {
            throw new IllegalArgumentException("Too many addon offers");
        }
        offers = List.copyOf(offers);
        Set<String> ids = new HashSet<>();
        for (AddonOffer offer : offers) {
            Objects.requireNonNull(offer, "offer");
            if (!ids.add(offer.descriptor().id())) {
                throw new IllegalArgumentException("Duplicate addon offer: " + offer.descriptor().id());
            }
        }
    }
}
