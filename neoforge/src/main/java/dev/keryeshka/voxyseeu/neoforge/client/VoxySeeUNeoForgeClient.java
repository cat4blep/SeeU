package dev.keryeshka.voxyseeu.neoforge.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.keryeshka.voxyseeu.common.SharedDefaults;
import dev.keryeshka.voxyseeu.common.client.FarPlayerRenderer;
import dev.keryeshka.voxyseeu.common.client.FarPlayerTracker;
import dev.keryeshka.voxyseeu.common.client.SeeUClientConfig;
import dev.keryeshka.voxyseeu.common.client.SeeUConfigScreen;
import dev.keryeshka.voxyseeu.common.protocol.ClientHelloPacket;
import dev.keryeshka.voxyseeu.common.protocol.FarPlayersPacket;
import dev.keryeshka.voxyseeu.common.protocol.ProtocolConstants;
import dev.keryeshka.voxyseeu.neoforge.network.ClientHelloPayload;
import dev.keryeshka.voxyseeu.neoforge.network.FarPlayersPayload;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(modid = ProtocolConstants.MOD_ID, value = Dist.CLIENT)
public final class VoxySeeUNeoForgeClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("SeeU");
    private static final KeyMapping.Category SEEU_KEY_CATEGORY =
            KeyMapping.Category.register(Identifier.parse("seeu:general"));
    private static final KeyMapping OPEN_CONFIG_KEY = new KeyMapping(
            "key.seeu.open_config",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            SEEU_KEY_CATEGORY
    );
    private static final FarPlayerTracker TRACKER = new FarPlayerTracker();
    private static SeeUClientConfig config;
    private static FarPlayerRenderer renderer;

    private VoxySeeUNeoForgeClient() {
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(SEEU_KEY_CATEGORY);
        event.register(OPEN_CONFIG_KEY);
    }

    @SubscribeEvent
    public static void registerClientPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(FarPlayersPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> applyFarPlayers(payload.packet())));
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        ensureLoaded();
        TRACKER.clear();
        renderer.clear();
        LOGGER.info("Sending SeeU hello to server");
        sendHello();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if (renderer != null) {
            renderer.clear();
        }
        TRACKER.clear();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ensureLoaded();
        Minecraft minecraft = Minecraft.getInstance();
        while (OPEN_CONFIG_KEY.consumeClick()) {
            minecraft.gui.setScreen(new SeeUConfigScreen(minecraft.gui.screen(), config.copy(), VoxySeeUNeoForgeClient::applyConfig));
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(SubmitCustomGeometryEvent event) {
        ensureLoaded();
        renderer.render(
                event.getPoseStack(),
                event.getLevelRenderState(),
                event.getSubmitNodeCollector()
        );
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        ensureLoaded();
        if (!shouldDisableVanillaFog(event.getCamera())) {
            return;
        }
        disableFog(event.getFogData());
    }

    public static void applyFarPlayers(FarPlayersPacket packet) {
        ensureLoaded();
        boolean firstPacket = !TRACKER.hasReceivedPacket();
        TRACKER.apply(packet);
        if (firstPacket) {
            LOGGER.info("Received first SeeU packet: dimension={}, players={}", packet.dimensionKey(), packet.players().size());
        }
    }

    private static void ensureLoaded() {
        if (config != null && renderer != null) {
            return;
        }
        config = SeeUClientConfig.load(FMLPaths.CONFIGDIR.get());
        renderer = new FarPlayerRenderer(TRACKER, config);
        LOGGER.info(
                "Loaded SeeU client config: enabled={}, maxDistance={}, minDistance={}, animationDistance={}, nameTags={}, disableVanillaFog={}, shareSelf={}, shareMaxDistance={}",
                config.enabled,
                config.maximumRenderDistanceBlocks,
                config.minimumProxyDistanceBlocks,
                config.maximumAnimationDistanceBlocks,
                config.renderNameTags,
                config.disableVanillaFog,
                config.shareSelf,
                config.shareMaximumDistanceBlocks
        );
    }

    private static void applyConfig(SeeUClientConfig updatedConfig) {
        ensureLoaded();
        config.copyFrom(updatedConfig);
        config.save();
        sendHello();
    }

    private static boolean shouldDisableVanillaFog(Camera camera) {
        if (!config.enabled || !config.disableVanillaFog || camera.getFluidInCamera() != FogType.NONE) {
            return false;
        }
        Entity entity = camera.entity();
        return !(entity instanceof LivingEntity livingEntity
                && (livingEntity.hasEffect(MobEffects.BLINDNESS) || livingEntity.hasEffect(MobEffects.DARKNESS)));
    }

    private static void disableFog(FogData fogData) {
        fogData.environmentalStart = Float.MAX_VALUE;
        fogData.environmentalEnd = Float.MAX_VALUE;
        fogData.renderDistanceStart = Float.MAX_VALUE;
        fogData.renderDistanceEnd = Float.MAX_VALUE;
        fogData.skyEnd = Float.MAX_VALUE;
        fogData.cloudEnd = Float.MAX_VALUE;
    }

    private static void sendHello() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) {
            return;
        }
        ClientPacketDistributor.sendToServer(new ClientHelloPayload(new ClientHelloPacket(
                SharedDefaults.PROTOCOL_VERSION,
                config.enabled,
                config.maximumRenderDistanceBlocks,
                config.minimumProxyDistanceBlocks,
                config.shareSelf,
                config.shareMaximumDistanceBlocks
        )));
    }

}
