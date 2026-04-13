package dev.keryeshka.voxyseeu.fabric.client;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.keryeshka.voxyseeu.common.protocol.FarItemSnapshot;
import dev.keryeshka.voxyseeu.fabric.client.config.VoxySeeUClientConfig;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
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

final class FarPlayerRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("SeeU");
    private static final float WALK_ANIMATION_SCALE = 0.4F;

    private final FarPlayerTracker tracker;
    private final VoxySeeUClientConfig config;
    private final Map<UUID, FarPlayerRenderProxy> proxies = new HashMap<>();
    private final Map<UUID, Entity> vehicles = new HashMap<>();
    private boolean loggedFirstSubmission;

    FarPlayerRenderer(FarPlayerTracker tracker, VoxySeeUClientConfig config) {
        this.tracker = tracker;
        this.config = config;
    }

    void clear() {
        for (Entity vehicle : vehicles.values()) {
            vehicle.ejectPassengers();
        }
        proxies.clear();
        vehicles.clear();
        loggedFirstSubmission = false;
    }

    void render(WorldRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer localPlayer = minecraft.player;
        PoseStack poseStack = context.matrixStack();
        var consumers = context.consumers();
        if (level == null || localPlayer == null || poseStack == null || consumers == null) {
            clear();
            return;
        }

        String currentDimension = level.dimension().location().toString();
        if (!currentDimension.equals(tracker.dimensionKey())) {
            clear();
            return;
        }

        Vec3 cameraPosition = context.camera().getPosition();
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        float partialTick = 0.0F;
        int animationTick = localPlayer.tickCount;
        long now = System.nanoTime();

        Set<UUID> active = new HashSet<>();
        Set<UUID> activeVehicles = new HashSet<>();
        Set<UUID> submittedVehicles = new HashSet<>();
        for (Entity vehicle : vehicles.values()) {
            vehicle.ejectPassengers();
        }
        for (TrackedFarPlayer tracked : tracker.players()) {
            Vec3 position = tracked.renderPosition(now);
            double distance = position.distanceTo(localPlayer.position());
            if (distance < config.minimumProxyDistanceBlocks || distance > config.maximumRenderDistanceBlocks) {
                continue;
            }

            int chunkX = Mth.floor(position.x) >> 4;
            int chunkZ = Mth.floor(position.z) >> 4;
            boolean chunkLoaded = level.hasChunk(chunkX, chunkZ);
            boolean realPlayerStillPresent = level.getPlayerByUUID(tracked.uuid()) != null;
            int vanillaRenderDistanceBlocks = minecraft.options.getEffectiveRenderDistance() * 16;

            if (realPlayerStillPresent && chunkLoaded && distance <= vanillaRenderDistanceBlocks + 16.0D) {
                continue;
            }

            FarPlayerRenderProxy proxy = proxies.compute(tracked.uuid(), (uuid, current) -> {
                if (current == null || current.level() != level) {
                    return new FarPlayerRenderProxy(level, tracked.uuid(), tracked.name());
                }
                return current;
            });

            boolean allowWalkAnimation = config.maximumAnimationDistanceBlocks > 0
                    && distance <= config.maximumAnimationDistanceBlocks;
            proxy.apply(
                    tracked,
                    position,
                    config.renderNameTags,
                    config.maximumRenderDistanceBlocks,
                    allowWalkAnimation,
                    animationTick,
                    now
            );
            active.add(tracked.uuid());

            if (tracked.hasVehicle()) {
                Entity vehicle = vehicles.compute(tracked.vehicleUuid(), (uuid, current) -> {
                    if (current == null || current.level() != level || !BuiltInRegistries.ENTITY_TYPE.getKey(current.getType()).toString().equals(tracked.vehicleTypeId())) {
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
                    if (submittedVehicles.add(tracked.vehicleUuid())) {
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
                        Math.round(distance)
                );
                loggedFirstSubmission = true;
            }
        }

        proxies.keySet().removeIf(uuid -> !active.contains(uuid));
        vehicles.entrySet().removeIf(entry -> !activeVehicles.contains(entry.getKey()));
    }

    private static Entity createVehicleProxy(ClientLevel level, String entityTypeId) {
        try {
            EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(entityTypeId));
            if (entityType == null) {
                return null;
            }
            Entity entity = entityType.create(level);
            if (entity == null) {
                return null;
            }
            entity.noPhysics = true;
            entity.setNoGravity(true);
            entity.setInvisible(false);
            return entity;
        } catch (Exception ignored) {
            return null;
        }
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

        private FarPlayerRenderProxy(ClientLevel level, UUID trackedUuid, String name) {
            super(level, new GameProfile(trackedUuid, name));
            this.trackedUuid = trackedUuid;
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
            this.setItemSlot(EquipmentSlot.MAINHAND, createItemStack(tracked.mainHand()));
            this.setItemSlot(EquipmentSlot.OFFHAND, createItemStack(tracked.offHand()));
            this.setItemSlot(EquipmentSlot.FEET, createItemStack(tracked.feet()));
            this.setItemSlot(EquipmentSlot.LEGS, createItemStack(tracked.legs()));
            this.setItemSlot(EquipmentSlot.CHEST, createItemStack(tracked.chest()));
            this.setItemSlot(EquipmentSlot.HEAD, createItemStack(tracked.head()));
            this.setCustomName(Component.literal(tracked.name()));
            this.setCustomNameVisible(renderNameTags);
            updateWalkAnimation(position, tracked, allowWalkAnimation, animationTick);
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
                this.walkAnimation.setSpeed(0.0F);
                this.walkAnimation.update(0.0F, WALK_ANIMATION_SCALE);
                return;
            }
            if (animationTick == lastWalkAnimationTick) {
                return;
            }

            lastWalkAnimationTick = animationTick;
            if (!allowWalkAnimation || tracked.gliding() || tracked.swimming() || tracked.hasVehicle()) {
                this.walkAnimation.setSpeed(0.0F);
                this.walkAnimation.update(0.0F, WALK_ANIMATION_SCALE);
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
        if (snapshot == null || snapshot.isEmpty()) {
            return ItemStack.EMPTY;
        }
        try {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(snapshot.itemId()));
            if (item == null || item == Items.AIR) {
                return ItemStack.EMPTY;
            }
            return new ItemStack(item, Math.max(1, snapshot.count()));
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }
    }
}
