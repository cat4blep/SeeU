package dev.keryeshka.seeu.extra.neoforge.client;

import dev.keryeshka.seeu.extra.SeeUExtra;
import dev.keryeshka.seeu.extra.client.ExtraEntityRenderer;
import dev.keryeshka.seeu.extra.client.ExtraEntityTracker;
import dev.keryeshka.seeu.extra.client.SeeUExtraClientConfig;
import dev.keryeshka.seeu.extra.client.SeeUExtraClientEndpoint;
import dev.keryeshka.seeu.extra.protocol.ExtraPacketCodec;
import dev.keryeshka.voxyseeu.api.addon.SeeUClientAddons;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.minecraft.client.Minecraft;

@Mod(value = SeeUExtra.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = SeeUExtra.MOD_ID, value = Dist.CLIENT)
public final class SeeUExtraNeoForgeClient {
    private static SeeUExtraClientEndpoint endpoint;
    private static ExtraEntityRenderer renderer;

    public SeeUExtraNeoForgeClient() {
        SeeUExtraClientConfig config = SeeUExtraClientConfig.load(FMLPaths.CONFIGDIR.get());
        ExtraEntityTracker tracker = new ExtraEntityTracker();
        renderer = new ExtraEntityRenderer(tracker, config);
        endpoint = new SeeUExtraClientEndpoint(tracker, renderer);
        SeeUClientAddons.getInstance().register(
                SeeUExtra.DESCRIPTOR,
                () -> ExtraPacketCodec.encodeClientOffer(config.offer()),
                endpoint
        );
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        clear();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (renderer == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        var consumers = Minecraft.getInstance().renderBuffers().bufferSource();
        renderer.render(
                event.getPoseStack(),
                consumers,
                event.getCamera().getPosition(),
                event.getFrustum(),
                event.getPartialTick().getGameTimeDeltaPartialTick(false)
        );
        consumers.endBatch();
    }

    private static void clear() {
        if (endpoint != null) {
            endpoint.clear();
        }
    }
}
