package dev.keryeshka.voxyseeu.fabric.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

final class VoxyAccess {
    private static final Logger LOGGER = LoggerFactory.getLogger("VoxySeeU");
    private static final int DEPTH_SAMPLE_RADIUS = 1;
    private static final float DEPTH_OCCLUSION_BIAS = 0.0035F;
    private static final float EMPTY_DEPTH_THRESHOLD = 0.0001F;
    private static final long NON_AIR_BLOCK_MASK = 140737354137600L;

    private final Reflection reflection;
    private Snapshot cachedSnapshot;
    private ClientLevel cachedLevel;
    private boolean loggedUnavailable;
    private boolean loggedAvailable;

    VoxyAccess() {
        this.reflection = Reflection.load();
    }

    boolean hasRenderableData(ClientLevel level, Vec3 position) {
        Snapshot snapshot = snapshot(level);
        return snapshot != null && snapshot.hasChunkVoxelData(position);
    }

    boolean hasDepthSupport(ClientLevel level, Vec3 position) {
        Snapshot snapshot = snapshot(level);
        return snapshot != null && snapshot.hasDepthSupport(position);
    }

    boolean isOccludedByDepth(ClientLevel level, Vec3 position) {
        Snapshot snapshot = snapshot(level);
        return snapshot != null && snapshot.isOccludedByDepth(position);
    }

    RaycastSession openRaycast(ClientLevel level) {
        return new RaycastSession(snapshot(level));
    }

    private Snapshot snapshot(ClientLevel level) {
        if (reflection == null) {
            if (!loggedUnavailable) {
                LOGGER.info("Voxy integration is unavailable; falling back to vanilla-only visibility checks.");
                loggedUnavailable = true;
            }
            return null;
        }
        if (cachedSnapshot != null && cachedLevel == level) {
            return cachedSnapshot;
        }

        try {
            Object renderSystem = reflection.getRenderSystem.invoke(null);
            Object engine = renderSystem != null
                    ? reflection.renderSystemGetEngine.invoke(renderSystem)
                    : reflection.ofEngineNullable.invoke(null, level);
            if (engine == null) {
                cachedSnapshot = null;
                cachedLevel = level;
                return null;
            }

            Object mapper = reflection.worldEngineGetMapper.invoke(engine);
            Snapshot snapshot = new Snapshot(
                    reflection,
                    renderSystem,
                    engine,
                    mapper,
                    level.getMinY(),
                    level.getMaxY()
            );
            cachedSnapshot = snapshot;
            cachedLevel = level;

            if (!loggedAvailable) {
                LOGGER.info("Detected Voxy integration; far players will follow stored Voxy chunk data and voxel occlusion.");
                loggedAvailable = true;
            }
            return snapshot;
        } catch (ReflectiveOperationException exception) {
            LOGGER.warn("Failed to resolve Voxy runtime integration, disabling Voxy-aware visibility checks.", exception);
            cachedSnapshot = null;
            cachedLevel = level;
            return null;
        }
    }

    private static long sectionIdForBlock(Snapshot snapshot, int blockX, int blockY, int blockZ, int lod) {
        int sectionShift = lod + 5;
        int sectionX = blockX >> sectionShift;
        int sectionY = blockY >> sectionShift;
        int sectionZ = blockZ >> sectionShift;
        return snapshot.sectionId(lod, sectionX, sectionY, sectionZ);
    }

    private static int localIndexForBlock(int blockX, int blockY, int blockZ, int lod) {
        int scaledX = blockX >> lod;
        int scaledY = blockY >> lod;
        int scaledZ = blockZ >> lod;
        return ((scaledY & 31) << 10) | ((scaledZ & 31) << 5) | (scaledX & 31);
    }

    private static final class Snapshot {
        private final Reflection reflection;
        private final Object renderSystem;
        private final Object engine;
        private final Object mapper;
        private final int minY;
        private final int maxY;
        private final int maxLodLayer;

        private Snapshot(
                Reflection reflection,
                Object renderSystem,
                Object engine,
                Object mapper,
                int minY,
                int maxY
        ) {
            this.reflection = reflection;
            this.renderSystem = renderSystem;
            this.engine = engine;
            this.mapper = mapper;
            this.minY = minY;
            this.maxY = maxY;
            this.maxLodLayer = reflection.maxLodLayer;
        }

        long sectionId(int level, int sectionX, int sectionY, int sectionZ) {
            try {
                return (long) reflection.worldEngineGetWorldSectionId.invoke(null, level, sectionX, sectionY, sectionZ);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unable to compute Voxy section id.", exception);
            }
        }

        boolean hasChunkVoxelData(Vec3 position) {
            int chunkX = Mth.floor(position.x) >> 4;
            int chunkZ = Mth.floor(position.z) >> 4;
            return hasChunkVoxelData(chunkX, chunkZ);
        }

        private boolean hasChunkVoxelData(int chunkX, int chunkZ) {
            int blockX = chunkX << 4;
            int blockZ = chunkZ << 4;
            int sectionX = blockX >> 5;
            int sectionZ = blockZ >> 5;
            int localXStart = blockX & 31;
            int localZStart = blockZ & 31;
            int localXEnd = localXStart + 15;
            int localZEnd = localZStart + 15;

            int minSectionY = minY >> 5;
            int maxSectionY = (maxY - 1) >> 5;
            for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
                CachedSection section;
                try {
                    section = acquireSection(sectionX, sectionY, sectionZ, 0);
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("Unable to inspect Voxy chunk voxel data.", exception);
                }
                try {
                    if (!section.present() || section.data() == null || section.data().length == 0) {
                        continue;
                    }
                    if (section.nonEmptyBlockCount() <= 0) {
                        continue;
                    }
                    for (int localY = 0; localY < 32; localY++) {
                        int yBase = localY << 10;
                        for (int localZ = localZStart; localZ <= localZEnd; localZ++) {
                            int zBase = yBase | (localZ << 5);
                            for (int localX = localXStart; localX <= localXEnd; localX++) {
                                long mapping = section.data()[zBase | localX];
                                if (mapping == reflection.unknownMapping) {
                                    continue;
                                }
                                if ((mapping & NON_AIR_BLOCK_MASK) != 0L) {
                                    return true;
                                }
                            }
                        }
                    }
                } finally {
                    section.release(reflection);
                }
            }
            return false;
        }

        boolean hasDepthSupport(Vec3 position) {
            return sceneDepth(position) > EMPTY_DEPTH_THRESHOLD;
        }

        boolean isOccludedByDepth(Vec3 position) {
            DepthSample sample = depthSample(position);
            return sample.sceneDepth() > EMPTY_DEPTH_THRESHOLD
                    && sample.playerDepth() + DEPTH_OCCLUSION_BIAS < sample.sceneDepth();
        }

        private float sceneDepth(Vec3 position) {
            return depthSample(position).sceneDepth();
        }

        private DepthSample depthSample(Vec3 position) {
            if (renderSystem == null) {
                return DepthSample.EMPTY;
            }

            try {
                Object viewport = reflection.renderSystemGetViewport.invoke(renderSystem);
                if (viewport == null) {
                    return DepthSample.EMPTY;
                }

                int width = (int) reflection.viewportWidthField.get(viewport);
                int height = (int) reflection.viewportHeightField.get(viewport);
                if (width <= 0 || height <= 0) {
                    return DepthSample.EMPTY;
                }

                Matrix4f mvp = (Matrix4f) reflection.viewportMvpField.get(viewport);
                Vector4f clip = new Vector4f((float) position.x, (float) position.y, (float) position.z, 1.0F);
                mvp.transform(clip);
                if (clip.w <= 0.0F) {
                    return DepthSample.EMPTY;
                }

                float inverseW = 1.0F / clip.w;
                float ndcX = clip.x * inverseW;
                float ndcY = clip.y * inverseW;
                float ndcZ = clip.z * inverseW;
                if (ndcX < -1.0F || ndcX > 1.0F || ndcY < -1.0F || ndcY > 1.0F || ndcZ < -1.0F || ndcZ > 1.0F) {
                    return DepthSample.EMPTY;
                }

                int pixelX = Mth.clamp((int) Math.floor((ndcX * 0.5F + 0.5F) * width), 0, width - 1);
                int pixelY = Mth.clamp((int) Math.floor((ndcY * 0.5F + 0.5F) * height), 0, height - 1);
                float playerDepth = ndcZ * 0.5F + 0.5F;
                float sceneDepth = readMaxSceneDepth(viewport, pixelX, pixelY, width, height);
                return new DepthSample(sceneDepth, playerDepth);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unable to query Voxy depth bounds.", exception);
            }
        }

        private float readMaxSceneDepth(Object viewport, int pixelX, int pixelY, int width, int height)
                throws ReflectiveOperationException {
            int minX = Math.max(0, pixelX - DEPTH_SAMPLE_RADIUS);
            int minY = Math.max(0, pixelY - DEPTH_SAMPLE_RADIUS);
            int maxX = Math.min(width - 1, pixelX + DEPTH_SAMPLE_RADIUS);
            int maxY = Math.min(height - 1, pixelY + DEPTH_SAMPLE_RADIUS);
            int sampleWidth = maxX - minX + 1;
            int sampleHeight = maxY - minY + 1;

            Object depthFramebuffer = reflection.viewportDepthBoundingBufferField.get(viewport);
            Object framebuffer = reflection.depthFramebufferFramebufferField.get(depthFramebuffer);
            int framebufferId = (int) reflection.glFramebufferIdField.get(framebuffer);

            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer depthBuffer = stack.mallocFloat(sampleWidth * sampleHeight);
                int previousReadFramebuffer = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
                GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, framebufferId);
                try {
                    GL11C.glReadPixels(
                            minX,
                            minY,
                            sampleWidth,
                            sampleHeight,
                            GL11C.GL_DEPTH_COMPONENT,
                            GL11C.GL_FLOAT,
                            depthBuffer
                    );
                } finally {
                    GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
                }

                float maxDepth = 0.0F;
                for (int index = 0; index < depthBuffer.limit(); index++) {
                    maxDepth = Math.max(maxDepth, depthBuffer.get(index));
                }
                return maxDepth;
            }
        }

        CachedSection acquireSection(int sectionX, int sectionY, int sectionZ, int lod) throws ReflectiveOperationException {
            long sectionId = sectionId(lod, sectionX, sectionY, sectionZ);
            Object section = reflection.worldEngineAcquireIfExists.invoke(engine, sectionX, sectionY, sectionZ, lod);
            if (section == null) {
                return CachedSection.missing(sectionId);
            }
            long[] data = (long[]) reflection.worldSectionRawData.invoke(section);
            int nonEmptyBlockCount = (int) reflection.worldSectionGetNonEmptyBlockCount.invoke(section);
            return new CachedSection(sectionId, section, data, nonEmptyBlockCount);
        }

        int opacity(long mapping) throws ReflectiveOperationException {
            if ((boolean) reflection.mapperIsAir.invoke(null, mapping)) {
                return 0;
            }
            return (int) reflection.mapperGetBlockStateOpacity.invoke(mapper, mapping);
        }
    }

    static final class RaycastSession implements AutoCloseable {
        private final Snapshot snapshot;
        private final Map<Long, CachedSection> sectionCache = new HashMap<>();

        private RaycastSession(Snapshot snapshot) {
            this.snapshot = snapshot;
        }

        int sampleOpacity(int blockX, int blockY, int blockZ) {
            if (snapshot == null || snapshot.mapper == null) {
                return 0;
            }

            for (int lod = 0; lod <= snapshot.maxLodLayer; lod++) {
                long sectionId = sectionIdForBlock(snapshot, blockX, blockY, blockZ, lod);
                CachedSection cached = sectionCache.get(sectionId);
                if (cached == null) {
                    try {
                        int sectionShift = lod + 5;
                        cached = snapshot.acquireSection(
                                blockX >> sectionShift,
                                blockY >> sectionShift,
                                blockZ >> sectionShift,
                                lod
                        );
                    } catch (ReflectiveOperationException exception) {
                        throw new IllegalStateException("Unable to acquire Voxy section for raycast.", exception);
                    }
                    sectionCache.put(sectionId, cached);
                }

                if (!cached.present() || cached.data() == null || cached.data().length == 0) {
                    continue;
                }

                long mapping = cached.data()[localIndexForBlock(blockX, blockY, blockZ, lod)];
                try {
                    return snapshot.opacity(mapping);
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("Unable to read Voxy mapping opacity.", exception);
                }
            }
            return 0;
        }

        boolean hasRenderableData(Vec3 position) {
            return snapshot != null && snapshot.hasChunkVoxelData(position);
        }

        @Override
        public void close() {
            for (CachedSection cached : sectionCache.values()) {
                cached.release(snapshot.reflection);
            }
            sectionCache.clear();
        }
    }

    private record CachedSection(long sectionId, Object section, long[] data, int nonEmptyBlockCount) {
        private static final Object MISSING_SECTION = new Object();

        private static CachedSection missing(long sectionId) {
            return new CachedSection(sectionId, MISSING_SECTION, null, 0);
        }

        private boolean present() {
            return section != MISSING_SECTION;
        }

        private void release(Reflection reflection) {
            if (!present()) {
                return;
            }
            try {
                reflection.worldSectionRelease.invoke(section);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unable to release Voxy section after use.", exception);
            }
        }
    }

    private record DepthSample(float sceneDepth, float playerDepth) {
        private static final DepthSample EMPTY = new DepthSample(0.0F, 0.0F);
    }

    private record Reflection(
            Method getRenderSystem,
            Method ofEngineNullable,
            Method renderSystemGetEngine,
            Method renderSystemGetViewport,
            Method worldEngineGetMapper,
            Method worldEngineAcquireIfExists,
            Method worldEngineGetWorldSectionId,
            Method worldSectionRawData,
            Method worldSectionRelease,
            Method worldSectionGetNonEmptyBlockCount,
            Method mapperGetBlockStateOpacity,
            Method mapperIsAir,
            Field viewportWidthField,
            Field viewportHeightField,
            Field viewportMvpField,
            Field viewportDepthBoundingBufferField,
            Field depthFramebufferFramebufferField,
            Field glFramebufferIdField,
            long unknownMapping,
            int maxLodLayer
    ) {
        private static Reflection load() {
            try {
                Class<?> iGetVoxyRenderSystemClass = Class.forName("me.cortex.voxy.client.core.IGetVoxyRenderSystem");
                Method getRenderSystem = iGetVoxyRenderSystemClass.getMethod("getNullable");

                Class<?> worldIdentifierClass = Class.forName("me.cortex.voxy.commonImpl.WorldIdentifier");
                Method ofEngineNullable = worldIdentifierClass.getMethod("ofEngineNullable", Level.class);

                Class<?> renderSystemClass = Class.forName("me.cortex.voxy.client.core.VoxyRenderSystem");
                Method renderSystemGetEngine = renderSystemClass.getMethod("getEngine");
                Method renderSystemGetViewport = renderSystemClass.getMethod("getViewport");

                Class<?> worldEngineClass = Class.forName("me.cortex.voxy.common.world.WorldEngine");
                Method worldEngineGetMapper = worldEngineClass.getMethod("getMapper");
                Method worldEngineAcquireIfExists = worldEngineClass.getMethod("acquireIfExists", int.class, int.class, int.class, int.class);
                Method worldEngineGetWorldSectionId = worldEngineClass.getMethod("getWorldSectionId", int.class, int.class, int.class, int.class);
                int maxLodLayer = (int) worldEngineClass.getField("MAX_LOD_LAYER").get(null);

                Class<?> worldSectionClass = Class.forName("me.cortex.voxy.common.world.WorldSection");
                Method worldSectionRawData = worldSectionClass.getMethod("_unsafeGetRawDataArray");
                Method worldSectionRelease = worldSectionClass.getMethod("release");
                Method worldSectionGetNonEmptyBlockCount = worldSectionClass.getMethod("getNonEmptyBlockCount");

                Class<?> mapperClass = Class.forName("me.cortex.voxy.common.world.other.Mapper");
                Method mapperGetBlockStateOpacity = mapperClass.getMethod("getBlockStateOpacity", long.class);
                Method mapperIsAir = mapperClass.getMethod("isAir", long.class);
                long unknownMapping = (long) mapperClass.getField("UNKNOWN_MAPPING").get(null);

                Class<?> viewportClass = Class.forName("me.cortex.voxy.client.core.rendering.Viewport");
                Field viewportWidthField = viewportClass.getField("width");
                Field viewportHeightField = viewportClass.getField("height");
                Field viewportMvpField = viewportClass.getField("MVP");
                Field viewportDepthBoundingBufferField = viewportClass.getField("depthBoundingBuffer");

                Class<?> depthFramebufferClass = Class.forName("me.cortex.voxy.client.core.rendering.util.DepthFramebuffer");
                Field depthFramebufferFramebufferField = depthFramebufferClass.getField("framebuffer");

                Class<?> glFramebufferClass = Class.forName("me.cortex.voxy.client.core.gl.GlFramebuffer");
                Field glFramebufferIdField = glFramebufferClass.getField("id");

                return new Reflection(
                        getRenderSystem,
                        ofEngineNullable,
                        renderSystemGetEngine,
                        renderSystemGetViewport,
                        worldEngineGetMapper,
                        worldEngineAcquireIfExists,
                        worldEngineGetWorldSectionId,
                        worldSectionRawData,
                        worldSectionRelease,
                        worldSectionGetNonEmptyBlockCount,
                        mapperGetBlockStateOpacity,
                        mapperIsAir,
                        viewportWidthField,
                        viewportHeightField,
                        viewportMvpField,
                        viewportDepthBoundingBufferField,
                        depthFramebufferFramebufferField,
                        glFramebufferIdField,
                        unknownMapping,
                        maxLodLayer
                );
            } catch (ReflectiveOperationException exception) {
                return null;
            }
        }
    }
}
