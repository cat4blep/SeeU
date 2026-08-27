package dev.keryeshka.voxyseeu.neoforge;

import dev.keryeshka.voxyseeu.api.addon.AddonLimits;
import dev.keryeshka.voxyseeu.api.addon.AddonTransport;
import dev.keryeshka.voxyseeu.api.addon.SeeUServerAddons;
import dev.keryeshka.voxyseeu.api.addon.protocol.AddonControlMessage;
import dev.keryeshka.voxyseeu.api.addon.protocol.AddonEnvelope;
import dev.keryeshka.voxyseeu.common.SharedDefaults;
import dev.keryeshka.voxyseeu.common.protocol.ProtocolConstants;
import dev.keryeshka.voxyseeu.common.server.SeeUServerConfig;
import dev.keryeshka.voxyseeu.neoforge.network.ClientHelloPayload;
import dev.keryeshka.voxyseeu.neoforge.network.AddonControlPayload;
import dev.keryeshka.voxyseeu.neoforge.network.AddonDataPayload;
import dev.keryeshka.voxyseeu.neoforge.network.FarPlayersPayload;
import dev.keryeshka.voxyseeu.neoforge.server.NeoForgeFarPlayerService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(ProtocolConstants.MOD_ID)
public final class VoxySeeUNeoForge {
    private final SeeUServerAddons serverAddons = SeeUServerAddons.getInstance();
    private final NeoForgeFarPlayerService service = new NeoForgeFarPlayerService(
            SeeUServerConfig.load(FMLPaths.CONFIGDIR.get())
    );

    public VoxySeeUNeoForge(IEventBus modEventBus) {
        modEventBus.addListener(this::registerPayloadHandlers);

        NeoForge.EVENT_BUS.addListener(service::onServerTick);
        NeoForge.EVENT_BUS.addListener(service::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedOut);
    }

    private void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ProtocolConstants.MOD_ID)
                .versioned(Integer.toString(SharedDefaults.PROTOCOL_VERSION))
                .optional();

        registrar.playToServer(ClientHelloPayload.TYPE, ClientHelloPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> service.acceptHello((ServerPlayer) context.player(), payload.packet())));
        registrar.playToClient(FarPlayersPayload.TYPE, FarPlayersPayload.STREAM_CODEC);

        PayloadRegistrar addonRegistrar = event.registrar(
                Integer.toString(AddonLimits.BUS_PROTOCOL_VERSION)
        ).optional();
        addonRegistrar.playBidirectional(
                AddonControlPayload.TYPE,
                AddonControlPayload.STREAM_CODEC,
                (payload, context) -> {
                    ServerPlayer player = (ServerPlayer) context.player();
                    ensureAddonConnection(player);
                    serverAddons.receiveControl(player.getUUID(), payload.message());
                }
        );
        addonRegistrar.playBidirectional(
                AddonDataPayload.TYPE,
                AddonDataPayload.STREAM_CODEC,
                (payload, context) -> {
                    ServerPlayer player = (ServerPlayer) context.player();
                    ensureAddonConnection(player);
                    serverAddons.receiveData(player.getUUID(), payload.envelope());
                }
        );
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        ensureAddonConnection((ServerPlayer) event.getEntity());
    }

    private void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        serverAddons.disconnect(event.getEntity().getUUID());
    }

    private void ensureAddonConnection(ServerPlayer player) {
        if (serverAddons.isConnected(player.getUUID()) || !supportsAddonBus(player)) {
            return;
        }
        serverAddons.connect(player.getUUID(), new AddonTransport() {
            @Override
            public void sendControl(AddonControlMessage message) {
                if (player.connection.hasChannel(AddonControlPayload.TYPE)) {
                    PacketDistributor.sendToPlayer(player, new AddonControlPayload(message));
                }
            }

            @Override
            public void sendData(AddonEnvelope envelope) {
                if (player.connection.hasChannel(AddonDataPayload.TYPE)) {
                    PacketDistributor.sendToPlayer(player, new AddonDataPayload(envelope));
                }
            }

            @Override
            public void disconnectForProtocolViolation() {
                player.connection.disconnect(Component.literal("Invalid SeeU addon traffic"));
            }
        });
    }

    private static boolean supportsAddonBus(ServerPlayer player) {
        return player.connection.hasChannel(AddonControlPayload.TYPE)
                && player.connection.hasChannel(AddonDataPayload.TYPE);
    }
}
