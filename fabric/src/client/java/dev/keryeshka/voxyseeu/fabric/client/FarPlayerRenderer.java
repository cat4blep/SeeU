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
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

final class FarPlayerRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("VoxySeeU");
    private static final long VISIBILITY_CACHE_NANOS = TimeUnit.MILLISECONDS.toNanos(175L);
    private static final double RAYCAST_END_MARGIN = 0.35D;
    private static final double RAYCAST_START_OFFSET = 0.05D;

    private final FarPlayerTracker tracker;
    private final VoxySeeUClientConfig config;
    private final VoxyAccess voxyAccess = new VoxyAccess();
    private final Map<UUID, FarPlayerRenderProxy> proxies = new HashMap<>();
    private final Map<UUID, VisibilityDecision> visibility = new HashMap<>();
    private boolean loggedFirstSubmission;

    FarPlayerRenderer(FarPlayerTracker tracker, VoxySeeUClientConfig config) {
        this.tracker = tracker;
        this.config = config;
    }

    void clear() {
        proxies.clear();
        visibility.clear();
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

            VisibilityDecision decision = visibility.computeIfAbsent(tracked.uuid(), ignored -> new VisibilityDecision());
            if (decision.shouldRefresh(now, cameraPosition, position)) {
                decision.recompute(now, cameraPosition, position, canRenderProxy(level, tracked, cameraPosition, position));
            }
            if (!decision.shouldRender()) {
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
        visibility.keySet().removeIf(uuid -> !active.contains(uuid));
    }

    private boolean canRenderProxy(ClientLevel level, TrackedFarPlayer tracked, Vec3 cameraPosition, Vec3 position) {
        Vec3[] visibilitySamples = visibilitySamples(tracked, position);
        try (VoxyAccess.RaycastSession raycast = voxyAccess.openRaycast(level)) {
            for (Vec3 sample : visibilitySamples) {
                int chunkX = Mth.floor(sample.x) >> 4;
                int chunkZ = Mth.floor(sample.z) >> 4;
                if (level.hasChunk(chunkX, chunkZ)) {
                    if (!isOccluded(level, cameraPosition, sample, raycast)) {
                        return true;
                    }
                    continue;
                }
                if (!raycast.hasRenderableData(sample)
                        || !voxyAccess.hasDepthSupport(level, sample)
                        || voxyAccess.isOccludedByDepth(level, sample)) {
                    continue;
                }
                if (!isOccluded(level, cameraPosition, sample, raycast)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Vec3[] visibilitySamples(TrackedFarPlayer tracked, Vec3 position) {
        double feetOffset = 0.15D;
        double bodyOffset = tracked.gliding() || tracked.swimming()
                ? 0.65D
                : tracked.sneaking() ? 1.0D : 1.15D;
        double headOffset = tracked.gliding() || tracked.swimming()
                ? 0.9D
                : tracked.sneaking() ? 1.35D : 1.62D;
        return new Vec3[] {
                position.add(0.0D, headOffset, 0.0D),
                position.add(0.0D, bodyOffset, 0.0D),
                position.add(0.0D, feetOffset, 0.0D)
        };
    }

    private static boolean isOccluded(ClientLevel level, Vec3 start, Vec3 target, VoxyAccess.RaycastSession raycast) {
        Vec3 delta = target.subtract(start);
        double distance = delta.length();
        if (distance <= 0.001D) {
            return false;
        }

        Vec3 direction = delta.scale(1.0D / distance);
        Vec3 rayStart = start.add(direction.scale(RAYCAST_START_OFFSET));
        double maxDistance = Math.max(0.0D, distance - RAYCAST_END_MARGIN);
        if (maxDistance <= 0.0D) {
            return false;
        }

        int x = Mth.floor(rayStart.x);
        int y = Mth.floor(rayStart.y);
        int z = Mth.floor(rayStart.z);
        int targetX = Mth.floor(target.x);
        int targetY = Mth.floor(target.y);
        int targetZ = Mth.floor(target.z);

        int stepX = Integer.compare((int) Math.signum(direction.x), 0);
        int stepY = Integer.compare((int) Math.signum(direction.y), 0);
        int stepZ = Integer.compare((int) Math.signum(direction.z), 0);

        double tMaxX = stepDistance(rayStart.x, direction.x, x, stepX);
        double tMaxY = stepDistance(rayStart.y, direction.y, y, stepY);
        double tMaxZ = stepDistance(rayStart.z, direction.z, z, stepZ);
        double tDeltaX = deltaDistance(direction.x);
        double tDeltaY = deltaDistance(direction.y);
        double tDeltaZ = deltaDistance(direction.z);

        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
        double traveled = 0.0D;
        while (traveled <= maxDistance) {
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                x += stepX;
                traveled = tMaxX;
                tMaxX += tDeltaX;
            } else if (tMaxY <= tMaxZ) {
                y += stepY;
                traveled = tMaxY;
                tMaxY += tDeltaY;
            } else {
                z += stepZ;
                traveled = tMaxZ;
                tMaxZ += tDeltaZ;
            }

            if (traveled > maxDistance || (x == targetX && y == targetY && z == targetZ)) {
                return false;
            }
            if (isOpaque(level, blockPos.set(x, y, z), raycast)) {
                return true;
            }
        }
        return false;
    }

    private static double stepDistance(double origin, double direction, int block, int step) {
        if (step == 0) {
            return Double.POSITIVE_INFINITY;
        }
        double boundary = step > 0 ? block + 1.0D : block;
        return (boundary - origin) / direction;
    }

    private static double deltaDistance(double direction) {
        return direction == 0.0D ? Double.POSITIVE_INFINITY : Math.abs(1.0D / direction);
    }

    private static boolean isOpaque(ClientLevel level, BlockPos pos, VoxyAccess.RaycastSession raycast) {
        if (pos.getY() < level.getMinY() || pos.getY() >= level.getMaxY()) {
            return false;
        }

        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        if (level.hasChunk(chunkX, chunkZ)) {
            BlockState state = level.getBlockState(pos);
            return !state.isAir() && state.canOcclude() && state.isSolidRender();
        }
        return raycast.sampleOpacity(pos.getX(), pos.getY(), pos.getZ()) > 0;
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

    private static final class VisibilityDecision {
        private long evaluatedAt;
        private int cameraX;
        private int cameraY;
        private int cameraZ;
        private int targetX;
        private int targetY;
        private int targetZ;
        private boolean shouldRender;

        private boolean shouldRefresh(long now, Vec3 cameraPosition, Vec3 targetPosition) {
            return now - evaluatedAt > VISIBILITY_CACHE_NANOS
                    || cameraX != Mth.floor(cameraPosition.x)
                    || cameraY != Mth.floor(cameraPosition.y)
                    || cameraZ != Mth.floor(cameraPosition.z)
                    || targetX != Mth.floor(targetPosition.x)
                    || targetY != Mth.floor(targetPosition.y)
                    || targetZ != Mth.floor(targetPosition.z);
        }

        private void recompute(long now, Vec3 cameraPosition, Vec3 targetPosition, boolean shouldRender) {
            this.evaluatedAt = now;
            this.cameraX = Mth.floor(cameraPosition.x);
            this.cameraY = Mth.floor(cameraPosition.y);
            this.cameraZ = Mth.floor(cameraPosition.z);
            this.targetX = Mth.floor(targetPosition.x);
            this.targetY = Mth.floor(targetPosition.y);
            this.targetZ = Mth.floor(targetPosition.z);
            this.shouldRender = shouldRender;
        }

        private boolean shouldRender() {
            return shouldRender;
        }
    }
}
