package dev.keryeshka.voxyseeu.api.addon;

import java.util.Arrays;
import java.util.Objects;

/**
 * A server endpoint's response to a client offer.
 *
 * @param accepted whether the addon session is accepted
 * @param acknowledgementData addon-owned acknowledgement payload, limited to 32 KiB
 */
public record AddonDecision(boolean accepted, byte[] acknowledgementData) {
    public AddonDecision {
        Objects.requireNonNull(acknowledgementData, "acknowledgementData");
        if (acknowledgementData.length > AddonLimits.MAX_HANDSHAKE_BYTES) {
            throw new IllegalArgumentException("Addon acknowledgement exceeds 32 KiB");
        }
        if (!accepted && acknowledgementData.length != 0) {
            throw new IllegalArgumentException("Rejected addon decisions cannot contain acknowledgement data");
        }
        acknowledgementData = Arrays.copyOf(acknowledgementData, acknowledgementData.length);
    }

    @Override
    public byte[] acknowledgementData() {
        return Arrays.copyOf(acknowledgementData, acknowledgementData.length);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof AddonDecision decision
                && accepted == decision.accepted
                && Arrays.equals(acknowledgementData, decision.acknowledgementData);
    }

    @Override
    public int hashCode() {
        return 31 * Boolean.hashCode(accepted) + Arrays.hashCode(acknowledgementData);
    }

    public static AddonDecision accept() {
        return new AddonDecision(true, new byte[0]);
    }

    public static AddonDecision accept(byte[] acknowledgementData) {
        return new AddonDecision(true, acknowledgementData);
    }

    public static AddonDecision reject() {
        return new AddonDecision(false, new byte[0]);
    }
}
