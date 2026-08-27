package dev.keryeshka.voxyseeu.neoforge.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.shaders.FogShape;
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
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
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
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(value = ProtocolConstants.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = ProtocolConstants.MOD_ID, value = Dist.CLIENT)
public final class VoxySeeUNeoForgeClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("SeeU");
    private static final String SEEU_KEY_CATEGORY = "key.categories.seeu.general";
    private static final KeyMapping OPEN_CONFIG_KEY = new KeyMapping(
            "key.seeu.open_config",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            SEEU_KEY_CATEGORY
    );

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
        event.register(OPEN_CONFIG_KEY);
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
    public static void onRenderLevel(RenderLevelStageEvent event) {
        ensureLoaded();
        renderer.render(event);
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        ensureLoaded();
        if (!shouldDisableVanillaFog(event.getCamera())) {
            return;
        }
        event.setNearPlaneDistance(Float.MAX_VALUE);
        event.setFarPlaneDistance(Float.MAX_VALUE);
        event.setFogShape(FogShape.CYLINDER);
        event.setCanceled(true);
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

    public static void handleAddonControl(AddonControlMessage message) {
        CLIENT_ADDONS.receiveControl(message);
    }

    public static void handleAddonData(AddonEnvelope envelope) {
        CLIENT_ADDONS.receiveData(envelope);
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
        Entity entity = camera.getEntity();
        return !(entity instanceof LivingEntity livingEntity
                && (livingEntity.hasEffect(MobEffects.BLINDNESS) || livingEntity.hasEffect(MobEffects.DARKNESS)));
    }

    private static void sendHello() {
        Minecraft minecraft = Minecraft.getInstance();
        if (config == null || minecraft.getConnection() == null) {
            return;
        }
        PacketDistributor.sendToServer(new ClientHelloPayload(new ClientHelloPacket(
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
                    PacketDistributor.sendToServer(new AddonControlPayload(message));
                }
            }

            @Override
            public void sendData(AddonEnvelope envelope) {
                var currentConnection = Minecraft.getInstance().getConnection();
                if (currentConnection != null && currentConnection.hasChannel(AddonDataPayload.TYPE)) {
                    PacketDistributor.sendToServer(new AddonDataPayload(envelope));
                }
            }
        });
    }
}
