package dev.keryeshka.seeu.extra.server;

import dev.keryeshka.seeu.extra.protocol.ClientOffer;
import dev.keryeshka.seeu.extra.protocol.ExtraPacketCodec;
import dev.keryeshka.voxyseeu.api.addon.AddonCloseReason;
import dev.keryeshka.voxyseeu.api.addon.AddonDecision;
import dev.keryeshka.voxyseeu.api.addon.ServerAddonEndpoint;
import dev.keryeshka.voxyseeu.api.addon.ServerAddonPeer;
import dev.keryeshka.voxyseeu.api.addon.ServerAddonSession;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SeeUExtraServerEndpoint implements ServerAddonEndpoint {
    private final ExtraEntityBroadcaster broadcaster;
    private final Map<UUID, ClientOffer> pendingOffers = new HashMap<>();
    private final Map<UUID, OpenSession> sessions = new HashMap<>();

    public SeeUExtraServerEndpoint(SeeUExtraServerConfig config) {
        this.broadcaster = new ExtraEntityBroadcaster(config.settings());
    }

    @Override
    public AddonDecision accept(ServerAddonPeer peer, byte[] helloData) {
        try {
            ClientOffer offer = ExtraPacketCodec.decodeClientOffer(helloData);
            pendingOffers.put(peer.playerId(), offer);
            return AddonDecision.accept();
        } catch (IllegalArgumentException exception) {
            pendingOffers.remove(peer.playerId());
            return AddonDecision.reject();
        }
    }

    @Override
    public void onOpen(ServerAddonSession session) {
        ClientOffer offer = pendingOffers.remove(session.playerId());
        if (offer == null) {
            session.close();
            return;
        }
        OpenSession previous = sessions.put(session.playerId(), new OpenSession(session, offer));
        if (previous != null && previous.session().isOpen()) {
            previous.session().close();
        }
    }

    @Override
    public void onClose(ServerAddonSession session, AddonCloseReason reason) {
        pendingOffers.remove(session.playerId());
        sessions.computeIfPresent(session.playerId(), (uuid, current) ->
                current.session() == session ? null : current
        );
    }

    public void tick(MinecraftServer server) {
        if (!broadcaster.isEnabled()) {
            return;
        }
        sessions.entrySet().removeIf(entry -> !entry.getValue().session().isOpen());
        if (sessions.isEmpty()) {
            return;
        }
        List<ExtraViewerSession> viewers = new ArrayList<>(sessions.size());
        for (OpenSession open : sessions.values()) {
            if (!open.session().isOpen()) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(open.session().playerId());
            if (player != null) {
                viewers.add(new Viewer(open.session(), open.offer(), player));
            }
        }
        broadcaster.tick(server, viewers);
    }

    private record OpenSession(ServerAddonSession session, ClientOffer offer) {
    }

    private record Viewer(
            ServerAddonSession session,
            ClientOffer offer,
            ServerPlayer player
    ) implements ExtraViewerSession {
        @Override
        public void send(dev.keryeshka.seeu.extra.protocol.EntitySnapshotPacket packet) {
            try {
                session.send(ExtraPacketCodec.encodeSnapshotPacket(packet));
            } catch (RuntimeException | LinkageError failure) {
                if (session.isOpen()) {
                    session.close();
                }
                throw new IllegalStateException("SeeU Extra session send failed", failure);
            }
        }
    }
}
