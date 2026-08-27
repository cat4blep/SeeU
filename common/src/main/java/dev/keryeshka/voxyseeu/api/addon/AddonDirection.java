package dev.keryeshka.voxyseeu.api.addon;

/** Defines which side may send addon data after negotiation. */
public enum AddonDirection {
    /** The server may send data to the client. */
    CLIENTBOUND,
    /** Both the client and the server may send data. */
    BIDIRECTIONAL
}
