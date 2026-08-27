package dev.keryeshka.seeu.extra;

import dev.keryeshka.seeu.extra.protocol.ExtraProtocol;
import dev.keryeshka.voxyseeu.api.addon.AddonDescriptor;
import dev.keryeshka.voxyseeu.api.addon.AddonDirection;

public final class SeeUExtra {
    public static final String MOD_ID = "seeu_extra";
    public static final String MOD_NAME = "SeeU Extra";
    public static final AddonDescriptor DESCRIPTOR = new AddonDescriptor(
            MOD_ID,
            ExtraProtocol.VERSION,
            AddonDirection.CLIENTBOUND,
            ExtraProtocol.MAX_PACKET_BYTES
    );

    private SeeUExtra() {
    }
}
