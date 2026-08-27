package dev.keryeshka.seeu.extra.client;

import dev.keryeshka.seeu.extra.protocol.ExtraPacketCodec;
import dev.keryeshka.voxyseeu.api.addon.AddonCloseReason;
import dev.keryeshka.voxyseeu.api.addon.ClientAddonEndpoint;
import dev.keryeshka.voxyseeu.api.addon.ClientAddonSession;

public final class SeeUExtraClientEndpoint implements ClientAddonEndpoint {
    private final ExtraEntityTracker tracker;
    private final ExtraEntityRenderer renderer;

    public SeeUExtraClientEndpoint(ExtraEntityTracker tracker, ExtraEntityRenderer renderer) {
        this.tracker = tracker;
        this.renderer = renderer;
    }

    @Override
    public void onOpen(ClientAddonSession session, byte[] acknowledgementData) {
        clear();
        if (acknowledgementData.length != 0) {
            session.close();
        }
    }

    @Override
    public void onData(ClientAddonSession session, byte[] payload) {
        try {
            tracker.apply(ExtraPacketCodec.decodeSnapshotPacket(payload));
        } catch (IllegalArgumentException exception) {
            session.close();
        }
    }

    @Override
    public void onClose(ClientAddonSession session, AddonCloseReason reason) {
        clear();
    }

    public void clear() {
        tracker.clear();
        renderer.clear();
    }
}
