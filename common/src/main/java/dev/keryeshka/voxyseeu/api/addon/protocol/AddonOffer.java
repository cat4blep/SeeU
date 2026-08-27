package dev.keryeshka.voxyseeu.api.addon.protocol;

import dev.keryeshka.voxyseeu.api.addon.AddonDescriptor;
import dev.keryeshka.voxyseeu.api.addon.AddonLimits;

import java.util.Arrays;
import java.util.Objects;

/** One client addon offer and its addon-owned hello payload. */
public final class AddonOffer {
    private final AddonDescriptor descriptor;
    private final byte[] helloData;

    public AddonOffer(AddonDescriptor descriptor, byte[] helloData) {
        this(descriptor, helloData, true);
    }

    static AddonOffer fromCodec(AddonDescriptor descriptor, byte[] helloData) {
        return new AddonOffer(descriptor, helloData, false);
    }

    private AddonOffer(AddonDescriptor descriptor, byte[] helloData, boolean copyHelloData) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(helloData, "helloData");
        if (helloData.length > AddonLimits.MAX_HANDSHAKE_BYTES) {
            throw new IllegalArgumentException("Addon hello exceeds 32 KiB");
        }
        this.helloData = copyHelloData ? Arrays.copyOf(helloData, helloData.length) : helloData;
    }

    public AddonDescriptor descriptor() {
        return descriptor;
    }

    public byte[] helloData() {
        return Arrays.copyOf(helloData, helloData.length);
    }

    /** Returns the hello length without allocating a defensive copy. */
    public int helloDataLength() {
        return helloData.length;
    }

    byte[] helloDataForCodec() {
        return helloData;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof AddonOffer offer
                && descriptor.equals(offer.descriptor)
                && Arrays.equals(helloData, offer.helloData);
    }

    @Override
    public int hashCode() {
        return 31 * descriptor.hashCode() + Arrays.hashCode(helloData);
    }

    @Override
    public String toString() {
        return "AddonOffer[descriptor=" + descriptor
                + ", helloDataLength=" + helloData.length + ']';
    }
}
