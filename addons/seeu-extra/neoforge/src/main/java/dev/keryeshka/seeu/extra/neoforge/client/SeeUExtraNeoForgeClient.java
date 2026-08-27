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
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.lang.reflect.Field;

@Mod(value = SeeUExtra.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = SeeUExtra.MOD_ID, value = Dist.CLIENT)
public final class SeeUExtraNeoForgeClient {
    private static final Field SUBMIT_NODE_COLLECTOR_FIELD = findSubmitNodeCollectorField();
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
    public static void onExtractLevelRenderState(ExtractLevelRenderStateEvent event) {
        if (renderer != null) {
            renderer.updateFrustum(event.getFrustum());
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent.AfterOpaqueBlocks event) {
        if (renderer == null) {
            return;
        }
        SubmitNodeCollector submitNodeCollector = submitNodeCollector(event);
        if (submitNodeCollector == null) {
            return;
        }
        renderer.render(
                event.getPoseStack(),
                event.getLevelRenderState(),
                submitNodeCollector
        );
    }

    private static void clear() {
        if (endpoint != null) {
            endpoint.clear();
        }
    }

    private static SubmitNodeCollector submitNodeCollector(RenderLevelStageEvent event) {
        if (SUBMIT_NODE_COLLECTOR_FIELD == null) {
            return null;
        }
        try {
            Object value = SUBMIT_NODE_COLLECTOR_FIELD.get(event.getLevelRenderer());
            return value instanceof SubmitNodeCollector collector ? collector : null;
        } catch (IllegalAccessException exception) {
            return null;
        }
    }

    private static Field findSubmitNodeCollectorField() {
        for (Field field : LevelRenderer.class.getDeclaredFields()) {
            if (SubmitNodeCollector.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                return field;
            }
        }
        return null;
    }
}
