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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Client addon registry and connection bus.
 *
 * <p>Addons register during loader initialization through {@link #getInstance()}. The loader then installs a
 * transport with {@link #connect(AddonTransport)}. Registration freezes at the first connection. Loader adapters
 * must invoke lifecycle and receive methods on the game main thread.</p>
 */
public final class SeeUClientAddons {
    private static final SeeUClientAddons INSTANCE = new SeeUClientAddons();

    private final Map<String, Registration> registrations = new LinkedHashMap<>();
    private final Map<String, ClientSession> sessions = new LinkedHashMap<>();
    private Map<String, AddonDescriptor> pendingOffers = Map.of();
    private AddonTransport transport;
    private long generation;
    private boolean frozen;
    private boolean awaitingAcceptance;

    public static SeeUClientAddons getInstance() {
        return INSTANCE;
    }

    /** Registers an addon with hello data that is rebuilt for every negotiation. */
    public void register(
            AddonDescriptor descriptor,
            Supplier<byte[]> helloData,
            ClientAddonEndpoint endpoint
    ) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(helloData, "helloData");
        Objects.requireNonNull(endpoint, "endpoint");
        ensureRegistrationOpen();
        if (registrations.size() >= AddonLimits.MAX_OFFERS) {
            throw new IllegalStateException("SeeU supports at most " + AddonLimits.MAX_OFFERS + " registered addons");
        }
        Registration previous = registrations.putIfAbsent(
                descriptor.id(),
                new Registration(descriptor, helloData, endpoint)
        );
        if (previous != null) {
            throw new IllegalStateException("Addon is already registered: " + descriptor.id());
        }
    }

    /** Registers an addon with fixed hello data. */
    public void register(AddonDescriptor descriptor, byte[] helloData, ClientAddonEndpoint endpoint) {
        Objects.requireNonNull(helloData, "helloData");
        if (helloData.length > AddonLimits.MAX_HANDSHAKE_BYTES) {
            throw new IllegalArgumentException("Addon hello exceeds 32 KiB");
        }
        byte[] fixedHello = Arrays.copyOf(helloData, helloData.length);
        register(descriptor, () -> Arrays.copyOf(fixedHello, fixedHello.length), endpoint);
    }

    /** Freezes registration, attaches a connection transport, and sends the first offer list. */
    public void connect(AddonTransport transport) {
        Objects.requireNonNull(transport, "transport");
        if (this.transport != null) {
            throw new IllegalStateException("Client addon bus is already connected");
        }
        frozen = true;
        generation = 0L;
        this.transport = transport;
        startNegotiation();
    }

    /** Receives a decoded server control message on the game main thread. */
    public void receiveControl(AddonControlMessage message) {
        Objects.requireNonNull(message, "message");
        if (transport == null) {
            return;
        }
        if (message instanceof AddonAcceptanceList acceptances) {
            receiveAcceptances(acceptances);
        } else if (message instanceof AddonCloseMessage close) {
            receiveClose(close);
        } else if (message instanceof AddonRenegotiateMessage renegotiate
                && renegotiate.currentGeneration() == generation) {
            startNegotiation();
        }
    }

    /** Receives a decoded server data message on the game main thread. */
    public void receiveData(AddonEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        if (transport == null || envelope.generation() != generation) {
            return;
        }
        ClientSession session = sessions.get(envelope.addonId());
        if (session == null || !session.open || envelope.payloadLength() > session.descriptor.maximumPayloadBytes()) {
            return;
        }
        byte[] payload = envelope.payload();
        try {
            session.registration.endpoint.onData(session, payload);
        } catch (RuntimeException | LinkageError exception) {
            session.fail();
        }
    }

    /** Starts a fresh offer/acceptance exchange on the current connection. */
    public void renegotiate() {
        ensureConnected();
        startNegotiation();
    }

    /** Ends the connection and closes every accepted addon exactly once. */
    public void disconnect() {
        if (transport == null) {
            return;
        }
        transport = null;
        generation = 0L;
        awaitingAcceptance = false;
        pendingOffers = Map.of();
        closeAll(AddonCloseReason.DISCONNECTED);
    }

    /** Alias for loader adapters whose connection lifecycle uses close terminology. */
    public void close() {
        disconnect();
    }

    public boolean isFrozen() {
        return frozen;
    }

    public boolean isConnected() {
        return transport != null;
    }

    public boolean isAccepted(String addonId) {
        ClientSession session = sessions.get(addonId);
        return session != null && session.open;
    }

    private void startNegotiation() {
        ensureConnected();
        closeAll(AddonCloseReason.RENEGOTIATED);
        generation = nextGeneration(generation);

        List<AddonOffer> offers = new ArrayList<>(registrations.size());
        Map<String, AddonDescriptor> offeredDescriptors = new LinkedHashMap<>();
        for (Registration registration : registrations.values()) {
            try {
                AddonOffer offer = new AddonOffer(registration.descriptor, registration.helloData.get());
                offers.add(offer);
                offeredDescriptors.put(registration.descriptor.id(), registration.descriptor);
            } catch (RuntimeException | LinkageError ignored) {
                // A broken hello supplier must not prevent unrelated addons from negotiating.
            }
        }
        pendingOffers = Map.copyOf(offeredDescriptors);
        awaitingAcceptance = true;
        transport.sendControl(new AddonOfferList(generation, offers));
    }

    private void receiveAcceptances(AddonAcceptanceList message) {
        if (!awaitingAcceptance || message.generation() != generation) {
            return;
        }
        long acceptedGeneration = message.generation();
        Map<String, AddonDescriptor> offeredDescriptors = pendingOffers;
        awaitingAcceptance = false;
        for (AddonAcceptance acceptance : message.acceptances()) {
            if (generation != acceptedGeneration || transport == null) {
                return;
            }

            AddonDescriptor offered = offeredDescriptors.get(acceptance.descriptor().id());
            Registration registration = registrations.get(acceptance.descriptor().id());
            if (!acceptance.decision().accepted()
                    || offered == null
                    || registration == null
                    || !matches(offered, acceptance.descriptor())) {
                continue;
            }

            AddonDescriptor negotiated = negotiatedDescriptor(offered, acceptance.descriptor());
            ClientSession session = new ClientSession(registration, negotiated, acceptedGeneration);
            sessions.put(negotiated.id(), session);
            try {
                registration.endpoint.onOpen(session, acceptance.decision().acknowledgementData());
            } catch (RuntimeException | LinkageError exception) {
                if (generation == acceptedGeneration
                        && sessions.get(negotiated.id()) == session) {
                    session.fail();
                }
            }
            if (generation != acceptedGeneration || transport == null) {
                return;
            }
        }
        if (generation == acceptedGeneration) {
            pendingOffers = Map.of();
        }
    }

    private void receiveClose(AddonCloseMessage message) {
        if (message.generation() != generation) {
            return;
        }
        ClientSession session = sessions.get(message.addonId());
        if (session != null) {
            session.closeInternal(AddonCloseReason.REMOTE_CLOSE, false);
        }
    }

    private void closeAll(AddonCloseReason reason) {
        List<ClientSession> current = List.copyOf(sessions.values());
        for (ClientSession session : current) {
            session.closeInternal(reason, false);
        }
    }

    private void ensureRegistrationOpen() {
        if (frozen) {
            throw new IllegalStateException("Addon registration is frozen for this game process");
        }
    }

    private void ensureConnected() {
        if (transport == null) {
            throw new IllegalStateException("Client addon bus is not connected");
        }
    }

    private static boolean matches(AddonDescriptor offered, AddonDescriptor accepted) {
        return offered.id().equals(accepted.id())
                && offered.protocolVersion() == accepted.protocolVersion()
                && offered.direction() == accepted.direction();
    }

    private static AddonDescriptor negotiatedDescriptor(AddonDescriptor first, AddonDescriptor second) {
        return new AddonDescriptor(
                first.id(),
                first.protocolVersion(),
                first.direction(),
                Math.min(first.maximumPayloadBytes(), second.maximumPayloadBytes())
        );
    }

    private static long nextGeneration(long current) {
        if (current == Long.MAX_VALUE) {
            throw new IllegalStateException("Addon negotiation generation exhausted");
        }
        return current + 1;
    }

    private record Registration(
            AddonDescriptor descriptor,
            Supplier<byte[]> helloData,
            ClientAddonEndpoint endpoint
    ) {
    }

    private final class ClientSession implements ClientAddonSession {
        private final Registration registration;
        private final AddonDescriptor descriptor;
        private final long sessionGeneration;
        private boolean open = true;

        private ClientSession(Registration registration, AddonDescriptor descriptor, long sessionGeneration) {
            this.registration = registration;
            this.descriptor = descriptor;
            this.sessionGeneration = sessionGeneration;
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
            if (!open || transport == null || sessionGeneration != generation) {
                throw new IllegalStateException("Addon session is closed");
            }
            if (descriptor.direction() != AddonDirection.BIDIRECTIONAL) {
                throw new IllegalStateException("Clientbound addon sessions cannot send from the client");
            }
            if (payload.length > descriptor.maximumPayloadBytes()) {
                throw new IllegalArgumentException("Addon payload exceeds the negotiated limit");
            }
            transport.sendData(new AddonEnvelope(sessionGeneration, descriptor.id(), payload));
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
            sessions.remove(descriptor.id(), this);
            try {
                if (notifyRemote && transport != null && sessionGeneration == generation) {
                    transport.sendControl(new AddonCloseMessage(sessionGeneration, descriptor.id(), reason));
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
