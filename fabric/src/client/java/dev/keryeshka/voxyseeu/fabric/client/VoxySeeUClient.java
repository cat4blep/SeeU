package dev.keryeshka.voxyseeu.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.keryeshka.voxyseeu.api.addon.AddonTransport;
import dev.keryeshka.voxyseeu.api.addon.SeeUClientAddons;
import dev.keryeshka.voxyseeu.api.addon.protocol.AddonControlMessage;
import dev.keryeshka.voxyseeu.api.addon.protocol.AddonEnvelope;
import dev.keryeshka.voxyseeu.common.SharedDefaults;
import dev.keryeshka.voxyseeu.common.client.FarPlayerRenderer;
import dev.keryeshka.voxyseeu.common.client.FarPlayerTracker;
import dev.keryeshka.voxyseeu.common.client.SeeUClientConfig;
import dev.keryeshka.voxyseeu.common.client.SeeUConfigScreen;
import dev.keryeshka.voxyseeu.common.protocol.ClientHelloPacket;
import dev.keryeshka.voxyseeu.fabric.network.ClientHelloPayload;
import dev.keryeshka.voxyseeu.fabric.network.AddonControlPayload;
import dev.keryeshka.voxyseeu.fabric.network.AddonDataPayload;
import dev.keryeshka.voxyseeu.fabric.network.FabricPayloads;
import dev.keryeshka.voxyseeu.fabric.network.FarPlayersPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
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
    private final SeeUClientAddons clientAddons = SeeUClientAddons.getInstance();
    private static SeeUClientConfig config;
    private static FarPlayerRenderer renderer;

    @Override
    public void onInitializeClient() {
        FabricPayloads.register();
        KeyMappingHelper.registerKeyMapping(OPEN_CONFIG_KEY);

        config = SeeUClientConfig.load(FabricLoader.getInstance().getConfigDir());
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
        renderer = new FarPlayerRenderer(tracker, config);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            tracker.clear();
            renderer.clear();
            LOGGER.info("Sending SeeU hello to server");
            sendHello();
            connectAddons();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            tracker.clear();
            renderer.clear();
            clientAddons.disconnect();
        });

        ClientPlayNetworking.registerGlobalReceiver(FarPlayersPayload.TYPE, (payload, context) -> {
            boolean firstPacket = !tracker.hasReceivedPacket();
            if (!tracker.apply(payload.packet())) {
                return;
            }
            if (firstPacket) {
                LOGGER.info(
                        "Received first SeeU packet: dimension={}, players={}",
                        payload.packet().dimensionKey(),
                        payload.packet().players().size()
                );
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(AddonControlPayload.TYPE, (payload, context) ->
                clientAddons.receiveControl(payload.message()));
        ClientPlayNetworking.registerGlobalReceiver(AddonDataPayload.TYPE, (payload, context) ->
                clientAddons.receiveData(payload.envelope()));

        LevelRenderEvents.COLLECT_SUBMITS.register(context -> renderer.render(
                context.poseStack(),
                context.levelState(),
                context.submitNodeCollector()
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_CONFIG_KEY.consumeClick()) {
                client.gui.setScreen(createConfigScreen(client.gui.screen()));
            }
        });
    }

    static Screen createConfigScreen(Screen parent) {
        return new SeeUConfigScreen(parent, config.copy(), VoxySeeUClient::applyConfig);
    }

    private static void applyConfig(SeeUClientConfig updatedConfig) {
        config.copyFrom(updatedConfig);
        config.save();
        sendHello();
    }

    public static boolean shouldDisableVanillaFog(Camera camera) {
        if (!config.enabled || !config.disableVanillaFog || camera.getFluidInCamera() != FogType.NONE) {
            return false;
        }
        Entity entity = camera.entity();
        return !(entity instanceof LivingEntity livingEntity
                && (livingEntity.hasEffect(MobEffects.BLINDNESS) || livingEntity.hasEffect(MobEffects.DARKNESS)));
    }

    private static void sendHello() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) {
            return;
        }
        ClientPlayNetworking.send(new ClientHelloPayload(new ClientHelloPacket(
                SharedDefaults.PROTOCOL_VERSION,
                config.enabled,
                config.maximumRenderDistanceBlocks,
                config.minimumProxyDistanceBlocks,
                config.shareSelf,
                config.shareMaximumDistanceBlocks
        )));
    }

    private void connectAddons() {
        clientAddons.disconnect();
        if (!ClientPlayNetworking.canSend(AddonControlPayload.TYPE)
                || !ClientPlayNetworking.canSend(AddonDataPayload.TYPE)) {
            return;
        }
        clientAddons.connect(new AddonTransport() {
            @Override
            public void sendControl(AddonControlMessage message) {
                if (ClientPlayNetworking.canSend(AddonControlPayload.TYPE)) {
                    ClientPlayNetworking.send(new AddonControlPayload(message));
                }
            }

            @Override
            public void sendData(AddonEnvelope envelope) {
                if (ClientPlayNetworking.canSend(AddonDataPayload.TYPE)) {
                    ClientPlayNetworking.send(new AddonDataPayload(envelope));
                }
            }
        });
    }
}
