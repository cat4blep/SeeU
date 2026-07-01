package dev.keryeshka.voxyseeu.neoforge;

import dev.keryeshka.voxyseeu.common.SharedDefaults;
import dev.keryeshka.voxyseeu.common.protocol.ProtocolConstants;
import dev.keryeshka.voxyseeu.neoforge.client.VoxySeeUNeoForgeClient;
import dev.keryeshka.voxyseeu.neoforge.config.VoxySeeUServerConfig;
import dev.keryeshka.voxyseeu.neoforge.network.ClientHelloPayload;
import dev.keryeshka.voxyseeu.neoforge.network.FarPlayersPayload;
import dev.keryeshka.voxyseeu.neoforge.server.NeoForgeFarPlayerService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(ProtocolConstants.MOD_ID)
public final class VoxySeeUNeoForge {
    private final NeoForgeFarPlayerService service = new NeoForgeFarPlayerService(VoxySeeUServerConfig.load());

    public VoxySeeUNeoForge(IEventBus modEventBus) {
        modEventBus.addListener(this::registerPayloadHandlers);

        NeoForge.EVENT_BUS.addListener(service::onServerTick);
        NeoForge.EVENT_BUS.addListener(service::onPlayerLoggedOut);
    }

    private void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ProtocolConstants.MOD_ID)
                .versioned(Integer.toString(SharedDefaults.PROTOCOL_VERSION))
                .optional();

        registrar.playToServer(ClientHelloPayload.TYPE, ClientHelloPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> service.handleHello((ServerPlayer) context.player(), payload.packet())));

        if (FMLEnvironment.dist == Dist.CLIENT) {
            registrar.playToClient(FarPlayersPayload.TYPE, FarPlayersPayload.STREAM_CODEC, (payload, context) ->
                    context.enqueueWork(() -> VoxySeeUNeoForgeClient.handleFarPlayers(payload.packet())));
        } else {
            registrar.playToClient(FarPlayersPayload.TYPE, FarPlayersPayload.STREAM_CODEC, (payload, context) -> {
            });
        }
    }
}
