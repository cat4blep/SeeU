package dev.keryeshka.voxyseeu.api.addon;

/** Wire limits enforced by the SeeU addon bus. */
public final class AddonLimits {
    public static final int BUS_PROTOCOL_VERSION = 1;
    public static final int MAX_OFFERS = 32;
    public static final int MAX_ADDON_ID_BYTES = 64;
    public static final int MAX_HANDSHAKE_BYTES = 32 * 1024;
    public static final int MAX_DATA_BYTES = 1024 * 1024;
    public static final int MAX_ENCODED_CONTROL_BYTES = MAX_OFFERS
            * (MAX_HANDSHAKE_BYTES + MAX_ADDON_ID_BYTES + 32)
            + 32;
    public static final int MAX_ENCODED_DATA_BYTES = MAX_DATA_BYTES
            + MAX_ADDON_ID_BYTES
            + 32;

    private AddonLimits() {
    }
}
