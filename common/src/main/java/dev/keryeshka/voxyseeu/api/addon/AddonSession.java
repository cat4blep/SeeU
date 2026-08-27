package dev.keryeshka.voxyseeu.api.addon;

/** A negotiated addon channel with bounded message sending. */
public interface AddonSession {
    AddonDescriptor descriptor();

    boolean isOpen();

    /**
     * Sends one opaque addon message.
     *
     * @throws IllegalArgumentException if the negotiated per-message limit is exceeded
     * @throws IllegalStateException if the session is closed or its direction forbids sending
     */
    void send(byte[] payload);

    /** Closes this addon without affecting SeeU or any other addon. */
    void close();
}
