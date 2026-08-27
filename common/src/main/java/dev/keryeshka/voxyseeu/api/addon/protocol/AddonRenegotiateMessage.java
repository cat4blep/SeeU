package dev.keryeshka.voxyseeu.api.addon.protocol;

/** Server request for the client to start a fresh addon negotiation. */
public record AddonRenegotiateMessage(long currentGeneration) implements AddonControlMessage {
    public AddonRenegotiateMessage {
        if (currentGeneration < 0) {
            throw new IllegalArgumentException("Current addon generation cannot be negative");
        }
    }
}
