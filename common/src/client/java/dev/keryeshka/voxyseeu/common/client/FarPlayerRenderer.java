package dev.keryeshka.voxyseeu.common.client;

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
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
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

public final class FarPlayerRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("SeeU");
    private static final float WALK_ANIMATION_SCALE = 0.4F;
    private static final AtomicInteger NEXT_PROXY_ENTITY_ID = new AtomicInteger(1_000_000_000);

    private final FarPlayerTracker tracker;
    private final SeeUClientConfig config;
    private final Map<UUID, FarPlayerRenderProxy> proxies = new HashMap<>();
    private final Map<UUID, Entity> vehicles = new HashMap<>();
    private final Set<UUID> activePlayers = new HashSet<>();
    private final Set<UUID> activeVehicles = new HashSet<>();
    private final Set<UUID> submittedVehicles = new HashSet<>();
    private boolean loggedFirstSubmission;

    public FarPlayerRenderer(FarPlayerTracker tracker, SeeUClientConfig config) {
        this.tracker = tracker;
        this.config = config;
    }

    public void clear() {
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

    public void render(
            PoseStack poseStack,
            LevelRenderState levelRenderState,
            SubmitNodeCollector submitNodeCollector
    ) {
        if (!config.enabled) {
            clear();
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer localPlayer = minecraft.player;
        if (level == null || localPlayer == null) {
            clear();
            return;
        }

        String currentDimension = level.dimension().identifier().toString();
        if (!currentDimension.equals(tracker.dimensionKey())) {
            clear();
            return;
        }

        Vec3 cameraPosition = minecraft.gameRenderer.getMainCamera().position();
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Frustum frustum = levelRenderState.cameraRenderState.cullFrustum;
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
                    allowWalkAnimation,
                    animationTick,
                    now
            );
            activePlayers.add(tracked.uuid());

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
                    if (submittedVehicles.add(tracked.vehicleUuid())
                            && isVisible(frustum, vehicle)) {
                        var vehicleRenderState = dispatcher.extractEntity(vehicle, partialTick);
                        Vec3 vehiclePosition = tracked.renderVehiclePosition(now);
                        dispatcher.submit(
                                vehicleRenderState,
                                levelRenderState.cameraRenderState,
                                vehiclePosition.x - cameraPosition.x,
                                vehiclePosition.y - cameraPosition.y,
                                vehiclePosition.z - cameraPosition.z,
                                poseStack,
                                submitNodeCollector
                        );
                    }
                } else {
                    proxy.stopRiding();
                }
            } else {
                proxy.stopRiding();
            }

            if (isVisible(frustum, proxy)) {
                var renderState = dispatcher.extractEntity(proxy, partialTick);
                dispatcher.submit(
                        renderState,
                        levelRenderState.cameraRenderState,
                        position.x - cameraPosition.x,
                        position.y - cameraPosition.y,
                        position.z - cameraPosition.z,
                        poseStack,
                        submitNodeCollector
                );
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

    private static Entity createVehicleProxy(ClientLevel level, String entityTypeId) {
        Identifier typeId = Identifier.tryParse(entityTypeId);
        if (typeId == null) {
            return null;
        }
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getValue(typeId);
        if (entityType == null) {
            return null;
        }
        Entity entity = entityType.create(level, EntitySpawnReason.LOAD);
        if (entity == null) {
            return null;
        }
        entity.setId(nextProxyEntityId());
        entity.noPhysics = true;
        entity.setNoGravity(true);
        entity.setInvisible(false);
        return entity;
    }

    private static boolean isVisible(Frustum frustum, Entity entity) {
        return frustum == null || frustum.isVisible(entity.getBoundingBox());
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
        vehicle.setOldPosAndRot(position, yaw, pitch);
        vehicle.xo = position.x;
        vehicle.yo = position.y;
        vehicle.zo = position.z;
        vehicle.xOld = position.x;
        vehicle.yOld = position.y;
        vehicle.zOld = position.z;
        vehicle.snapTo(position, yaw, pitch);
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
                boolean allowWalkAnimation,
                int animationTick,
                long now
        ) {
            float bodyYaw = tracked.renderBodyYaw(now);
            float headYaw = tracked.renderHeadYaw(now);
            float pitch = tracked.renderPitch(now);

            this.tickCount = animationTick;
            this.setOldPosAndRot(position, bodyYaw, pitch);
            this.xo = position.x;
            this.yo = position.y;
            this.zo = position.z;
            this.xOld = position.x;
            this.yOld = position.y;
            this.zOld = position.z;
            this.snapTo(position, bodyYaw, pitch);
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
                this.walkAnimation.stop();
                return;
            }
            if (animationTick == lastWalkAnimationTick) {
                return;
            }

            lastWalkAnimationTick = animationTick;
            if (!allowWalkAnimation || tracked.gliding() || tracked.swimming() || tracked.hasVehicle()) {
                this.walkAnimation.stop();
                lastWalkAnimationPosition = position;
                return;
            }

            float movement = (float) Mth.length(
                    position.x - lastWalkAnimationPosition.x,
                    0.0D,
                    position.z - lastWalkAnimationPosition.z
            );
            float walkSpeed = Math.min(movement * 4.0F, 1.0F);
            this.walkAnimation.update(walkSpeed, WALK_ANIMATION_SCALE, 1.0F);
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
    }

    private static ItemStack createItemStack(FarItemSnapshot snapshot) {
        if (snapshot.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Identifier itemId = Identifier.tryParse(snapshot.itemId());
        if (itemId == null) {
            return ItemStack.EMPTY;
        }
        Item registeredItem = BuiltInRegistries.ITEM.getValue(itemId);
        if (registeredItem == null || registeredItem == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(registeredItem, snapshot.count());
    }
}
