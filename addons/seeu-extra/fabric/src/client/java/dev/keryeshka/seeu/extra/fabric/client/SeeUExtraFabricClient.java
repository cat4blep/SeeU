package dev.keryeshka.seeu.extra.fabric.client;

import dev.keryeshka.seeu.extra.SeeUExtra;
import dev.keryeshka.seeu.extra.client.ExtraEntityRenderer;
import dev.keryeshka.seeu.extra.client.ExtraEntityTracker;
import dev.keryeshka.seeu.extra.client.SeeUExtraClientConfig;
import dev.keryeshka.seeu.extra.client.SeeUExtraClientEndpoint;
import dev.keryeshka.seeu.extra.protocol.ExtraPacketCodec;
import dev.keryeshka.voxyseeu.api.addon.SeeUClientAddons;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;

public final class SeeUExtraFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SeeUExtraClientConfig config = SeeUExtraClientConfig.load(FabricLoader.getInstance().getConfigDir());
        ExtraEntityTracker tracker = new ExtraEntityTracker();
        ExtraEntityRenderer renderer = new ExtraEntityRenderer(tracker, config);
        SeeUExtraClientEndpoint endpoint = new SeeUExtraClientEndpoint(tracker, renderer);

        SeeUClientAddons.getInstance().register(
                SeeUExtra.DESCRIPTOR,
                () -> ExtraPacketCodec.encodeClientOffer(config.offer()),
                endpoint
        );
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> endpoint.clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> endpoint.clear());
        WorldRenderEvents.END_EXTRACTION.register(context -> renderer.updateFrustum(context.frustum()));
        WorldRenderEvents.AFTER_ENTITIES.register(context -> renderer.render(
                context.matrices(),
                context.worldState(),
                context.commandQueue()
        ));
    }
}
