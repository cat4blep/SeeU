package dev.keryeshka.voxyseeu.api.addon;

/** Describes why an accepted addon session ended. */
public enum AddonCloseReason {
    LOCAL_CLOSE,
    REMOTE_CLOSE,
    DISCONNECTED,
    RENEGOTIATED,
    ENDPOINT_FAILURE,
    PROTOCOL_VIOLATION
}
