package dev.keryeshka.voxyseeu.neoforge.client;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.keryeshka.voxyseeu.common.protocol.FarItemSnapshot;
import dev.keryeshka.voxyseeu.common.protocol.FarPlayerMetadata;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import dev.keryeshka.voxyseeu.neoforge.client.config.VoxySeeUClientConfig;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

final class FarPlayerRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("SeeU");
    private static final float WALK_ANIMATION_SCALE = 0.4F;
    private static final AtomicInteger NEXT_PROXY_ENTITY_ID = new AtomicInteger(1_000_000_000);

    private final FarPlayerTracker tracker;
    private final VoxySeeUClientConfig config;
    private final Map<UUID, FarPlayerRenderProxy> proxies = new HashMap<>();
    private final Map<UUID, Entity> vehicles = new HashMap<>();
    private final Set<UUID> activePlayers = new HashSet<>();
    private final Set<UUID> activeVehicles = new HashSet<>();
    private final Set<UUID> submittedVehicles = new HashSet<>();
    private boolean loggedFirstSubmission;

    FarPlayerRenderer(FarPlayerTracker tracker, VoxySeeUClientConfig config) {
        this.tracker = tracker;
        this.config = config;
    }

    void clear() {
        for (FarPlayerRenderProxy proxy : proxies.values()) {
            proxy.stopRiding();
        }
        for (Entity vehicle : vehicles.values()) {
            vehicle.ejectPassengers();
        }
        proxies.clear();
        vehicles.clear();
        activePlayers.clear();
        activeVehicles.clear();
        submittedVehicles.clear();
        loggedFirstSubmission = false;
    }

    void render(RenderLevelStageEvent context) {
        if (context.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        PoseStack poseStack = context.getPoseStack();
        MultiBufferSource.BufferSource consumers = Minecraft.getInstance().renderBuffers().bufferSource();
        Vec3 cameraPosition = context.getCamera().getPosition();
        Frustum frustum = context.getFrustum();
        float partialTick = context.getPartialTick().getGameTimeDeltaPartialTick(false);
        Minecraft minecraft = Minecraft.getInstance();
        if (!config.enabled) {
            clear();
            return;
        }
        ClientLevel level = minecraft.level;
        LocalPlayer localPlayer = minecraft.player;
        if (level == null || localPlayer == null || poseStack == null || consumers == null) {
            clear();
            return;
        }

        String currentDimension = level.dimension().location().toString();
        if (!currentDimension.equals(tracker.dimensionKey())) {
            clear();
            return;
        }

        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        int animationTick = localPlayer.tickCount;
        long now = System.nanoTime();
        Vec3 localPlayerPosition = localPlayer.position();
        double minimumDistanceSquared = (double) config.minimumProxyDistanceBlocks
                * config.minimumProxyDistanceBlocks;
        double maximumDistanceSquared = (double) config.maximumRenderDistanceBlocks
                * config.maximumRenderDistanceBlocks;
        double maximumAnimationDistanceSquared = (double) config.maximumAnimationDistanceBlocks
                * config.maximumAnimationDistanceBlocks;
        double vanillaRenderDistance = minecraft.options.getEffectiveRenderDistance() * 16.0D + 16.0D;
        double vanillaRenderDistanceSquared = vanillaRenderDistance * vanillaRenderDistance;

        activePlayers.clear();
        activeVehicles.clear();
        submittedVehicles.clear();
        for (TrackedFarPlayer tracked : tracker.players()) {
            Vec3 position = tracked.renderPosition(now);
            double distanceSquared = position.distanceToSqr(localPlayerPosition);
            if (distanceSquared < minimumDistanceSquared || distanceSquared > maximumDistanceSquared) {
                continue;
            }

            int chunkX = Mth.floor(position.x) >> 4;
            int chunkZ = Mth.floor(position.z) >> 4;
            boolean chunkLoaded = level.hasChunk(chunkX, chunkZ);
            boolean realPlayerStillPresent = level.getPlayerByUUID(tracked.uuid()) != null;
            if (realPlayerStillPresent && chunkLoaded && distanceSquared <= vanillaRenderDistanceSquared) {
                continue;
            }

            FarPlayerRenderProxy proxy = proxies.compute(tracked.uuid(), (uuid, current) -> {
                if (current == null || current.level() != level) {
                    return new FarPlayerRenderProxy(level, tracked.uuid(), tracked.name());
                }
                return current;
            });

            boolean allowWalkAnimation = config.maximumAnimationDistanceBlocks > 0
                    && distanceSquared <= maximumAnimationDistanceSquared;
            proxy.apply(
                    tracked,
                    position,
                    config.renderNameTags,
                    config.maximumRenderDistanceBlocks,
                    allowWalkAnimation,
                    animationTick,
                    now
            );
            activePlayers.add(tracked.uuid());

            if (tracked.hasVehicle()) {
                Entity vehicle = vehicles.compute(tracked.vehicleUuid(), (uuid, current) -> {
                    if (current == null
                            || current.level() != level
                            || !BuiltInRegistries.ENTITY_TYPE.getKey(current.getType()).toString()
                            .equals(tracked.vehicleTypeId())) {
                        return createVehicleProxy(level, tracked.vehicleTypeId());
                    }
                    return current;
                });
                if (vehicle != null) {
                    applyVehicleState(vehicle, tracked, now);
                    activeVehicles.add(tracked.vehicleUuid());
                    if (proxy.getVehicle() != vehicle) {
                        proxy.stopRiding();
                        proxy.startRiding(vehicle);
                    }
                    if (submittedVehicles.add(tracked.vehicleUuid())
                            && shouldRender(dispatcher, vehicle, frustum, cameraPosition)) {
                        Vec3 vehiclePosition = tracked.renderVehiclePosition(now);
                        poseStack.pushPose();
                        dispatcher.render(
                                vehicle,
                                vehiclePosition.x - cameraPosition.x,
                                vehiclePosition.y - cameraPosition.y,
                                vehiclePosition.z - cameraPosition.z,
                                tracked.renderVehicleYaw(now),
                                partialTick,
                                poseStack,
                                consumers,
                                LightTexture.FULL_BRIGHT
                        );
                        poseStack.popPose();
                    }
                } else {
                    proxy.stopRiding();
                }
            } else {
                proxy.stopRiding();
            }

            if (shouldRender(dispatcher, proxy, frustum, cameraPosition)) {
                poseStack.pushPose();
                dispatcher.render(
                        proxy,
                        position.x - cameraPosition.x,
                        position.y - cameraPosition.y,
                        position.z - cameraPosition.z,
                        tracked.renderBodyYaw(now),
                        partialTick,
                        poseStack,
                        consumers,
                        LightTexture.FULL_BRIGHT
                );
                poseStack.popPose();

                if (!loggedFirstSubmission) {
                    LOGGER.info(
                            "Submitted first far player render proxy: name={}, distance={}",
                            tracked.name(),
                            Math.round(Math.sqrt(distanceSquared))
                    );
                    loggedFirstSubmission = true;
                }
            }
        }
        consumers.endBatch();

        proxies.entrySet().removeIf(entry -> {
            if (activePlayers.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().stopRiding();
            return true;
        });
        vehicles.entrySet().removeIf(entry -> {
            if (activeVehicles.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().ejectPassengers();
            return true;
        });
    }

    private static boolean shouldRender(
            EntityRenderDispatcher dispatcher,
            Entity entity,
            Frustum frustum,
            Vec3 cameraPosition
    ) {
        return frustum == null || dispatcher.shouldRender(
                entity,
                frustum,
                cameraPosition.x,
                cameraPosition.y,
                cameraPosition.z
        );
    }

    private static Entity createVehicleProxy(ClientLevel level, String entityTypeId) {
        ResourceLocation typeId = ResourceLocation.tryParse(entityTypeId);
        if (typeId == null) {
            return null;
        }
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(typeId);
        if (entityType == null) {
            return null;
        }
        try {
            Entity entity = entityType.create(level);
            if (entity == null) {
                return null;
            }
            entity.setId(nextProxyEntityId());
            entity.noPhysics = true;
            entity.setNoGravity(true);
            entity.setInvisible(false);
            return entity;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static int nextProxyEntityId() {
        return NEXT_PROXY_ENTITY_ID.getAndUpdate(current ->
                current == Integer.MAX_VALUE ? 1_000_000_000 : current + 1
        );
    }

    private static void applyVehicleState(Entity vehicle, TrackedFarPlayer tracked, long now) {
        Vec3 position = tracked.renderVehiclePosition(now);
        float yaw = tracked.renderVehicleYaw(now);
        float pitch = tracked.renderVehiclePitch(now);
        vehicle.setOldPosAndRot();
        vehicle.xo = position.x;
        vehicle.yo = position.y;
        vehicle.zo = position.z;
        vehicle.xOld = position.x;
        vehicle.yOld = position.y;
        vehicle.zOld = position.z;
        vehicle.moveTo(position.x, position.y, position.z, yaw, pitch);
        vehicle.setYRot(yaw);
        vehicle.yRotO = yaw;
        vehicle.setXRot(pitch);
        vehicle.xRotO = pitch;
    }

    private static Pose resolvePose(TrackedFarPlayer tracked) {
        if (tracked.gliding()) {
            return Pose.FALL_FLYING;
        }
        if (tracked.swimming()) {
            return Pose.SWIMMING;
        }
        if (tracked.sneaking()) {
            return Pose.CROUCHING;
        }
        return Pose.STANDING;
    }

    private static final class FarPlayerRenderProxy extends RemotePlayer {
        private final UUID trackedUuid;
        private int maximumRenderDistanceBlocks;
        private Vec3 lastWalkAnimationPosition;
        private int lastWalkAnimationTick = Integer.MIN_VALUE;
        private FarPlayerMetadata appliedMetadata;
        private boolean appliedNameTagVisibility;

        private FarPlayerRenderProxy(ClientLevel level, UUID trackedUuid, String name) {
            super(level, new GameProfile(trackedUuid, name));
            this.trackedUuid = trackedUuid;
            this.setId(nextProxyEntityId());
            this.noPhysics = true;
            this.setNoGravity(true);
            this.setInvisible(false);
            this.setGlowingTag(true);
        }

        void apply(
                TrackedFarPlayer tracked,
                Vec3 position,
                boolean renderNameTags,
                int maximumRenderDistanceBlocks,
                boolean allowWalkAnimation,
                int animationTick,
                long now
        ) {
            float bodyYaw = tracked.renderBodyYaw(now);
            float headYaw = tracked.renderHeadYaw(now);
            float pitch = tracked.renderPitch(now);

            this.maximumRenderDistanceBlocks = maximumRenderDistanceBlocks;
            this.tickCount = animationTick;
            this.setOldPosAndRot();
            this.xo = position.x;
            this.yo = position.y;
            this.zo = position.z;
            this.xOld = position.x;
            this.yOld = position.y;
            this.zOld = position.z;
            this.moveTo(position.x, position.y, position.z, bodyYaw, pitch);
            this.setYRot(bodyYaw);
            this.yRotO = bodyYaw;
            this.setXRot(pitch);
            this.xRotO = pitch;
            this.setYBodyRot(bodyYaw);
            this.yBodyRotO = bodyYaw;
            this.setYHeadRot(headYaw);
            this.yHeadRotO = headYaw;
            this.setShiftKeyDown(tracked.sneaking());
            this.setSwimming(tracked.swimming());
            this.setPose(resolvePose(tracked));
            applyMetadata(tracked.metadata());
            if (appliedNameTagVisibility != renderNameTags) {
                this.setCustomNameVisible(renderNameTags);
                appliedNameTagVisibility = renderNameTags;
            }
            updateWalkAnimation(position, tracked, allowWalkAnimation, animationTick);
        }

        private void applyMetadata(FarPlayerMetadata metadata) {
            if (metadata.equals(appliedMetadata)) {
                return;
            }
            appliedMetadata = metadata;
            this.setItemSlot(EquipmentSlot.MAINHAND, createItemStack(metadata.mainHand()));
            this.setItemSlot(EquipmentSlot.OFFHAND, createItemStack(metadata.offHand()));
            this.setItemSlot(EquipmentSlot.FEET, createItemStack(metadata.feet()));
            this.setItemSlot(EquipmentSlot.LEGS, createItemStack(metadata.legs()));
            this.setItemSlot(EquipmentSlot.CHEST, createItemStack(metadata.chest()));
            this.setItemSlot(EquipmentSlot.HEAD, createItemStack(metadata.head()));
            this.setCustomName(Component.literal(metadata.name()));
        }

        private void updateWalkAnimation(
                Vec3 position,
                TrackedFarPlayer tracked,
                boolean allowWalkAnimation,
                int animationTick
        ) {
            if (lastWalkAnimationPosition == null) {
                lastWalkAnimationPosition = position;
                lastWalkAnimationTick = animationTick;
                stopWalkAnimation();
                return;
            }
            if (animationTick == lastWalkAnimationTick) {
                return;
            }

            lastWalkAnimationTick = animationTick;
            if (!allowWalkAnimation || tracked.gliding() || tracked.swimming() || tracked.hasVehicle()) {
                stopWalkAnimation();
                lastWalkAnimationPosition = position;
                return;
            }

            float movement = (float) Mth.length(
                    position.x - lastWalkAnimationPosition.x,
                    0.0D,
                    position.z - lastWalkAnimationPosition.z
            );
            float walkSpeed = Math.min(movement * 4.0F, 1.0F);
            this.walkAnimation.update(walkSpeed, WALK_ANIMATION_SCALE);
            lastWalkAnimationPosition = position;
        }

        private void stopWalkAnimation() {
            this.walkAnimation.setSpeed(0.0F);
            this.walkAnimation.update(0.0F, WALK_ANIMATION_SCALE);
        }

        @Override
        protected PlayerInfo getPlayerInfo() {
            ClientPacketListener connection = Minecraft.getInstance().getConnection();
            if (connection == null) {
                return super.getPlayerInfo();
            }
            PlayerInfo info = connection.getPlayerInfo(trackedUuid);
            return info != null ? info : super.getPlayerInfo();
        }

        @Override
        public boolean shouldRenderAtSqrDistance(double distanceSquared) {
            double maxDistance = Math.max(64, maximumRenderDistanceBlocks);
            return distanceSquared <= maxDistance * maxDistance;
        }
    }

    private static ItemStack createItemStack(FarItemSnapshot snapshot) {
        if (snapshot.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation itemId = ResourceLocation.tryParse(snapshot.itemId());
        if (itemId == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item, snapshot.count());
    }
}
