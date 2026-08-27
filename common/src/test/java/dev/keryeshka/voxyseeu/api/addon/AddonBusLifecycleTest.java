package dev.keryeshka.voxyseeu.api.addon;

import dev.keryeshka.voxyseeu.api.addon.protocol.AddonControlMessage;
import dev.keryeshka.voxyseeu.api.addon.protocol.AddonEnvelope;
import dev.keryeshka.voxyseeu.api.addon.protocol.AddonOfferList;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddonBusLifecycleTest {
    private static final UUID PLAYER_ID = UUID.fromString("4f574ca9-a32a-456c-a329-cb3c2695ef32");

    @Test
    void rejectsDuplicateRegistrationAndFreezesAtConnect() {
        SeeUClientAddons client = new SeeUClientAddons();
        AddonDescriptor descriptor = descriptor("test_addon", AddonDirection.CLIENTBOUND, 64);
        client.register(descriptor, new byte[0], new ClientAddonEndpoint() {
        });
        assertThrows(IllegalStateException.class,
                () -> client.register(descriptor, new byte[0], new ClientAddonEndpoint() {
                }));

        client.connect(new RecordingTransport());
        assertTrue(client.isFrozen());
        assertThrows(IllegalStateException.class,
                () -> client.register(descriptor("late", AddonDirection.CLIENTBOUND, 64), new byte[0],
                        new ClientAddonEndpoint() {
                        }));

        SeeUServerAddons server = new SeeUServerAddons();
        server.register(descriptor, acceptingServer());
        server.connect(PLAYER_ID, new RecordingTransport());
        assertThrows(IllegalStateException.class, () -> server.register(descriptor, acceptingServer()));
    }

    @Test
    void negotiatesHelloAckAndBidirectionalDataWithEffectiveBound() {
        SeeUClientAddons client = new SeeUClientAddons();
        SeeUServerAddons server = new SeeUServerAddons();
        AddonDescriptor clientDescriptor = descriptor("test_addon", AddonDirection.BIDIRECTIONAL, 128);
        AddonDescriptor serverDescriptor = descriptor("test_addon", AddonDirection.BIDIRECTIONAL, 64);
        AtomicReference<ClientAddonSession> clientSession = new AtomicReference<>();
        AtomicReference<ServerAddonSession> serverSession = new AtomicReference<>();
        List<byte[]> clientReceived = new ArrayList<>();
        List<byte[]> serverReceived = new ArrayList<>();

        client.register(clientDescriptor, new byte[]{1, 2}, new ClientAddonEndpoint() {
            @Override
            public void onOpen(ClientAddonSession session, byte[] acknowledgementData) {
                assertArrayEquals(new byte[]{3, 4}, acknowledgementData);
                clientSession.set(session);
            }

            @Override
            public void onData(ClientAddonSession session, byte[] payload) {
                clientReceived.add(payload);
            }
        });
        server.register(serverDescriptor, new ServerAddonEndpoint() {
            @Override
            public AddonDecision accept(ServerAddonPeer peer, byte[] helloData) {
                assertEquals(PLAYER_ID, peer.playerId());
                assertArrayEquals(new byte[]{1, 2}, helloData);
                return AddonDecision.accept(new byte[]{3, 4});
            }

            @Override
            public void onOpen(ServerAddonSession session) {
                serverSession.set(session);
            }

            @Override
            public void onData(ServerAddonSession session, byte[] payload) {
                serverReceived.add(payload);
            }
        });

        Bridge bridge = new Bridge(client, server);
        bridge.connect();
        assertTrue(client.isAccepted("test_addon"));
        assertTrue(server.isAccepted(PLAYER_ID, "test_addon"));
        assertNotNull(clientSession.get());
        assertNotNull(serverSession.get());
        assertEquals(64, clientSession.get().descriptor().maximumPayloadBytes());
        assertEquals(64, serverSession.get().descriptor().maximumPayloadBytes());

        clientSession.get().send(new byte[]{5});
        serverSession.get().send(new byte[]{6});
        assertArrayEquals(new byte[]{5}, serverReceived.getFirst());
        assertArrayEquals(new byte[]{6}, clientReceived.getFirst());
        assertThrows(IllegalArgumentException.class, () -> clientSession.get().send(new byte[65]));
    }

    @Test
    void ignoresClientSideStaleAndUnknownDataAndRejectsDirectionSend() {
        SeeUClientAddons client = new SeeUClientAddons();
        SeeUServerAddons server = new SeeUServerAddons();
        AddonDescriptor descriptor = descriptor("test_addon", AddonDirection.CLIENTBOUND, 64);
        AtomicReference<ClientAddonSession> clientSession = new AtomicReference<>();
        AtomicInteger clientData = new AtomicInteger();
        AtomicInteger serverData = new AtomicInteger();
        client.register(descriptor, new byte[0], new ClientAddonEndpoint() {
            @Override
            public void onOpen(ClientAddonSession session, byte[] acknowledgementData) {
                clientSession.set(session);
            }

            @Override
            public void onData(ClientAddonSession session, byte[] payload) {
                clientData.incrementAndGet();
            }
        });
        server.register(descriptor, new ServerAddonEndpoint() {
            @Override
            public AddonDecision accept(ServerAddonPeer peer, byte[] helloData) {
                return AddonDecision.accept();
            }

            @Override
            public void onData(ServerAddonSession session, byte[] payload) {
                serverData.incrementAndGet();
            }
        });
        Bridge bridge = new Bridge(client, server);
        bridge.connect();

        client.receiveData(new AddonEnvelope(2, "test_addon", new byte[]{1}));
        client.receiveData(new AddonEnvelope(1, "unknown", new byte[]{1}));
        assertEquals(0, clientData.get());
        assertEquals(0, serverData.get());
        assertThrows(IllegalStateException.class, () -> clientSession.get().send(new byte[]{1}));
    }

    @Test
    void exactVersionAndDirectionMustMatch() {
        SeeUClientAddons client = new SeeUClientAddons();
        SeeUServerAddons server = new SeeUServerAddons();
        client.register(new AddonDescriptor("versioned", 2, AddonDirection.CLIENTBOUND, 64), new byte[0],
                new ClientAddonEndpoint() {
                });
        client.register(new AddonDescriptor("directed", 1, AddonDirection.BIDIRECTIONAL, 64), new byte[0],
                new ClientAddonEndpoint() {
                });
        server.register(new AddonDescriptor("versioned", 1, AddonDirection.CLIENTBOUND, 64), acceptingServer());
        server.register(new AddonDescriptor("directed", 1, AddonDirection.CLIENTBOUND, 64), acceptingServer());
        new Bridge(client, server).connect();
        assertFalse(client.isAccepted("versioned"));
        assertFalse(client.isAccepted("directed"));
        assertFalse(server.isAccepted(PLAYER_ID, "versioned"));
        assertFalse(server.isAccepted(PLAYER_ID, "directed"));
    }

    @Test
    void endpointFailureClosesOnlyThatAddon() {
        SeeUClientAddons client = new SeeUClientAddons();
        SeeUServerAddons server = new SeeUServerAddons();
        AddonDescriptor broken = descriptor("broken", AddonDirection.BIDIRECTIONAL, 64);
        AddonDescriptor healthy = descriptor("healthy", AddonDirection.BIDIRECTIONAL, 64);
        AtomicReference<ClientAddonSession> brokenClient = new AtomicReference<>();
        AtomicReference<ClientAddonSession> healthyClient = new AtomicReference<>();
        AtomicInteger healthyMessages = new AtomicInteger();
        AtomicInteger brokenServerCloses = new AtomicInteger();
        AtomicInteger brokenClientCloses = new AtomicInteger();

        client.register(broken, new byte[0], endpointCapturing(brokenClient, brokenClientCloses));
        client.register(healthy, new byte[0], endpointCapturing(healthyClient, new AtomicInteger()));
        server.register(broken, new ServerAddonEndpoint() {
            @Override
            public AddonDecision accept(ServerAddonPeer peer, byte[] helloData) {
                return AddonDecision.accept();
            }

            @Override
            public void onData(ServerAddonSession session, byte[] payload) {
                throw new LinkageError("addon linkage bug");
            }

            @Override
            public void onClose(ServerAddonSession session, AddonCloseReason reason) {
                brokenServerCloses.incrementAndGet();
            }
        });
        server.register(healthy, new ServerAddonEndpoint() {
            @Override
            public AddonDecision accept(ServerAddonPeer peer, byte[] helloData) {
                return AddonDecision.accept();
            }

            @Override
            public void onData(ServerAddonSession session, byte[] payload) {
                healthyMessages.incrementAndGet();
            }
        });
        new Bridge(client, server).connect();

        brokenClient.get().send(new byte[]{1});
        assertFalse(server.isAccepted(PLAYER_ID, "broken"));
        assertFalse(client.isAccepted("broken"));
        assertTrue(server.isAccepted(PLAYER_ID, "healthy"));
        assertTrue(client.isAccepted("healthy"));
        healthyClient.get().send(new byte[]{2});
        assertEquals(1, healthyMessages.get());
        assertEquals(1, brokenServerCloses.get());
        assertEquals(1, brokenClientCloses.get());
    }

    @Test
    void reconnectStartsNegotiationAtGenerationOne() {
        SeeUClientAddons client = new SeeUClientAddons();
        List<Long> generations = new ArrayList<>();
        AddonTransport transport = new AddonTransport() {
            @Override
            public void sendControl(AddonControlMessage message) {
                if (message instanceof AddonOfferList offers) {
                    generations.add(offers.generation());
                }
            }

            @Override
            public void sendData(AddonEnvelope envelope) {
            }
        };

        client.connect(transport);
        client.disconnect();
        client.connect(transport);

        assertEquals(List.of(1L, 1L), generations);
    }

    @Test
    void disconnectCallbacksRunExactlyOnce() {
        SeeUClientAddons client = new SeeUClientAddons();
        SeeUServerAddons server = new SeeUServerAddons();
        AddonDescriptor descriptor = descriptor("test_addon", AddonDirection.CLIENTBOUND, 64);
        AtomicInteger clientCloses = new AtomicInteger();
        AtomicInteger serverCloses = new AtomicInteger();
        client.register(descriptor, new byte[0], endpointCapturing(new AtomicReference<>(), clientCloses));
        server.register(descriptor, new ServerAddonEndpoint() {
            @Override
            public AddonDecision accept(ServerAddonPeer peer, byte[] helloData) {
                return AddonDecision.accept();
            }

            @Override
            public void onClose(ServerAddonSession session, AddonCloseReason reason) {
                assertEquals(AddonCloseReason.DISCONNECTED, reason);
                serverCloses.incrementAndGet();
            }
        });
        new Bridge(client, server).connect();

        client.disconnect();
        client.disconnect();
        server.disconnect(PLAYER_ID);
        server.disconnect(PLAYER_ID);
        assertEquals(1, clientCloses.get());
        assertEquals(1, serverCloses.get());
    }

    @Test
    void eitherSideCanRequestRenegotiation() {
        SeeUClientAddons client = new SeeUClientAddons();
        SeeUServerAddons server = new SeeUServerAddons();
        AddonDescriptor descriptor = descriptor("test_addon", AddonDirection.CLIENTBOUND, 64);
        AtomicInteger clientOpens = new AtomicInteger();
        AtomicInteger serverOpens = new AtomicInteger();
        AtomicInteger clientCloses = new AtomicInteger();
        AtomicInteger serverCloses = new AtomicInteger();
        client.register(descriptor, new byte[0], new ClientAddonEndpoint() {
            @Override
            public void onOpen(ClientAddonSession session, byte[] acknowledgementData) {
                clientOpens.incrementAndGet();
            }

            @Override
            public void onClose(ClientAddonSession session, AddonCloseReason reason) {
                assertEquals(AddonCloseReason.RENEGOTIATED, reason);
                clientCloses.incrementAndGet();
            }
        });
        server.register(descriptor, new ServerAddonEndpoint() {
            @Override
            public AddonDecision accept(ServerAddonPeer peer, byte[] helloData) {
                return AddonDecision.accept();
            }

            @Override
            public void onOpen(ServerAddonSession session) {
                serverOpens.incrementAndGet();
            }

            @Override
            public void onClose(ServerAddonSession session, AddonCloseReason reason) {
                assertEquals(AddonCloseReason.RENEGOTIATED, reason);
                serverCloses.incrementAndGet();
            }
        });
        Bridge bridge = new Bridge(client, server);
        bridge.connect();

        server.renegotiate(PLAYER_ID);
        client.renegotiate();

        assertEquals(3, clientOpens.get());
        assertEquals(3, serverOpens.get());
        assertEquals(2, clientCloses.get());
        assertEquals(2, serverCloses.get());
        assertTrue(client.isAccepted("test_addon"));
        assertTrue(server.isAccepted(PLAYER_ID, "test_addon"));
    }

    @Test
    void reentrantRenegotiationDuringClientOpenKeepsTheNewestSession() {
        SeeUClientAddons client = new SeeUClientAddons();
        SeeUServerAddons server = new SeeUServerAddons();
        AddonDescriptor descriptor = descriptor("test_addon", AddonDirection.BIDIRECTIONAL, 64);
        AtomicInteger clientOpens = new AtomicInteger();
        AtomicInteger serverMessages = new AtomicInteger();
        AtomicReference<ClientAddonSession> newestSession = new AtomicReference<>();

        client.register(descriptor, new byte[0], new ClientAddonEndpoint() {
            @Override
            public void onOpen(ClientAddonSession session, byte[] acknowledgementData) {
                newestSession.set(session);
                if (clientOpens.incrementAndGet() == 1) {
                    client.renegotiate();
                }
            }
        });
        server.register(descriptor, new ServerAddonEndpoint() {
            @Override
            public AddonDecision accept(ServerAddonPeer peer, byte[] helloData) {
                return AddonDecision.accept();
            }

            @Override
            public void onData(ServerAddonSession session, byte[] payload) {
                serverMessages.incrementAndGet();
            }
        });

        new Bridge(client, server).connect();

        assertEquals(2, clientOpens.get());
        assertTrue(client.isAccepted("test_addon"));
        assertTrue(server.isAccepted(PLAYER_ID, "test_addon"));
        newestSession.get().send(new byte[]{1});
        assertEquals(1, serverMessages.get());
    }

    @Test
    void serverDisconnectsDirectionSpoofsAndClosesAcceptedSessions() {
        SeeUServerAddons server = new SeeUServerAddons();
        AddonDescriptor descriptor = descriptor("clientbound", AddonDirection.CLIENTBOUND, 64);
        AtomicReference<AddonCloseReason> closeReason = new AtomicReference<>();
        RecordingTransport transport = new RecordingTransport();
        server.register(descriptor, new ServerAddonEndpoint() {
            @Override
            public AddonDecision accept(ServerAddonPeer peer, byte[] helloData) {
                return AddonDecision.accept();
            }

            @Override
            public void onClose(ServerAddonSession session, AddonCloseReason reason) {
                closeReason.set(reason);
            }
        });
        server.connect(PLAYER_ID, transport);
        server.receiveControl(PLAYER_ID, new dev.keryeshka.voxyseeu.api.addon.protocol.AddonOfferList(
                1,
                List.of(new dev.keryeshka.voxyseeu.api.addon.protocol.AddonOffer(descriptor, new byte[0]))
        ));
        assertTrue(server.isAccepted(PLAYER_ID, descriptor.id()));

        server.receiveData(PLAYER_ID, new AddonEnvelope(1, descriptor.id(), new byte[]{1}));

        assertEquals(1, transport.protocolDisconnects.get());
        assertFalse(server.isAccepted(PLAYER_ID, descriptor.id()));
        assertEquals(AddonCloseReason.PROTOCOL_VIOLATION, closeReason.get());
    }

    @Test
    void serverBoundsUnknownInboundDataByBytes() {
        SeeUServerAddons server = new SeeUServerAddons();
        RecordingTransport transport = new RecordingTransport();
        server.connect(PLAYER_ID, transport);
        server.receiveControl(
                PLAYER_ID,
                new dev.keryeshka.voxyseeu.api.addon.protocol.AddonOfferList(1, List.of())
        );
        AddonEnvelope maximumEnvelope = new AddonEnvelope(
                1,
                "unknown",
                new byte[AddonLimits.MAX_DATA_BYTES]
        );

        for (int index = 0; index < 5; index++) {
            server.receiveData(PLAYER_ID, maximumEnvelope);
        }

        assertEquals(1, transport.protocolDisconnects.get());
    }

    private static ClientAddonEndpoint endpointCapturing(
            AtomicReference<ClientAddonSession> sessionReference,
            AtomicInteger closes
    ) {
        return new ClientAddonEndpoint() {
            @Override
            public void onOpen(ClientAddonSession session, byte[] acknowledgementData) {
                sessionReference.set(session);
            }

            @Override
            public void onClose(ClientAddonSession session, AddonCloseReason reason) {
                closes.incrementAndGet();
            }
        };
    }

    private static ServerAddonEndpoint acceptingServer() {
        return (peer, helloData) -> AddonDecision.accept();
    }

    private static AddonDescriptor descriptor(String id, AddonDirection direction, int maximumPayloadBytes) {
        return new AddonDescriptor(id, 1, direction, maximumPayloadBytes);
    }

    private static final class RecordingTransport implements AddonTransport {
        private final AtomicInteger protocolDisconnects = new AtomicInteger();

        @Override
        public void sendControl(AddonControlMessage message) {
        }

        @Override
        public void sendData(AddonEnvelope envelope) {
        }

        @Override
        public void disconnectForProtocolViolation() {
            protocolDisconnects.incrementAndGet();
        }
    }

    private static final class Bridge {
        private final SeeUClientAddons client;
        private final SeeUServerAddons server;

        private Bridge(SeeUClientAddons client, SeeUServerAddons server) {
            this.client = client;
            this.server = server;
        }

        private void connect() {
            server.connect(PLAYER_ID, serverTransport());
            client.connect(clientTransport());
        }

        private AddonTransport clientTransport() {
            return new AddonTransport() {
                @Override
                public void sendControl(AddonControlMessage message) {
                    server.receiveControl(PLAYER_ID, message);
                }

                @Override
                public void sendData(AddonEnvelope envelope) {
                    server.receiveData(PLAYER_ID, envelope);
                }
            };
        }

        private AddonTransport serverTransport() {
            return new AddonTransport() {
                @Override
                public void sendControl(AddonControlMessage message) {
                    client.receiveControl(message);
                }

                @Override
                public void sendData(AddonEnvelope envelope) {
                    client.receiveData(envelope);
                }
            };
        }
    }
}
