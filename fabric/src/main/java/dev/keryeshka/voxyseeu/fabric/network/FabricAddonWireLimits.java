package dev.keryeshka.voxyseeu.fabric.network;

import dev.keryeshka.voxyseeu.api.addon.AddonLimits;

public final class FabricAddonWireLimits {
    public static final int CONTROL_BYTES = AddonLimits.MAX_ENCODED_CONTROL_BYTES;
    public static final int DATA_BYTES = AddonLimits.MAX_ENCODED_DATA_BYTES;
    public static final int FRAGMENT_BYTES = 24 * 1024;

    private FabricAddonWireLimits() {
    }
}
