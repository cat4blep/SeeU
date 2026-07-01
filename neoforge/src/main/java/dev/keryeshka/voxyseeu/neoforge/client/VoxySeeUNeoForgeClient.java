package dev.keryeshka.voxyseeu.neoforge.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.keryeshka.voxyseeu.common.SharedDefaults;
import dev.keryeshka.voxyseeu.common.protocol.ClientHelloPacket;
import dev.keryeshka.voxyseeu.common.protocol.FarPlayersPacket;
import dev.keryeshka.voxyseeu.common.protocol.ProtocolConstants;
import dev.keryeshka.voxyseeu.neoforge.client.config.VoxySeeUClientConfig;
import dev.keryeshka.voxyseeu.neoforge.network.ClientHelloPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(modid = ProtocolConstants.MOD_ID, value = Dist.CLIENT)
public final class VoxySeeUNeoForgeClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("SeeU");
    private static final String SEEU_KEY_CATEGORY = ResourceLocation.parse("seeu:general").toString();
    private static final KeyMapping OPEN_CONFIG_KEY = new KeyMapping(
            "key.seeu.open_config",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            SEEU_KEY_CATEGORY
    );

    private static final FarPlayerTracker TRACKER = new FarPlayerTracker();
    private static VoxySeeUClientConfig config;
    private static FarPlayerRenderer renderer;

    private VoxySeeUNeoForgeClient() {
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
            minecraft.setScreen(new SeeUConfigScreen(minecraft.screen, config.copy(), VoxySeeUNeoForgeClient::applyConfig));
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        ensureLoaded();
        renderer.render(event);
    }

    public static void handleFarPlayers(FarPlayersPacket packet) {
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
        config = VoxySeeUClientConfig.load();
        renderer = new FarPlayerRenderer(TRACKER, config);
        LOGGER.info(
                "Loaded SeeU client config: enabled={}, maxDistance={}, minDistance={}, animationDistance={}, nameTags={}, shareSelf={}, shareMaxDistance={}",
                config.enabled,
                config.maximumRenderDistanceBlocks,
                config.minimumProxyDistanceBlocks,
                config.maximumAnimationDistanceBlocks,
                config.renderNameTags,
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
                config.renderNameTags,
                config.shareSelf,
                config.shareMaximumDistanceBlocks
        )));
    }
}
