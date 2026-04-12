package dev.keryeshka.voxyseeu.fabric.client;

import dev.keryeshka.voxyseeu.common.SharedDefaults;
import dev.keryeshka.voxyseeu.common.protocol.ClientHelloPacket;
import dev.keryeshka.voxyseeu.fabric.client.config.VoxySeeUClientConfig;
import dev.keryeshka.voxyseeu.fabric.network.ClientHelloPayload;
import dev.keryeshka.voxyseeu.fabric.network.FabricPayloads;
import dev.keryeshka.voxyseeu.fabric.network.FarPlayersPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VoxySeeUClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("SeeU");

    private final FarPlayerTracker tracker = new FarPlayerTracker();

    @Override
    public void onInitializeClient() {
        FabricPayloads.register();

        VoxySeeUClientConfig config = VoxySeeUClientConfig.load();
        LOGGER.info(
                "Loaded SeeU client config: enabled={}, maxDistance={}, minDistance={}, nameTags={}",
                config.enabled,
                config.maximumRenderDistanceBlocks,
                config.minimumProxyDistanceBlocks,
                config.renderNameTags
        );
        FarPlayerRenderer renderer = new FarPlayerRenderer(tracker, config);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            tracker.clear();
            renderer.clear();
            LOGGER.info("Sending SeeU hello to server");
            ClientPlayNetworking.send(new ClientHelloPayload(new ClientHelloPacket(
                    SharedDefaults.PROTOCOL_VERSION,
                    config.enabled,
                    config.maximumRenderDistanceBlocks,
                    config.minimumProxyDistanceBlocks,
                    config.renderNameTags
            )));
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            tracker.clear();
            renderer.clear();
        });

        ClientPlayNetworking.registerGlobalReceiver(FarPlayersPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    boolean firstPacket = !tracker.hasReceivedPacket();
                    tracker.apply(payload.packet());
                    if (firstPacket) {
                        LOGGER.info(
                                "Received first SeeU packet: dimension={}, players={}",
                                payload.packet().dimensionKey(),
                                payload.packet().players().size()
                        );
                    }
                }));

        LevelRenderEvents.COLLECT_SUBMITS.register(renderer::render);
    }
}
