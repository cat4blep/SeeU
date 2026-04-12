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
        if (snapshot == null) {
            return false;
        }
        return snapshot.hasRenderableData(position);
    }

    RaycastSession openRaycast(ClientLevel level) {
        return new RaycastSession(snapshot(level));
    }

    boolean isOccludedByDepth(ClientLevel level, Vec3 position) {
        Snapshot snapshot = snapshot(level);
        return snapshot != null && snapshot.isOccludedByDepth(position);
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

            Object activeSectionMap = null;
            Object nodeData = null;
            Object visibleSectionMap = null;
            if (renderSystem != null) {
                Object asyncNodeManager = reflection.renderSystemNodeManagerField.get(renderSystem);
                if (asyncNodeManager != null) {
                    Object nodeManager = reflection.asyncNodeManagerManagerField.get(asyncNodeManager);
                    if (nodeManager != null) {
                        activeSectionMap = reflection.nodeManagerActiveSectionMapField.get(nodeManager);
                        nodeData = reflection.nodeManagerNodeDataField.get(nodeManager);
                    }
                }
                Object chunkBoundRenderer = reflection.renderSystemChunkBoundRendererField.get(renderSystem);
                if (chunkBoundRenderer != null) {
                    visibleSectionMap = reflection.chunkBoundRendererChunkMapField.get(chunkBoundRenderer);
                }
            }

            Object mapper = reflection.worldEngineGetMapper.invoke(engine);
            Snapshot snapshot = new Snapshot(
                    reflection,
                    renderSystem,
                    engine,
                    activeSectionMap,
                    nodeData,
                    visibleSectionMap,
                    mapper
            );
            cachedSnapshot = snapshot;
            cachedLevel = level;

            if (!loggedAvailable) {
                LOGGER.info("Detected Voxy integration; far players will follow active Voxy sections and voxel occlusion.");
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
        private final Object activeSectionMap;
        private final Object nodeData;
        private final Object visibleSectionMap;
        private final Object mapper;
        private final int maxLodLayer;

        private Snapshot(
                Reflection reflection,
                Object renderSystem,
                Object engine,
                Object activeSectionMap,
                Object nodeData,
                Object visibleSectionMap,
                Object mapper
        ) {
            this.reflection = reflection;
            this.renderSystem = renderSystem;
            this.engine = engine;
            this.activeSectionMap = activeSectionMap;
            this.nodeData = nodeData;
            this.visibleSectionMap = visibleSectionMap;
            this.mapper = mapper;
            this.maxLodLayer = reflection.maxLodLayer;
        }

        long sectionId(int level, int sectionX, int sectionY, int sectionZ) {
            try {
                return (long) reflection.worldEngineGetWorldSectionId.invoke(null, level, sectionX, sectionY, sectionZ);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unable to compute Voxy section id.", exception);
            }
        }

        boolean hasRenderableData(Vec3 position) {
            if (!hasActiveRenderSystem()) {
                return false;
            }

            int blockX = Mth.floor(position.x);
            int blockY = Mth.floor(position.y);
            int blockZ = Mth.floor(position.z);
            for (int lod = 0; lod <= maxLodLayer; lod++) {
                if (isRenderable(sectionIdForBlock(this, blockX, blockY, blockZ, lod))) {
                    return true;
                }
            }
            return false;
        }

        private boolean isRenderable(long sectionId) {
            try {
                if (visibleSectionMap != null) {
                    return (boolean) reflection.long2IntMapContainsKey.invoke(visibleSectionMap, sectionId);
                }
                if (!(boolean) reflection.activeSectionMapContainsKey.invoke(activeSectionMap, sectionId)) {
                    return false;
                }

                int nodeState = (int) reflection.activeSectionMapGet.invoke(activeSectionMap, sectionId);
                if ((nodeState & reflection.nodeTypeMask) == reflection.nodeTypeRequest) {
                    return false;
                }

                int nodeId = nodeState & reflection.nodeIdMask;
                int geometryId = (int) reflection.nodeStoreGetNodeGeometry.invoke(nodeData, nodeId);
                return geometryId != reflection.nullGeometryId;
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unable to read Voxy active node state.", exception);
            }
        }

        boolean isOccludedByDepth(Vec3 position) {
            if (renderSystem == null) {
                return false;
            }

            try {
                Object viewport = reflection.renderSystemGetViewport.invoke(renderSystem);
                if (viewport == null) {
                    return false;
                }

                int width = (int) reflection.viewportWidthField.get(viewport);
                int height = (int) reflection.viewportHeightField.get(viewport);
                if (width <= 0 || height <= 0) {
                    return false;
                }

                Matrix4f mvp = (Matrix4f) reflection.viewportMvpField.get(viewport);
                Vector4f clip = new Vector4f((float) position.x, (float) position.y, (float) position.z, 1.0F);
                mvp.transform(clip);
                if (clip.w <= 0.0F) {
                    return false;
                }

                float inverseW = 1.0F / clip.w;
                float ndcX = clip.x * inverseW;
                float ndcY = clip.y * inverseW;
                float ndcZ = clip.z * inverseW;
                if (ndcX < -1.0F || ndcX > 1.0F || ndcY < -1.0F || ndcY > 1.0F || ndcZ < -1.0F || ndcZ > 1.0F) {
                    return false;
                }

                int pixelX = Mth.clamp((int) Math.floor((ndcX * 0.5F + 0.5F) * width), 0, width - 1);
                int pixelY = Mth.clamp((int) Math.floor((ndcY * 0.5F + 0.5F) * height), 0, height - 1);
                float playerDepth = ndcZ * 0.5F + 0.5F;
                float sceneDepth = readMaxSceneDepth(viewport, pixelX, pixelY, width, height);
                return sceneDepth > EMPTY_DEPTH_THRESHOLD && playerDepth + DEPTH_OCCLUSION_BIAS < sceneDepth;
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

        CachedSection acquireSection(long sectionId, int blockX, int blockY, int blockZ, int lod) throws ReflectiveOperationException {
            int sectionShift = lod + 5;
            int sectionX = blockX >> sectionShift;
            int sectionY = blockY >> sectionShift;
            int sectionZ = blockZ >> sectionShift;
            Object section = reflection.worldEngineAcquireIfExists.invoke(engine, sectionX, sectionY, sectionZ, lod);
            if (section == null) {
                return CachedSection.missing(sectionId);
            }
            long[] data = (long[]) reflection.worldSectionRawData.invoke(section);
            return new CachedSection(sectionId, section, data);
        }

        int opacity(long mapping) throws ReflectiveOperationException {
            if ((boolean) reflection.mapperIsAir.invoke(null, mapping)) {
                return 0;
            }
            return (int) reflection.mapperGetBlockStateOpacity.invoke(mapper, mapping);
        }

        boolean hasActiveRenderSystem() {
            return renderSystem != null && activeSectionMap != null && nodeData != null;
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
                        cached = snapshot.acquireSection(sectionId, blockX, blockY, blockZ, lod);
                    } catch (ReflectiveOperationException exception) {
                        throw new IllegalStateException("Unable to acquire Voxy section for raycast.", exception);
                    }
                    sectionCache.put(sectionId, cached);
                }

                if (!cached.present()) {
                    continue;
                }
                if (cached.data() == null || cached.data().length == 0) {
                    return 0;
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
            return snapshot != null && snapshot.hasRenderableData(position);
        }

        @Override
        public void close() {
            for (CachedSection cached : sectionCache.values()) {
                cached.release(snapshot);
            }
            sectionCache.clear();
        }
    }

    private record CachedSection(long sectionId, Object section, long[] data) {
        private static final Object MISSING_SECTION = new Object();

        private static CachedSection missing(long sectionId) {
            return new CachedSection(sectionId, MISSING_SECTION, null);
        }

        private boolean present() {
            return section != MISSING_SECTION;
        }

        private void release(Snapshot snapshot) {
            if (!present()) {
                return;
            }
            try {
                snapshot.reflection.worldSectionRelease.invoke(section);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unable to release Voxy section after raycast.", exception);
            }
        }
    }

    private record Reflection(
            Method getRenderSystem,
            Method ofEngineNullable,
            Method renderSystemGetEngine,
            Method worldEngineGetMapper,
            Method worldEngineAcquireIfExists,
            Method worldEngineGetWorldSectionId,
            Method worldSectionRawData,
            Method worldSectionRelease,
            Method mapperGetBlockStateOpacity,
            Method mapperIsAir,
            Method activeSectionMapContainsKey,
            Method activeSectionMapGet,
            Method long2IntMapContainsKey,
            Method nodeStoreGetNodeGeometry,
            Method renderSystemGetViewport,
            Field renderSystemNodeManagerField,
            Field renderSystemChunkBoundRendererField,
            Field asyncNodeManagerManagerField,
            Field nodeManagerActiveSectionMapField,
            Field nodeManagerNodeDataField,
            Field chunkBoundRendererChunkMapField,
            Field viewportWidthField,
            Field viewportHeightField,
            Field viewportMvpField,
            Field viewportDepthBoundingBufferField,
            Field depthFramebufferFramebufferField,
            Field glFramebufferIdField,
            int nodeIdMask,
            int nodeTypeMask,
            int nodeTypeRequest,
            int nullGeometryId,
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
                Field renderSystemNodeManagerField = renderSystemClass.getDeclaredField("nodeManager");
                renderSystemNodeManagerField.setAccessible(true);
                Field renderSystemChunkBoundRendererField = renderSystemClass.getField("chunkBoundRenderer");

                Class<?> asyncNodeManagerClass = Class.forName("me.cortex.voxy.client.core.rendering.hierachical.AsyncNodeManager");
                Field asyncNodeManagerManagerField = asyncNodeManagerClass.getDeclaredField("manager");
                asyncNodeManagerManagerField.setAccessible(true);

                Class<?> nodeManagerClass = Class.forName("me.cortex.voxy.client.core.rendering.hierachical.NodeManager");
                Field nodeManagerActiveSectionMapField = nodeManagerClass.getDeclaredField("activeSectionMap");
                nodeManagerActiveSectionMapField.setAccessible(true);
                Field nodeManagerNodeDataField = nodeManagerClass.getDeclaredField("nodeData");
                nodeManagerNodeDataField.setAccessible(true);
                int nodeIdMask = (int) nodeManagerClass.getField("NODE_ID_MSK").get(null);
                Field nodeTypeMaskField = nodeManagerClass.getDeclaredField("NODE_TYPE_MSK");
                nodeTypeMaskField.setAccessible(true);
                int nodeTypeMask = (int) nodeTypeMaskField.get(null);
                Field nodeTypeRequestField = nodeManagerClass.getDeclaredField("NODE_TYPE_REQUEST");
                nodeTypeRequestField.setAccessible(true);
                int nodeTypeRequest = (int) nodeTypeRequestField.get(null);
                int nullGeometryId = (int) nodeManagerClass.getField("NULL_GEOMETRY_ID").get(null);

                Class<?> activeSectionMapClass = Class.forName("it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap");
                Method activeSectionMapContainsKey = activeSectionMapClass.getMethod("containsKey", long.class);
                Method activeSectionMapGet = activeSectionMapClass.getMethod("get", long.class);
                Method long2IntMapContainsKey = activeSectionMapClass.getMethod("containsKey", long.class);

                Class<?> chunkBoundRendererClass = Class.forName("me.cortex.voxy.client.core.rendering.ChunkBoundRenderer");
                Field chunkBoundRendererChunkMapField = chunkBoundRendererClass.getDeclaredField("chunk2idx");
                chunkBoundRendererChunkMapField.setAccessible(true);

                Class<?> worldEngineClass = Class.forName("me.cortex.voxy.common.world.WorldEngine");
                Method worldEngineGetMapper = worldEngineClass.getMethod("getMapper");
                Method worldEngineAcquireIfExists = worldEngineClass.getMethod("acquireIfExists", int.class, int.class, int.class, int.class);
                Method worldEngineGetWorldSectionId = worldEngineClass.getMethod("getWorldSectionId", int.class, int.class, int.class, int.class);
                int maxLodLayer = (int) worldEngineClass.getField("MAX_LOD_LAYER").get(null);

                Class<?> worldSectionClass = Class.forName("me.cortex.voxy.common.world.WorldSection");
                Method worldSectionRawData = worldSectionClass.getMethod("_unsafeGetRawDataArray");
                Method worldSectionRelease = worldSectionClass.getMethod("release");

                Class<?> nodeStoreClass = Class.forName("me.cortex.voxy.client.core.rendering.hierachical.NodeStore");
                Method nodeStoreGetNodeGeometry = nodeStoreClass.getMethod("getNodeGeometry", int.class);

                Class<?> viewportClass = Class.forName("me.cortex.voxy.client.core.rendering.Viewport");
                Field viewportWidthField = viewportClass.getField("width");
                Field viewportHeightField = viewportClass.getField("height");
                Field viewportMvpField = viewportClass.getField("MVP");
                Field viewportDepthBoundingBufferField = viewportClass.getField("depthBoundingBuffer");

                Class<?> depthFramebufferClass = Class.forName("me.cortex.voxy.client.core.rendering.util.DepthFramebuffer");
                Field depthFramebufferFramebufferField = depthFramebufferClass.getField("framebuffer");

                Class<?> glFramebufferClass = Class.forName("me.cortex.voxy.client.core.gl.GlFramebuffer");
                Field glFramebufferIdField = glFramebufferClass.getField("id");

                Class<?> mapperClass = Class.forName("me.cortex.voxy.common.world.other.Mapper");
                Method mapperGetBlockStateOpacity = mapperClass.getMethod("getBlockStateOpacity", long.class);
                Method mapperIsAir = mapperClass.getMethod("isAir", long.class);

                return new Reflection(
                        getRenderSystem,
                        ofEngineNullable,
                        renderSystemGetEngine,
                        worldEngineGetMapper,
                        worldEngineAcquireIfExists,
                        worldEngineGetWorldSectionId,
                        worldSectionRawData,
                        worldSectionRelease,
                        mapperGetBlockStateOpacity,
                        mapperIsAir,
                        activeSectionMapContainsKey,
                        activeSectionMapGet,
                        long2IntMapContainsKey,
                        nodeStoreGetNodeGeometry,
                        renderSystemGetViewport,
                        renderSystemNodeManagerField,
                        renderSystemChunkBoundRendererField,
                        asyncNodeManagerManagerField,
                        nodeManagerActiveSectionMapField,
                        nodeManagerNodeDataField,
                        chunkBoundRendererChunkMapField,
                        viewportWidthField,
                        viewportHeightField,
                        viewportMvpField,
                        viewportDepthBoundingBufferField,
                        depthFramebufferFramebufferField,
                        glFramebufferIdField,
                        nodeIdMask,
                        nodeTypeMask,
                        nodeTypeRequest,
                        nullGeometryId,
                        maxLodLayer
                );
            } catch (ReflectiveOperationException exception) {
                return null;
            }
        }
    }
}
