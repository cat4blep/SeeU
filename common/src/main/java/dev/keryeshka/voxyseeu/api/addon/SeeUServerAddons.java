package dev.keryeshka.voxyseeu.api.addon;

import dev.keryeshka.voxyseeu.api.addon.protocol.AddonAcceptance;
import dev.keryeshka.voxyseeu.api.addon.protocol.AddonAcceptanceList;
import dev.keryeshka.voxyseeu.api.addon.protocol.AddonCloseMessage;
import dev.keryeshka.voxyseeu.api.addon.protocol.AddonControlMessage;
import dev.keryeshka.voxyseeu.api.addon.protocol.AddonEnvelope;
import dev.keryeshka.voxyseeu.api.addon.protocol.AddonOffer;
import dev.keryeshka.voxyseeu.api.addon.protocol.AddonOfferList;
import dev.keryeshka.voxyseeu.api.addon.protocol.AddonRenegotiateMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Server addon registry and per-player session bus.
 *
 * <p>Addons register during loader initialization through {@link #getInstance()}. Registration freezes when the
 * first player transport connects. Loader adapters must invoke lifecycle and receive methods on the server main
 * thread.</p>
 */
public final class SeeUServerAddons {
    private static final SeeUServerAddons INSTANCE = new SeeUServerAddons();
    private static final long INBOUND_WINDOW_NANOS = TimeUnit.SECONDS.toNanos(1L);
    private static final int MAX_INBOUND_MESSAGES_PER_WINDOW = 128;
    private static final int MAX_INBOUND_CONTROL_MESSAGES_PER_WINDOW = 8;
    private static final long MAX_INBOUND_BYTES_PER_WINDOW = (long) AddonLimits.MAX_ENCODED_CONTROL_BYTES
            + 2L * AddonLimits.MAX_ENCODED_DATA_BYTES;

    private final Map<String, Registration> registrations = new LinkedHashMap<>();
    private final Map<UUID, Connection> connections = new LinkedHashMap<>();
    private boolean frozen;

    public static SeeUServerAddons getInstance() {
        return INSTANCE;
    }

    public void register(AddonDescriptor descriptor, ServerAddonEndpoint endpoint) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(endpoint, "endpoint");
        if (frozen) {
            throw new IllegalStateException("Addon registration is frozen for this game process");
        }
        if (registrations.size() >= AddonLimits.MAX_OFFERS) {
            throw new IllegalStateException("SeeU supports at most " + AddonLimits.MAX_OFFERS + " registered addons");
        }
        Registration previous = registrations.putIfAbsent(descriptor.id(), new Registration(descriptor, endpoint));
        if (previous != null) {
            throw new IllegalStateException("Addon is already registered: " + descriptor.id());
        }
    }

    /** Freezes registration and attaches the transport for one player. */
    public void connect(UUID playerId, AddonTransport transport) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(transport, "transport");
        frozen = true;
        Connection previous = connections.putIfAbsent(playerId, new Connection(playerId, transport));
        if (previous != null) {
            throw new IllegalStateException("Player addon bus is already connected: " + playerId);
        }
    }

    /** Receives a decoded client control message on the server main thread. */
    public void receiveControl(UUID playerId, AddonControlMessage message) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(message, "message");
        Connection connection = connections.get(playerId);
        if (connection == null || !connection.connected) {
            return;
        }
        if (!connection.reserveInbound(encodedControlBytes(message), true)) {
            protocolViolation(connection);
            return;
        }
        if (message instanceof AddonOfferList offers) {
            if (connection.generation == Long.MAX_VALUE
                    || offers.generation() != connection.generation + 1L) {
                protocolViolation(connection);
                return;
            }
            receiveOffers(connection, offers);
        } else if (message instanceof AddonCloseMessage close) {
            if (close.generation() != connection.generation) {
                protocolViolation(connection);
                return;
            }
            receiveClose(connection, close);
        } else {
            protocolViolation(connection);
        }
    }

    /** Receives a decoded client data message on the server main thread. */
    public void receiveData(UUID playerId, AddonEnvelope envelope) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(envelope, "envelope");
        Connection connection = connections.get(playerId);
        if (connection == null || !connection.connected) {
            return;
        }
        if (!connection.reserveInbound(encodedDataBytes(envelope), false)) {
            protocolViolation(connection);
            return;
        }
        if (envelope.generation() != connection.generation) {
            protocolViolation(connection);
            return;
        }
        ServerSession session = connection.sessions.get(envelope.addonId());
        if (session == null || !session.open) {
            return;
        }
        if (session.descriptor.direction() != AddonDirection.BIDIRECTIONAL
                || envelope.payloadLength() > session.descriptor.maximumPayloadBytes()) {
            protocolViolation(connection);
            return;
        }
        byte[] payload = envelope.payload();
        try {
            session.registration.endpoint.onData(session, payload);
        } catch (RuntimeException | LinkageError exception) {
            session.fail();
        }
    }

    /** Quarantines a connected peer after a loader-level addon packet violation. */
    public void rejectProtocolViolation(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        Connection connection = connections.get(playerId);
        if (connection != null) {
            protocolViolation(connection);
        }
    }

    /** Requests a fresh client offer list without reconnecting the player. */
    public void renegotiate(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        Connection connection = connections.get(playerId);
        if (connection == null || !connection.connected) {
            throw new IllegalStateException("Player addon bus is not connected: " + playerId);
        }
        connection.transport.sendControl(new AddonRenegotiateMessage(connection.generation));
    }

    /** Ends one player connection and closes every accepted addon exactly once. */
    public void disconnect(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        Connection connection = connections.remove(playerId);
        if (connection == null) {
            return;
        }
        connection.connected = false;
        closeAll(connection, AddonCloseReason.DISCONNECTED);
    }

    /** Alias for loader adapters whose player lifecycle uses close terminology. */
    public void close(UUID playerId) {
        disconnect(playerId);
    }

    public boolean isFrozen() {
        return frozen;
    }

    public boolean isConnected(UUID playerId) {
        return connections.containsKey(playerId);
    }

    public boolean isAccepted(UUID playerId, String addonId) {
        Connection connection = connections.get(playerId);
        if (connection == null) {
            return false;
        }
        ServerSession session = connection.sessions.get(addonId);
        return session != null && session.open;
    }

    private void receiveOffers(Connection connection, AddonOfferList message) {
        closeAll(connection, AddonCloseReason.RENEGOTIATED);
        connection.generation = message.generation();

        List<AddonAcceptance> acceptances = new ArrayList<>(message.offers().size());
        List<ServerSession> opened = new ArrayList<>();
        for (AddonOffer offer : message.offers()) {
            Registration registration = registrations.get(offer.descriptor().id());
            if (registration == null || !matches(offer.descriptor(), registration.descriptor)) {
                acceptances.add(new AddonAcceptance(offer.descriptor(), AddonDecision.reject()));
                continue;
            }

            AddonDescriptor negotiated = negotiatedDescriptor(offer.descriptor(), registration.descriptor);
            AddonDecision decision;
            try {
                decision = Objects.requireNonNull(
                        registration.endpoint.accept(connection.peer, offer.helloData()),
                        "Server addon endpoint returned a null decision"
                );
            } catch (RuntimeException | LinkageError exception) {
                decision = AddonDecision.reject();
            }

            acceptances.add(new AddonAcceptance(negotiated, decision));
            if (decision.accepted()) {
                ServerSession session = new ServerSession(connection, registration, negotiated, message.generation());
                connection.sessions.put(negotiated.id(), session);
                opened.add(session);
            }
        }

        // Acceptance must precede data produced by onOpen.
        connection.transport.sendControl(new AddonAcceptanceList(message.generation(), acceptances));
        for (ServerSession session : opened) {
            if (!session.open) {
                continue;
            }
            try {
                session.registration.endpoint.onOpen(session);
            } catch (RuntimeException | LinkageError exception) {
                session.fail();
            }
        }
    }

    private void receiveClose(Connection connection, AddonCloseMessage message) {
        ServerSession session = connection.sessions.get(message.addonId());
        if (session != null) {
            session.closeInternal(AddonCloseReason.REMOTE_CLOSE, false);
        }
    }

    private static boolean matches(AddonDescriptor offered, AddonDescriptor registered) {
        return offered.id().equals(registered.id())
                && offered.protocolVersion() == registered.protocolVersion()
                && offered.direction() == registered.direction();
    }

    private static AddonDescriptor negotiatedDescriptor(AddonDescriptor first, AddonDescriptor second) {
        return new AddonDescriptor(
                first.id(),
                first.protocolVersion(),
                first.direction(),
                Math.min(first.maximumPayloadBytes(), second.maximumPayloadBytes())
        );
    }

    private static int encodedControlBytes(AddonControlMessage message) {
        long bytes = 32L;
        if (message instanceof AddonOfferList offers) {
            for (AddonOffer offer : offers.offers()) {
                bytes += offer.descriptor().id().length() + offer.helloDataLength() + 32L;
            }
        } else if (message instanceof AddonCloseMessage close) {
            bytes += close.addonId().length();
        }
        return (int) Math.min(bytes, Integer.MAX_VALUE);
    }

    private static int encodedDataBytes(AddonEnvelope envelope) {
        return envelope.payloadLength() + envelope.addonId().length() + 32;
    }

    private static void protocolViolation(Connection connection) {
        if (!connection.connected) {
            return;
        }
        connection.connected = false;
        closeAll(connection, AddonCloseReason.PROTOCOL_VIOLATION);
        connection.transport.disconnectForProtocolViolation();
    }

    private static void closeAll(Connection connection, AddonCloseReason reason) {
        List<ServerSession> current = List.copyOf(connection.sessions.values());
        for (ServerSession session : current) {
            session.closeInternal(reason, false);
        }
    }

    private record Registration(AddonDescriptor descriptor, ServerAddonEndpoint endpoint) {
    }

    private record Peer(UUID playerId) implements ServerAddonPeer {
    }

    private final class Connection {
        private final UUID playerId;
        private final Peer peer;
        private final AddonTransport transport;
        private final Map<String, ServerSession> sessions = new LinkedHashMap<>();
        private long generation;
        private boolean connected = true;
        private long inboundWindowStartedNanos = System.nanoTime();
        private long inboundBytes;
        private int inboundMessages;
        private int inboundControlMessages;

        private Connection(UUID playerId, AddonTransport transport) {
            this.playerId = playerId;
            this.peer = new Peer(playerId);
            this.transport = transport;
        }

        private boolean reserveInbound(int bytes, boolean control) {
            long nowNanos = System.nanoTime();
            if (nowNanos < inboundWindowStartedNanos
                    || nowNanos - inboundWindowStartedNanos >= INBOUND_WINDOW_NANOS) {
                inboundWindowStartedNanos = nowNanos;
                inboundBytes = 0L;
                inboundMessages = 0;
                inboundControlMessages = 0;
            }

            if (inboundMessages >= MAX_INBOUND_MESSAGES_PER_WINDOW
                    || control && inboundControlMessages >= MAX_INBOUND_CONTROL_MESSAGES_PER_WINDOW
                    || inboundBytes + bytes > MAX_INBOUND_BYTES_PER_WINDOW) {
                return false;
            }
            inboundMessages++;
            if (control) {
                inboundControlMessages++;
            }
            inboundBytes += bytes;
            return true;
        }
    }

    private final class ServerSession implements ServerAddonSession {
        private final Connection connection;
        private final Registration registration;
        private final AddonDescriptor descriptor;
        private final long sessionGeneration;
        private boolean open = true;

        private ServerSession(
                Connection connection,
                Registration registration,
                AddonDescriptor descriptor,
                long sessionGeneration
        ) {
            this.connection = connection;
            this.registration = registration;
            this.descriptor = descriptor;
            this.sessionGeneration = sessionGeneration;
        }

        @Override
        public UUID playerId() {
            return connection.playerId;
        }

        @Override
        public AddonDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void send(byte[] payload) {
            Objects.requireNonNull(payload, "payload");
            if (!open || !connection.connected || sessionGeneration != connection.generation) {
                throw new IllegalStateException("Addon session is closed");
            }
            if (payload.length > descriptor.maximumPayloadBytes()) {
                throw new IllegalArgumentException("Addon payload exceeds the negotiated limit");
            }
            connection.transport.sendData(new AddonEnvelope(sessionGeneration, descriptor.id(), payload));
        }

        @Override
        public void close() {
            closeInternal(AddonCloseReason.LOCAL_CLOSE, true);
        }

        private void fail() {
            closeInternal(AddonCloseReason.ENDPOINT_FAILURE, true);
        }

        private void closeInternal(AddonCloseReason reason, boolean notifyRemote) {
            if (!open) {
                return;
            }
            open = false;
            connection.sessions.remove(descriptor.id(), this);
            try {
                if (notifyRemote && connection.connected && sessionGeneration == connection.generation) {
                    connection.transport.sendControl(
                            new AddonCloseMessage(sessionGeneration, descriptor.id(), reason)
                    );
                }
            } finally {
                try {
                    registration.endpoint.onClose(this, reason);
                } catch (RuntimeException | LinkageError ignored) {
                    // Closing is terminal and must remain exactly once even if the endpoint fails again.
                }
            }
        }
    }
}
