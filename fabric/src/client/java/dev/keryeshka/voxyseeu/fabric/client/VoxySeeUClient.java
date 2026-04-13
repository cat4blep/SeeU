package dev.keryeshka.voxyseeu.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.keryeshka.voxyseeu.common.SharedDefaults;
import dev.keryeshka.voxyseeu.common.protocol.ClientHelloPacket;
import dev.keryeshka.voxyseeu.fabric.client.config.VoxySeeUClientConfig;
import dev.keryeshka.voxyseeu.fabric.network.ClientHelloPayload;
import dev.keryeshka.voxyseeu.fabric.network.FabricPayloads;
import dev.keryeshka.voxyseeu.fabric.network.FarPlayersPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.lwjgl.glfw.GLFW;

public final class VoxySeeUClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("SeeU");
    private static final KeyMapping.Category SEEU_KEY_CATEGORY = KeyMapping.Category.register(Identifier.parse("seeu:general"));
    private static final KeyMapping OPEN_CONFIG_KEY = new KeyMapping(
            "key.seeu.open_config",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            SEEU_KEY_CATEGORY
    );

    private final FarPlayerTracker tracker = new FarPlayerTracker();
    private static VoxySeeUClientConfig config;
    private static FarPlayerRenderer renderer;

    @Override
    public void onInitializeClient() {
        FabricPayloads.register();

        config = VoxySeeUClientConfig.load();
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
        renderer = new FarPlayerRenderer(tracker, config);
        KeyBindingHelper.registerKeyBinding(OPEN_CONFIG_KEY);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            tracker.clear();
            renderer.clear();
            LOGGER.info("Sending SeeU hello to server");
            sendHello();
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

        WorldRenderEvents.END_EXTRACTION.register(renderer::render);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_CONFIG_KEY.consumeClick()) {
                client.setScreen(new SeeUConfigScreen(client.screen, config.copy(), VoxySeeUClient::applyConfig));
            }
        });
    }

    private static void applyConfig(VoxySeeUClientConfig updatedConfig) {
        if (config == null) {
            config = updatedConfig.copy();
        } else {
            config.copyFrom(updatedConfig);
        }
        config.save();
        sendHello();
    }

    private static void sendHello() {
        Minecraft minecraft = Minecraft.getInstance();
        if (config == null || minecraft.getConnection() == null) {
            return;
        }
        ClientPlayNetworking.send(new ClientHelloPayload(new ClientHelloPacket(
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
