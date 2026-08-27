package dev.keryeshka.voxyseeu.api.addon;

/**
 * Client callbacks for one addon. Loader integrations invoke every callback on the game main thread.
 * Endpoint exceptions are isolated to this addon's session.
 */
public interface ClientAddonEndpoint {
    default void onOpen(ClientAddonSession session, byte[] acknowledgementData) {
    }

    default void onData(ClientAddonSession session, byte[] payload) {
    }

    default void onClose(ClientAddonSession session, AddonCloseReason reason) {
    }
}
