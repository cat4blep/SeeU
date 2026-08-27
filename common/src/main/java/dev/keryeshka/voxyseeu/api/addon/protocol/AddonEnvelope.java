package dev.keryeshka.voxyseeu.api.addon.protocol;

import dev.keryeshka.voxyseeu.api.addon.AddonDescriptor;
import dev.keryeshka.voxyseeu.api.addon.AddonLimits;

import java.util.Arrays;
import java.util.Objects;

/** One opaque post-negotiation addon message. */
public final class AddonEnvelope {
    private final long generation;
    private final String addonId;
    private final byte[] payload;

    public AddonEnvelope(long generation, String addonId, byte[] payload) {
        this(generation, addonId, payload, true);
    }

    static AddonEnvelope fromCodec(long generation, String addonId, byte[] payload) {
        return new AddonEnvelope(generation, addonId, payload, false);
    }

    private AddonEnvelope(long generation, String addonId, byte[] payload, boolean copyPayload) {
        if (generation <= 0) {
            throw new IllegalArgumentException("Addon negotiation generation must be positive");
        }
        Objects.requireNonNull(addonId, "addonId");
        Objects.requireNonNull(payload, "payload");
        if (!AddonDescriptor.isValidId(addonId)) {
            throw new IllegalArgumentException("Invalid addon id: " + addonId);
        }
        if (payload.length > AddonLimits.MAX_DATA_BYTES) {
            throw new IllegalArgumentException("Addon data exceeds 1 MiB");
        }
        this.generation = generation;
        this.addonId = addonId;
        this.payload = copyPayload ? Arrays.copyOf(payload, payload.length) : payload;
    }

    public long generation() {
        return generation;
    }

    public String addonId() {
        return addonId;
    }

    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }

    /** Returns the payload length without allocating a defensive copy. */
    public int payloadLength() {
        return payload.length;
    }

    byte[] payloadForCodec() {
        return payload;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof AddonEnvelope envelope
                && generation == envelope.generation
                && addonId.equals(envelope.addonId)
                && Arrays.equals(payload, envelope.payload);
    }

    @Override
    public int hashCode() {
        int result = Long.hashCode(generation);
        result = 31 * result + addonId.hashCode();
        return 31 * result + Arrays.hashCode(payload);
    }

    @Override
    public String toString() {
        return "AddonEnvelope[generation=" + generation
                + ", addonId=" + addonId
                + ", payloadLength=" + payload.length + ']';
    }
}
