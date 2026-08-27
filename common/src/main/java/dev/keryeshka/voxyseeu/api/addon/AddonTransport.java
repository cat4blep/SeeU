package dev.keryeshka.voxyseeu.api.addon;

import dev.keryeshka.voxyseeu.api.addon.protocol.AddonControlMessage;
import dev.keryeshka.voxyseeu.api.addon.protocol.AddonEnvelope;

/**
 * Loader-neutral transport installed by SeeU's Fabric or NeoForge adapter.
 * Implementations must preserve message order and schedule inbound bus calls on the game main thread.
 */
public interface AddonTransport {
    void sendControl(AddonControlMessage message);

    void sendData(AddonEnvelope envelope);

    /** Disconnects the underlying peer after invalid or abusive addon traffic. */
    default void disconnectForProtocolViolation() {
    }
}
