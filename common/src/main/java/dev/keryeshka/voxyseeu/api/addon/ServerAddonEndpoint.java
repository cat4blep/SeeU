package dev.keryeshka.voxyseeu.api.addon;

/**
 * Server callbacks for one addon. Loader integrations invoke every callback on the game main thread.
 * Endpoint exceptions are isolated to the affected player/addon session.
 */
public interface ServerAddonEndpoint {
    /** Decides whether to accept a client offer and may return up to 32 KiB of acknowledgement data. */
    AddonDecision accept(ServerAddonPeer peer, byte[] helloData);

    default void onOpen(ServerAddonSession session) {
    }

    default void onData(ServerAddonSession session, byte[] payload) {
    }

    default void onClose(ServerAddonSession session, AddonCloseReason reason) {
    }
}
