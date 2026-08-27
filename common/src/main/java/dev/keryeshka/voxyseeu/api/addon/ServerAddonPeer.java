package dev.keryeshka.voxyseeu.api.addon;

import java.util.UUID;

/** The server-side identity of the player offering an addon. */
public interface ServerAddonPeer {
    UUID playerId();
}
