package dev.keryeshka.seeu.extra.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.keryeshka.seeu.extra.protocol.ClientOffer;
import dev.keryeshka.seeu.extra.protocol.EntityFlags;
import dev.keryeshka.seeu.extra.protocol.EntitySnapshot;
import dev.keryeshka.seeu.extra.protocol.EquipmentSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class ExtraEntityRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("SeeU Extra");
    private static final AtomicInteger NEXT_PROXY_ID = new AtomicInteger(1_100_000_000);

    private final ExtraEntityTracker tracker;
    private final SeeUExtraClientConfig config;
    private final Map<UUID, ProxyEntry> proxies = new HashMap<>();
    private final Set<String> quarantinedTypes = new HashSet<>();
    private Frustum frustum;
    private long frame;

    public ExtraEntityRenderer(ExtraEntityTracker tracker, SeeUExtraClientConfig config) {
        this.tracker = tracker;
        this.config = config;
    }

    public void clear() {
        proxies.clear();
        quarantinedTypes.clear();
        frustum = null;
        frame = 0;
    }

    public void updateFrustum(Frustum frustum) {
        this.frustum = frustum;
    }

    public void render(
            PoseStack poseStack,
            LevelRenderState levelRenderState,
            SubmitNodeCollector submitNodeCollector
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer viewer = minecraft.player;
        ClientOffer offer = config.offer();
        if (!offer.enabled() || level == null || viewer == null || submitNodeCollector == null) {
            proxies.clear();
            return;
        }
        if (!level.dimension().identifier().toString().equals(tracker.dimensionKey())) {
            proxies.clear();
            return;
        }

        long currentFrame = nextFrame();
        long now = System.nanoTime();
        double minimumDistanceSquared = square(offer.minimumDistanceBlocks());
        double maximumDistanceSquared = square(offer.maximumDistanceBlocks());
        Vec3 cameraPosition = minecraft.gameRenderer.getMainCamera().position();
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Frustum currentFrustum = frustum;

        for (TrackedExtraEntity tracked : tracker.entities()) {
            InterpolatedEntityState state = tracked.sample(now);
            EntitySnapshot snapshot = state.snapshot();
            double distanceSquared = viewer.distanceToSqr(state.position());
            if (distanceSquared < minimumDistanceSquared || distanceSquared > maximumDistanceSquared) {
                continue;
            }
            if (level.getEntity(snapshot.uuid()) != null) {
                proxies.remove(snapshot.uuid());
                continue;
            }
            if (quarantinedTypes.contains(snapshot.typeId())) {
                continue;
            }

            try {
                ProxyEntry proxy = proxies.get(snapshot.uuid());
                if (proxy == null || proxy.level != level || !proxy.typeId.equals(snapshot.typeId())) {
                    proxy = createProxy(level, snapshot);
                    proxies.put(snapshot.uuid(), proxy);
                }
                proxy.lastSeenFrame = currentFrame;
                applyPosition(proxy.entity, state);
                if (proxy.appliedRevision != state.revision()) {
                    applySnapshotState(proxy.entity, snapshot);
                    proxy.appliedRevision = state.revision();
                }

                if (currentFrustum != null && !currentFrustum.isVisible(proxy.entity.getBoundingBox())) {
                    continue;
                }

                var renderState = dispatcher.extractEntity(proxy.entity, partialTick);
                Vec3 position = state.position();
                dispatcher.submit(
                        renderState,
                        levelRenderState.cameraRenderState,
                        position.x - cameraPosition.x,
                        position.y - cameraPosition.y,
                        position.z - cameraPosition.z,
                        poseStack,
                        submitNodeCollector
                );
            } catch (RuntimeException | LinkageError failure) {
                quarantine(snapshot.typeId(), failure);
            }
        }

        proxies.values().removeIf(proxy -> proxy.lastSeenFrame != currentFrame);
    }

    private ProxyEntry createProxy(ClientLevel level, EntitySnapshot snapshot) {
        Identifier identifier = Identifier.tryParse(snapshot.typeId());
        if (identifier == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(identifier)) {
            throw new IllegalArgumentException("Client registry has no entity type " + snapshot.typeId());
        }
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getValue(identifier);
        if (entityType == null) {
            throw new IllegalArgumentException("Client registry returned no entity type " + snapshot.typeId());
        }
        Entity entity = entityType.create(level, EntitySpawnReason.LOAD);
        if (entity == null) {
            throw new IllegalStateException("Entity type returned no proxy for " + snapshot.typeId());
        }

        entity.setId(nextProxyId());
        entity.setUUID(snapshot.uuid());
        entity.noPhysics = true;
        entity.setNoGravity(true);
        return new ProxyEntry(level, snapshot.typeId(), entity);
    }

    private static void applyPosition(Entity entity, InterpolatedEntityState state) {
        Vec3 position = state.position();
        entity.setOldPosAndRot(position, state.bodyYaw(), state.pitch());
        entity.xo = position.x;
        entity.yo = position.y;
        entity.zo = position.z;
        entity.xOld = position.x;
        entity.yOld = position.y;
        entity.zOld = position.z;
        entity.snapTo(position, state.bodyYaw(), state.pitch());
        entity.setYRot(state.bodyYaw());
        entity.yRotO = state.bodyYaw();
        entity.setXRot(state.pitch());
        entity.xRotO = state.pitch();
        entity.setYBodyRot(state.bodyYaw());
        entity.setYHeadRot(state.headYaw());
        if (entity instanceof LivingEntity living) {
            living.yBodyRotO = state.bodyYaw();
            living.yHeadRotO = state.headYaw();
        }
        entity.tickCount = state.age();
    }

    private static void applySnapshotState(Entity entity, EntitySnapshot snapshot) {
        entity.setDeltaMovement(snapshot.velocityX(), snapshot.velocityY(), snapshot.velocityZ());
        entity.setPose(Pose.valueOf(snapshot.pose()));
        entity.setSharedFlagOnFire(EntityFlags.has(snapshot.flags(), EntityFlags.ON_FIRE));
        entity.setInvisible(EntityFlags.has(snapshot.flags(), EntityFlags.INVISIBLE));
        entity.setGlowingTag(EntityFlags.has(snapshot.flags(), EntityFlags.GLOWING));
        entity.setShiftKeyDown(EntityFlags.has(snapshot.flags(), EntityFlags.CROUCHING));
        entity.setSprinting(EntityFlags.has(snapshot.flags(), EntityFlags.SPRINTING));
        entity.setSwimming(EntityFlags.has(snapshot.flags(), EntityFlags.SWIMMING));
        if (entity instanceof Mob mob) {
            mob.setBaby(EntityFlags.has(snapshot.flags(), EntityFlags.BABY));
        }
        if (entity instanceof LivingEntity living) {
            living.setItemSlot(EquipmentSlot.MAINHAND, itemStack(snapshot.mainHand()));
            living.setItemSlot(EquipmentSlot.OFFHAND, itemStack(snapshot.offHand()));
            living.setItemSlot(EquipmentSlot.FEET, itemStack(snapshot.feet()));
            living.setItemSlot(EquipmentSlot.LEGS, itemStack(snapshot.legs()));
            living.setItemSlot(EquipmentSlot.CHEST, itemStack(snapshot.chest()));
            living.setItemSlot(EquipmentSlot.HEAD, itemStack(snapshot.head()));
        }
    }

    private static ItemStack itemStack(EquipmentSnapshot equipment) {
        if (equipment.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Identifier identifier = Identifier.tryParse(equipment.itemId());
        if (identifier == null || !BuiltInRegistries.ITEM.containsKey(identifier)) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.getValue(identifier);
        return item == null ? ItemStack.EMPTY : new ItemStack(item, equipment.count());
    }

    private void quarantine(String typeId, Throwable failure) {
        if (!quarantinedTypes.add(typeId)) {
            return;
        }
        proxies.entrySet().removeIf(entry -> entry.getValue().typeId.equals(typeId));
        LOGGER.warn("Quarantined entity type {} after a proxy failure", typeId, failure);
    }

    private long nextFrame() {
        if (frame == Long.MAX_VALUE) {
            frame = 1;
            for (ProxyEntry proxy : proxies.values()) {
                proxy.lastSeenFrame = 0;
            }
        } else {
            frame++;
        }
        return frame;
    }

    private static int nextProxyId() {
        return NEXT_PROXY_ID.getAndUpdate(current ->
                current == Integer.MAX_VALUE ? 1_100_000_000 : current + 1
        );
    }

    private static double square(int value) {
        return (double) value * value;
    }

    private static final class ProxyEntry {
        private final ClientLevel level;
        private final String typeId;
        private final Entity entity;
        private long appliedRevision = Long.MIN_VALUE;
        private long lastSeenFrame;

        private ProxyEntry(ClientLevel level, String typeId, Entity entity) {
            this.level = level;
            this.typeId = typeId;
            this.entity = entity;
        }
    }
}
