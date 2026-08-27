package dev.keryeshka.voxyseeu.api.addon.protocol;

/** Marker for addon negotiation and lifecycle messages. */
public sealed interface AddonControlMessage permits
        AddonOfferList,
        AddonAcceptanceList,
        AddonCloseMessage,
        AddonRenegotiateMessage {
}
