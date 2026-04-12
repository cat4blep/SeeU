package dev.keryeshka.voxyseeu.fabric.client;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.keryeshka.voxyseeu.fabric.client.config.VoxySeeUClientConfig;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class FarPlayerRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("VoxySeeU");

    private final FarPlayerTracker tracker;
    private final VoxySeeUClientConfig config;
    private final Map<UUID, FarPlayerRenderProxy> proxies = new HashMap<>();
    private boolean loggedFirstSubmission;

    FarPlayerRenderer(FarPlayerTracker tracker, VoxySeeUClientConfig config) {
        this.tracker = tracker;
        this.config = config;
    }

    void clear() {
        proxies.clear();
        loggedFirstSubmission = false;
    }

    void render(LevelRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer localPlayer = minecraft.player;
        PoseStack poseStack = context.poseStack();
        if (level == null || localPlayer == null || poseStack == null || context.submitNodeCollector() == null) {
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
        int animationTick = localPlayer.tickCount;
        long now = System.nanoTime();

        Set<UUID> active = new HashSet<>();
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

            proxy.apply(tracked, position, config.renderNameTags, config.maximumRenderDistanceBlocks, animationTick, now);
            active.add(tracked.uuid());

            var renderState = dispatcher.extractEntity(proxy, partialTick);
            dispatcher.submit(
                    renderState,
                    context.levelState().cameraRenderState,
                    position.x - cameraPosition.x,
                    position.y - cameraPosition.y,
                    position.z - cameraPosition.z,
                    poseStack,
                    context.submitNodeCollector()
            );

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
                int animationTick,
                long now
        ) {
            float bodyYaw = tracked.renderBodyYaw(now);
            float headYaw = tracked.renderHeadYaw(now);
            float pitch = tracked.renderPitch(now);

            this.maximumRenderDistanceBlocks = maximumRenderDistanceBlocks;
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
            this.setCustomName(Component.literal(tracked.name()));
            this.setCustomNameVisible(renderNameTags);
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
}
