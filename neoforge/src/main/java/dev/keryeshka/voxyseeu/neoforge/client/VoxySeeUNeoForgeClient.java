package dev.keryeshka.voxyseeu.neoforge.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.keryeshka.voxyseeu.api.addon.AddonTransport;
import dev.keryeshka.voxyseeu.api.addon.SeeUClientAddons;
import dev.keryeshka.voxyseeu.api.addon.protocol.AddonControlMessage;
import dev.keryeshka.voxyseeu.api.addon.protocol.AddonEnvelope;
import dev.keryeshka.voxyseeu.common.SharedDefaults;
import dev.keryeshka.voxyseeu.common.protocol.ClientHelloPacket;
import dev.keryeshka.voxyseeu.common.protocol.FarPlayersPacket;
import dev.keryeshka.voxyseeu.common.protocol.ProtocolConstants;
import dev.keryeshka.voxyseeu.neoforge.client.config.VoxySeeUClientConfig;
import dev.keryeshka.voxyseeu.neoforge.network.AddonControlPayload;
import dev.keryeshka.voxyseeu.neoforge.network.AddonDataPayload;
import dev.keryeshka.voxyseeu.neoforge.network.ClientHelloPayload;
import dev.keryeshka.voxyseeu.neoforge.network.FarPlayersPayload;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

@Mod(value = ProtocolConstants.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = ProtocolConstants.MOD_ID, value = Dist.CLIENT)
public final class VoxySeeUNeoForgeClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("SeeU");
    private static final KeyMapping.Category SEEU_KEY_CATEGORY =
            new KeyMapping.Category(Identifier.parse("seeu:general"));
    private static final KeyMapping OPEN_CONFIG_KEY = new KeyMapping(
            "key.seeu.open_config",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            SEEU_KEY_CATEGORY
    );
    private static final Field SUBMIT_NODE_COLLECTOR_FIELD = findSubmitNodeCollectorField();

    private static final FarPlayerTracker TRACKER = new FarPlayerTracker();
    private static final SeeUClientAddons CLIENT_ADDONS = SeeUClientAddons.getInstance();
    private static VoxySeeUClientConfig config;
    private static FarPlayerRenderer renderer;

    public VoxySeeUNeoForgeClient(ModContainer container) {
        container.registerExtensionPoint(
                IConfigScreenFactory.class,
                (ignored, parent) -> createConfigScreen(parent)
        );
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(SEEU_KEY_CATEGORY);
        event.register(OPEN_CONFIG_KEY);
    }

    @SubscribeEvent
    public static void registerClientPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(FarPlayersPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> handleFarPlayers(payload.packet())));
        event.register(AddonControlPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> CLIENT_ADDONS.receiveControl(payload.message())));
        event.register(AddonDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> CLIENT_ADDONS.receiveData(payload.envelope())));
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        ensureLoaded();
        TRACKER.clear();
        renderer.clear();
        LOGGER.info("Sending SeeU hello to server");
        sendHello();
        connectAddons();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if (renderer != null) {
            renderer.clear();
        }
        TRACKER.clear();
        CLIENT_ADDONS.disconnect();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ensureLoaded();
        Minecraft minecraft = Minecraft.getInstance();
        while (OPEN_CONFIG_KEY.consumeClick()) {
            minecraft.setScreen(createConfigScreen(minecraft.screen));
        }
    }

    static Screen createConfigScreen(Screen parent) {
        ensureLoaded();
        return new SeeUConfigScreen(parent, config.copy(), VoxySeeUNeoForgeClient::applyConfig);
    }

    @SubscribeEvent
    public static void onExtractLevelRenderState(ExtractLevelRenderStateEvent event) {
        ensureLoaded();
        renderer.updateFrustum(event.getFrustum());
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent.AfterOpaqueBlocks event) {
        ensureLoaded();
        renderer.render(event, submitNodeCollector(event));
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        ensureLoaded();
        if (!shouldDisableVanillaFog(event.getCamera())) {
            return;
        }
        disableFog(event.getFogData());
    }

    public static void handleFarPlayers(FarPlayersPacket packet) {
        ensureLoaded();
        boolean firstPacket = !TRACKER.hasReceivedPacket();
        if (!TRACKER.apply(packet)) {
            return;
        }
        if (firstPacket) {
            LOGGER.info("Received first SeeU packet: dimension={}, players={}", packet.dimensionKey(), packet.players().size());
        }
    }

    private static void ensureLoaded() {
        if (config != null && renderer != null) {
            return;
        }
        config = VoxySeeUClientConfig.load();
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

    private static void applyConfig(VoxySeeUClientConfig updatedConfig) {
        ensureLoaded();
        config.copyFrom(updatedConfig);
        config.save();
        sendHello();
    }

    private static boolean shouldDisableVanillaFog(Camera camera) {
        if (config == null || !config.enabled || !config.disableVanillaFog || camera.getFluidInCamera() != FogType.NONE) {
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
        if (config == null || minecraft.getConnection() == null) {
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

    private static void connectAddons() {
        CLIENT_ADDONS.disconnect();
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null
                || !connection.hasChannel(AddonControlPayload.TYPE)
                || !connection.hasChannel(AddonDataPayload.TYPE)) {
            return;
        }
        CLIENT_ADDONS.connect(new AddonTransport() {
            @Override
            public void sendControl(AddonControlMessage message) {
                var currentConnection = Minecraft.getInstance().getConnection();
                if (currentConnection != null && currentConnection.hasChannel(AddonControlPayload.TYPE)) {
                    ClientPacketDistributor.sendToServer(new AddonControlPayload(message));
                }
            }

            @Override
            public void sendData(AddonEnvelope envelope) {
                var currentConnection = Minecraft.getInstance().getConnection();
                if (currentConnection != null && currentConnection.hasChannel(AddonDataPayload.TYPE)) {
                    ClientPacketDistributor.sendToServer(new AddonDataPayload(envelope));
                }
            }
        });
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
