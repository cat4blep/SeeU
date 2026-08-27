package dev.keryeshka.voxyseeu.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.keryeshka.voxyseeu.api.addon.AddonTransport;
import dev.keryeshka.voxyseeu.api.addon.SeeUClientAddons;
import dev.keryeshka.voxyseeu.api.addon.protocol.AddonControlMessage;
import dev.keryeshka.voxyseeu.api.addon.protocol.AddonEnvelope;
import dev.keryeshka.voxyseeu.common.SharedDefaults;
import dev.keryeshka.voxyseeu.common.protocol.ClientHelloPacket;
import dev.keryeshka.voxyseeu.fabric.client.config.VoxySeeUClientConfig;
import dev.keryeshka.voxyseeu.fabric.network.AddonControlPayload;
import dev.keryeshka.voxyseeu.fabric.network.AddonDataPayload;
import dev.keryeshka.voxyseeu.fabric.network.ClientHelloPayload;
import dev.keryeshka.voxyseeu.fabric.network.FabricAddonFragmentAssembler;
import dev.keryeshka.voxyseeu.fabric.network.FabricAddonFragmenter;
import dev.keryeshka.voxyseeu.fabric.network.FabricAddonWireCodec;
import dev.keryeshka.voxyseeu.fabric.network.FabricAddonWireLimits;
import dev.keryeshka.voxyseeu.fabric.network.FabricPayloads;
import dev.keryeshka.voxyseeu.fabric.network.FarPlayersPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.lwjgl.glfw.GLFW;

public final class VoxySeeUClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("SeeU");
    private static final String SEEU_KEY_CATEGORY = "key.categories.seeu.general";
    private static final KeyMapping OPEN_CONFIG_KEY = new KeyMapping(
            "key.seeu.open_config",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            SEEU_KEY_CATEGORY
    );

    private final FarPlayerTracker tracker = new FarPlayerTracker();
    private final SeeUClientAddons clientAddons = SeeUClientAddons.getInstance();
    private final FabricAddonFragmentAssembler controlAssembler = new FabricAddonFragmentAssembler(
            FabricAddonWireLimits.CONTROL_BYTES
    );
    private final FabricAddonFragmentAssembler dataAssembler = new FabricAddonFragmentAssembler(
            FabricAddonWireLimits.DATA_BYTES
    );
    private boolean addonTrafficRejected;
    private static VoxySeeUClientConfig config;
    private static FarPlayerRenderer renderer;

    @Override
    public void onInitializeClient() {
        FabricPayloads.register();
        KeyBindingHelper.registerKeyBinding(OPEN_CONFIG_KEY);

        config = VoxySeeUClientConfig.load();
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
            clearAddonAssemblies();
            LOGGER.info("Sending SeeU hello to server");
            sendHello();
            connectAddons();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            tracker.clear();
            renderer.clear();
            clearAddonAssemblies();
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
                receiveAddonControl(payload));
        ClientPlayNetworking.registerGlobalReceiver(AddonDataPayload.TYPE, (payload, context) ->
                receiveAddonData(payload));

        WorldRenderEvents.AFTER_ENTITIES.register(renderer::render);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            long nowNanos = System.nanoTime();
            if (controlAssembler.isExpired(nowNanos) || dataAssembler.isExpired(nowNanos)) {
                rejectAddonTraffic();
            }
            while (OPEN_CONFIG_KEY.consumeClick()) {
                client.setScreen(createConfigScreen(client.screen));
            }
        });
    }

    static Screen createConfigScreen(Screen parent) {
        return new SeeUConfigScreen(parent, config.copy(), VoxySeeUClient::applyConfig);
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

    public static boolean shouldDisableVanillaFog(Camera camera) {
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
        clearAddonAssemblies();
        addonTrafficRejected = false;
        if (!ClientPlayNetworking.canSend(AddonControlPayload.TYPE)
                || !ClientPlayNetworking.canSend(AddonDataPayload.TYPE)) {
            return;
        }
        clientAddons.connect(new AddonTransport() {
            private final FabricAddonFragmenter controlFragmenter = new FabricAddonFragmenter();
            private final FabricAddonFragmenter dataFragmenter = new FabricAddonFragmenter();

            @Override
            public void sendControl(AddonControlMessage message) {
                if (ClientPlayNetworking.canSend(AddonControlPayload.TYPE)) {
                    for (var fragment : controlFragmenter.fragment(
                            FabricAddonWireCodec.encodeControl(message),
                            FabricAddonWireLimits.CONTROL_BYTES
                    )) {
                        ClientPlayNetworking.send(new AddonControlPayload(fragment));
                    }
                }
            }

            @Override
            public void sendData(AddonEnvelope envelope) {
                if (ClientPlayNetworking.canSend(AddonDataPayload.TYPE)) {
                    for (var fragment : dataFragmenter.fragment(
                            FabricAddonWireCodec.encodeData(envelope),
                            FabricAddonWireLimits.DATA_BYTES
                    )) {
                        ClientPlayNetworking.send(new AddonDataPayload(fragment));
                    }
                }
            }
        });
    }

    private void receiveAddonControl(AddonControlPayload payload) {
        if (addonTrafficRejected) {
            return;
        }
        try {
            controlAssembler.accept(payload.fragment()).ifPresent(bytes -> {
                try {
                    clientAddons.receiveControl(FabricAddonWireCodec.decodeControl(bytes));
                } catch (RuntimeException ignored) {
                    rejectAddonTraffic();
                }
            });
        } catch (IllegalArgumentException ignored) {
            rejectAddonTraffic();
        }
    }

    private void receiveAddonData(AddonDataPayload payload) {
        if (addonTrafficRejected) {
            return;
        }
        try {
            dataAssembler.accept(payload.fragment()).ifPresent(bytes -> {
                try {
                    clientAddons.receiveData(FabricAddonWireCodec.decodeData(bytes));
                } catch (RuntimeException ignored) {
                    rejectAddonTraffic();
                }
            });
        } catch (IllegalArgumentException ignored) {
            rejectAddonTraffic();
        }
    }

    private void rejectAddonTraffic() {
        addonTrafficRejected = true;
        clearAddonAssemblies();
        clientAddons.disconnect();
    }

    private void clearAddonAssemblies() {
        controlAssembler.clear();
        dataAssembler.clear();
    }
}
