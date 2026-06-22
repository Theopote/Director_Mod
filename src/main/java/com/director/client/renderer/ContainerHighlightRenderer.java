package com.director.client.renderer;

import com.director.core.DirectorServices;
import com.director.client.renderer.mesh.BlockOutlineMeshCache;
import com.director.client.settings.RenderSettings;
import com.director.core.container.Container;
import com.director.core.container.ContainerManager;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

/**
 * Renders the selected container using either per-block geometry or a merged outer shell.
 */
public final class ContainerHighlightRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerHighlightRenderer.class);
    private static final double OUTLINE_EXPAND = 0.001;
    private static final double FILL_SHRINK = -0.005;

    private static volatile ContainerHighlightRenderer INSTANCE;

    private final float[] defaultOutlineColor = {0.4f, 1.0f, 0.5f, 0.9f};
    private final BlockOutlineMeshCache meshCache = new BlockOutlineMeshCache();
    private final RenderSettings settings = RenderSettings.get();

    private Container cachedContainer;
    private long cachedContainerModifiedTime = -1L;
    private BlockOutlineMeshCache.Mesh cachedContainerMesh;

    private ContainerHighlightRenderer() {
    }

    public static ContainerHighlightRenderer getInstance() {
        if (INSTANCE == null) {
            synchronized (ContainerHighlightRenderer.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ContainerHighlightRenderer();
                }
            }
        }
        return INSTANCE;
    }

    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, float tickDelta) {
        if (!settings.container_showOutline && !settings.container_showFill) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            return;
        }

        ContainerManager manager = DirectorServices.get().containers();
        if (manager == null) {
            return;
        }

        Container selectedContainer = manager.getSelectedContainer();
        if (selectedContainer == null) {
            return;
        }

        Collection<BlockPos> blocks = selectedContainer.getBlocks();
        if (blocks == null || blocks.isEmpty()) {
            clearCachedMesh();
            return;
        }

        Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
        BlockPos cameraBlock = BlockPos.ofFloored(cameraPos);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        float outlineR = readColor(settings.container_outlineColor, 0, defaultOutlineColor[0]);
        float outlineG = readColor(settings.container_outlineColor, 1, defaultOutlineColor[1]);
        float outlineB = readColor(settings.container_outlineColor, 2, defaultOutlineColor[2]);
        float outlineA = readColor(settings.container_outlineColor, 3, defaultOutlineColor[3]);
        float fillR = readColor(settings.container_fillColor, 0, defaultOutlineColor[0]);
        float fillG = readColor(settings.container_fillColor, 1, defaultOutlineColor[1]);
        float fillB = readColor(settings.container_fillColor, 2, defaultOutlineColor[2]);
        float fillA = readColor(settings.container_fillColor, 3, 0.2f);

        VertexConsumer lineConsumer = settings.container_showOutline
            ? vertexConsumers.getBuffer(RenderLayer.getLines())
            : null;
        VertexConsumer fillConsumer = settings.container_showFill
            ? vertexConsumers.getBuffer(RenderLayers.getSelectionFillLayer())
            : null;

        Container.BoundingBox bbox = selectedContainer.getBoundingBox();
        int maxDistSq = settings.selection_maxRenderDistance * settings.selection_maxRenderDistance;
        if (bbox != null && getDistSqToBox(cameraPos, bbox) > (double) maxDistSq) {
            return;
        }

        boolean useGreedy = settings.container_enableGreedy
            && blocks.size() >= Math.max(1, settings.container_greedyThreshold);

        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager._enableDepthTest();
        GlStateManager._disableCull();
        GlStateManager._depthMask(false);
        GlStateManager._enablePolygonOffset();
        GlStateManager._polygonOffset(-1.0f, -1.0f);

        try {
            if (useGreedy) {
                BlockOutlineMeshCache.Mesh mesh = getOrCreateCachedMesh(selectedContainer, blocks);
                renderMergedFill(fillConsumer, matrix, mesh, cameraPos, maxDistSq, fillR, fillG, fillB, fillA);
                renderMergedOutline(lineConsumer, matrix, mesh, cameraPos, outlineR, outlineG, outlineB, outlineA);
            } else {
                renderPerBlock(fillConsumer, lineConsumer, matrix, blocks, cameraPos, cameraBlock, maxDistSq,
                    fillR, fillG, fillB, fillA, outlineR, outlineG, outlineB, outlineA);
            }

            if (settings.container_showBoundingBox && lineConsumer != null && bbox != null) {
                float lw = settings.container_outlineWidth;
                RenderSystem.lineWidth(Math.max(1.0f, Math.min(lw, 4.0f)));
                Box box = new Box(
                    bbox.getMin().getX(), bbox.getMin().getY(), bbox.getMin().getZ(),
                    bbox.getMax().getX() + 1.0, bbox.getMax().getY() + 1.0, bbox.getMax().getZ() + 1.0
                ).offset(-cameraPos.x, -cameraPos.y, -cameraPos.z);
                renderBoundingBoxLines(lineConsumer, matrix, box, outlineR, outlineG, outlineB, outlineA);
            }
        } finally {
            GlStateManager._disablePolygonOffset();
            GlStateManager._enableCull();
            GlStateManager._depthMask(true);
            GlStateManager._disableBlend();
            RenderSystem.lineWidth(1.0f);
        }
    }

    private void renderMergedFill(VertexConsumer fillConsumer, Matrix4f matrix, BlockOutlineMeshCache.Mesh mesh,
                                  Vec3d cameraPos, int maxDistSq,
                                  float r, float g, float b, float a) {
        if (fillConsumer == null) {
            return;
        }

        int rendered = 0;
        for (BlockOutlineMeshCache.Quad quad : mesh.quads()) {
            if (!isQuadInRange(quad, cameraPos, maxDistSq)) {
                continue;
            }
            emitMergedQuad(fillConsumer, matrix, quad, cameraPos, r, g, b, a);
            rendered++;
        }

        if (rendered > 0 && LOGGER.isDebugEnabled()) {
            LOGGER.debug("[ContainerHighlightRenderer] merged fill quads={}", rendered);
        }
    }

    private void renderMergedOutline(VertexConsumer lineConsumer, Matrix4f matrix, BlockOutlineMeshCache.Mesh mesh,
                                     Vec3d cameraPos, float r, float g, float b, float a) {
        if (lineConsumer == null) {
            return;
        }

        float lw = settings.container_outlineWidth;
        RenderSystem.lineWidth(Math.max(1.0f, Math.min(lw, 4.0f)));

        int rendered = 0;
        for (BlockOutlineMeshCache.Line line : mesh.lines()) {
            drawLine(lineConsumer, matrix,
                (float) (line.x1() - cameraPos.x), (float) (line.y1() - cameraPos.y), (float) (line.z1() - cameraPos.z),
                (float) (line.x2() - cameraPos.x), (float) (line.y2() - cameraPos.y), (float) (line.z2() - cameraPos.z),
                r, g, b, a);
            rendered++;
        }

        if (rendered > 0 && LOGGER.isDebugEnabled()) {
            LOGGER.debug("[ContainerHighlightRenderer] merged outline lines={}", rendered);
        }
    }

    private void renderPerBlock(VertexConsumer fillConsumer, VertexConsumer lineConsumer, Matrix4f matrix,
                                Collection<BlockPos> blocks, Vec3d cameraPos, BlockPos cameraBlock, int maxDistSq,
                                float fillR, float fillG, float fillB, float fillA,
                                float outlineR, float outlineG, float outlineB, float outlineA) {
        float lw = settings.container_outlineWidth;
        if (lineConsumer != null) {
            RenderSystem.lineWidth(Math.max(1.0f, Math.min(lw, 4.0f)));
        }

        int rendered = 0;
        for (BlockPos pos : blocks) {
            if (cameraBlock.getSquaredDistance(pos) > maxDistSq) {
                continue;
            }
            if (fillConsumer != null) {
                drawFilledCube(fillConsumer, matrix, pos, fillR, fillG, fillB, fillA, cameraPos);
            }
            if (lineConsumer != null) {
                drawBlockOutline(lineConsumer, matrix, pos, outlineR, outlineG, outlineB, outlineA, cameraPos);
            }
            rendered++;
        }

        if (rendered > 0 && LOGGER.isDebugEnabled()) {
            LOGGER.debug("[ContainerHighlightRenderer] per-block render count={}", rendered);
        }
    }

    private BlockOutlineMeshCache.Mesh getOrCreateCachedMesh(Container container, Collection<BlockPos> blocks) {
        long mt = container.getModifiedTime();
        if (cachedContainer == container && cachedContainerModifiedTime == mt && cachedContainerMesh != null) {
            return cachedContainerMesh;
        }

        BlockOutlineMeshCache.Mesh mesh = meshCache.getOrCreateMesh(blocks);
        cachedContainer = container;
        cachedContainerModifiedTime = mt;
        cachedContainerMesh = mesh;
        return mesh;
    }

    private void clearCachedMesh() {
        cachedContainer = null;
        cachedContainerModifiedTime = -1L;
        cachedContainerMesh = null;
    }

    private static float readColor(float[] colors, int index, float fallback) {
        return colors != null && colors.length > index ? colors[index] : fallback;
    }

    private boolean isQuadInRange(BlockOutlineMeshCache.Quad quad, Vec3d cameraPos, int maxDistSq) {
        for (BlockPos p : new BlockPos[]{quad.p1(), quad.p2(), quad.p3(), quad.p4()}) {
            double dSq = (p.getX() - cameraPos.x) * (p.getX() - cameraPos.x)
                + (p.getY() - cameraPos.y) * (p.getY() - cameraPos.y)
                + (p.getZ() - cameraPos.z) * (p.getZ() - cameraPos.z);
            if (dSq <= maxDistSq) {
                return true;
            }
        }
        return false;
    }

    private static double getDistSqToBox(Vec3d cameraPos, Container.BoundingBox bbox) {
        double dx = 0.0;
        double dy = 0.0;
        double dz = 0.0;
        double cx = cameraPos.x;
        double cy = cameraPos.y;
        double cz = cameraPos.z;
        double minX = bbox.getMin().getX();
        double minY = bbox.getMin().getY();
        double minZ = bbox.getMin().getZ();
        double maxX = bbox.getMax().getX() + 1.0;
        double maxY = bbox.getMax().getY() + 1.0;
        double maxZ = bbox.getMax().getZ() + 1.0;

        if (cx < minX) dx = minX - cx;
        else if (cx > maxX) dx = cx - maxX;
        if (cy < minY) dy = minY - cy;
        else if (cy > maxY) dy = cy - maxY;
        if (cz < minZ) dz = minZ - cz;
        else if (cz > maxZ) dz = cz - maxZ;

        return dx * dx + dy * dy + dz * dz;
    }

    private void drawLine(VertexConsumer consumer, Matrix4f matrix,
                          float x1, float y1, float z1,
                          float x2, float y2, float z2,
                          float r, float g, float b, float a) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float nx = 0f;
        float ny = 1f;
        float nz = 0f;
        if (len > 1.0e-6f) {
            nx = dx / len;
            ny = dy / len;
            nz = dz / len;
        }
        consumer.vertex(matrix, x1, y1, z1).color(r, g, b, a).normal(nx, ny, nz);
        consumer.vertex(matrix, x2, y2, z2).color(r, g, b, a).normal(nx, ny, nz);
    }

    private void drawBlockOutline(VertexConsumer consumer, Matrix4f matrix, BlockPos pos,
                                  float r, float g, float b, float a, Vec3d cameraPos) {
        double minX = pos.getX() - cameraPos.x - OUTLINE_EXPAND;
        double minY = pos.getY() - cameraPos.y - OUTLINE_EXPAND;
        double minZ = pos.getZ() - cameraPos.z - OUTLINE_EXPAND;
        double maxX = pos.getX() - cameraPos.x + 1.0 + OUTLINE_EXPAND;
        double maxY = pos.getY() - cameraPos.y + 1.0 + OUTLINE_EXPAND;
        double maxZ = pos.getZ() - cameraPos.z + 1.0 + OUTLINE_EXPAND;

        drawLine(consumer, matrix, (float) minX, (float) minY, (float) minZ, (float) maxX, (float) minY, (float) minZ, r, g, b, a);
        drawLine(consumer, matrix, (float) maxX, (float) minY, (float) minZ, (float) maxX, (float) minY, (float) maxZ, r, g, b, a);
        drawLine(consumer, matrix, (float) maxX, (float) minY, (float) maxZ, (float) minX, (float) minY, (float) maxZ, r, g, b, a);
        drawLine(consumer, matrix, (float) minX, (float) minY, (float) maxZ, (float) minX, (float) minY, (float) minZ, r, g, b, a);

        drawLine(consumer, matrix, (float) minX, (float) maxY, (float) minZ, (float) maxX, (float) maxY, (float) minZ, r, g, b, a);
        drawLine(consumer, matrix, (float) maxX, (float) maxY, (float) minZ, (float) maxX, (float) maxY, (float) maxZ, r, g, b, a);
        drawLine(consumer, matrix, (float) maxX, (float) maxY, (float) maxZ, (float) minX, (float) maxY, (float) maxZ, r, g, b, a);
        drawLine(consumer, matrix, (float) minX, (float) maxY, (float) maxZ, (float) minX, (float) maxY, (float) minZ, r, g, b, a);

        drawLine(consumer, matrix, (float) minX, (float) minY, (float) minZ, (float) minX, (float) maxY, (float) minZ, r, g, b, a);
        drawLine(consumer, matrix, (float) maxX, (float) minY, (float) minZ, (float) maxX, (float) maxY, (float) minZ, r, g, b, a);
        drawLine(consumer, matrix, (float) maxX, (float) minY, (float) maxZ, (float) maxX, (float) maxY, (float) maxZ, r, g, b, a);
        drawLine(consumer, matrix, (float) minX, (float) minY, (float) maxZ, (float) minX, (float) maxY, (float) maxZ, r, g, b, a);
    }

    private void renderBoundingBoxLines(VertexConsumer consumer, Matrix4f matrix, Box box, float r, float g, float b, float a) {
        drawLine(consumer, matrix, (float) box.minX, (float) box.minY, (float) box.minZ,
            (float) box.maxX, (float) box.minY, (float) box.minZ, r, g, b, a);
        drawLine(consumer, matrix, (float) box.maxX, (float) box.minY, (float) box.minZ,
            (float) box.maxX, (float) box.minY, (float) box.maxZ, r, g, b, a);
        drawLine(consumer, matrix, (float) box.maxX, (float) box.minY, (float) box.maxZ,
            (float) box.minX, (float) box.minY, (float) box.maxZ, r, g, b, a);
        drawLine(consumer, matrix, (float) box.minX, (float) box.minY, (float) box.maxZ,
            (float) box.minX, (float) box.minY, (float) box.minZ, r, g, b, a);

        drawLine(consumer, matrix, (float) box.minX, (float) box.maxY, (float) box.minZ,
            (float) box.maxX, (float) box.maxY, (float) box.minZ, r, g, b, a);
        drawLine(consumer, matrix, (float) box.maxX, (float) box.maxY, (float) box.minZ,
            (float) box.maxX, (float) box.maxY, (float) box.maxZ, r, g, b, a);
        drawLine(consumer, matrix, (float) box.maxX, (float) box.maxY, (float) box.maxZ,
            (float) box.minX, (float) box.maxY, (float) box.maxZ, r, g, b, a);
        drawLine(consumer, matrix, (float) box.minX, (float) box.maxY, (float) box.maxZ,
            (float) box.minX, (float) box.maxY, (float) box.minZ, r, g, b, a);

        drawLine(consumer, matrix, (float) box.minX, (float) box.minY, (float) box.minZ,
            (float) box.minX, (float) box.maxY, (float) box.minZ, r, g, b, a);
        drawLine(consumer, matrix, (float) box.maxX, (float) box.minY, (float) box.minZ,
            (float) box.maxX, (float) box.maxY, (float) box.minZ, r, g, b, a);
        drawLine(consumer, matrix, (float) box.maxX, (float) box.minY, (float) box.maxZ,
            (float) box.maxX, (float) box.maxY, (float) box.maxZ, r, g, b, a);
        drawLine(consumer, matrix, (float) box.minX, (float) box.minY, (float) box.maxZ,
            (float) box.minX, (float) box.maxY, (float) box.maxZ, r, g, b, a);
    }

    private void drawFilledCube(VertexConsumer consumer, Matrix4f matrix, BlockPos pos,
                                float r, float g, float b, float a, Vec3d cameraPos) {
        float minX = (float) (pos.getX() - cameraPos.x + FILL_SHRINK);
        float minY = (float) (pos.getY() - cameraPos.y + FILL_SHRINK);
        float minZ = (float) (pos.getZ() - cameraPos.z + FILL_SHRINK);
        float maxX = (float) (pos.getX() - cameraPos.x + 1.0 - FILL_SHRINK);
        float maxY = (float) (pos.getY() - cameraPos.y + 1.0 - FILL_SHRINK);
        float maxZ = (float) (pos.getZ() - cameraPos.z + 1.0 - FILL_SHRINK);

        emitQuad(consumer, matrix, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, a);
    }

    private void emitMergedQuad(VertexConsumer consumer, Matrix4f matrix, BlockOutlineMeshCache.Quad quad, Vec3d cameraPos,
                                float r, float g, float b, float a) {
        Direction dir = quad.dir();
        float nx = dir.getOffsetX();
        float ny = dir.getOffsetY();
        float nz = dir.getOffsetZ();

        fullBrightVertex(consumer, matrix,
            (float) (quad.p1().getX() - cameraPos.x + nx * FILL_SHRINK),
            (float) (quad.p1().getY() - cameraPos.y + ny * FILL_SHRINK),
            (float) (quad.p1().getZ() - cameraPos.z + nz * FILL_SHRINK),
            r, g, b, a, nx, ny, nz);
        fullBrightVertex(consumer, matrix,
            (float) (quad.p2().getX() - cameraPos.x + nx * FILL_SHRINK),
            (float) (quad.p2().getY() - cameraPos.y + ny * FILL_SHRINK),
            (float) (quad.p2().getZ() - cameraPos.z + nz * FILL_SHRINK),
            r, g, b, a, nx, ny, nz);
        fullBrightVertex(consumer, matrix,
            (float) (quad.p3().getX() - cameraPos.x + nx * FILL_SHRINK),
            (float) (quad.p3().getY() - cameraPos.y + ny * FILL_SHRINK),
            (float) (quad.p3().getZ() - cameraPos.z + nz * FILL_SHRINK),
            r, g, b, a, nx, ny, nz);
        fullBrightVertex(consumer, matrix,
            (float) (quad.p4().getX() - cameraPos.x + nx * FILL_SHRINK),
            (float) (quad.p4().getY() - cameraPos.y + ny * FILL_SHRINK),
            (float) (quad.p4().getZ() - cameraPos.z + nz * FILL_SHRINK),
            r, g, b, a, nx, ny, nz);
    }

    private void emitQuad(VertexConsumer consumer, Matrix4f matrix,
                          float minX, float minY, float minZ,
                          float maxX, float maxY, float maxZ,
                          float r, float g, float b, float a) {
        fullBrightVertex(consumer, matrix, minX, minY, minZ, r, g, b, a, 0, 1, 0);
        fullBrightVertex(consumer, matrix, maxX, minY, minZ, r, g, b, a, 0, 1, 0);
        fullBrightVertex(consumer, matrix, maxX, maxY, minZ, r, g, b, a, 0, 1, 0);
        fullBrightVertex(consumer, matrix, minX, maxY, minZ, r, g, b, a, 0, 1, 0);

        fullBrightVertex(consumer, matrix, minX, minY, maxZ, r, g, b, a, 0, 1, 0);
        fullBrightVertex(consumer, matrix, maxX, minY, maxZ, r, g, b, a, 0, 1, 0);
        fullBrightVertex(consumer, matrix, maxX, maxY, maxZ, r, g, b, a, 0, 1, 0);
        fullBrightVertex(consumer, matrix, minX, maxY, maxZ, r, g, b, a, 0, 1, 0);

        fullBrightVertex(consumer, matrix, minX, minY, minZ, r, g, b, a, 0, 1, 0);
        fullBrightVertex(consumer, matrix, minX, minY, maxZ, r, g, b, a, 0, 1, 0);
        fullBrightVertex(consumer, matrix, minX, maxY, maxZ, r, g, b, a, 0, 1, 0);
        fullBrightVertex(consumer, matrix, minX, maxY, minZ, r, g, b, a, 0, 1, 0);

        fullBrightVertex(consumer, matrix, maxX, minY, minZ, r, g, b, a, 0, 1, 0);
        fullBrightVertex(consumer, matrix, maxX, minY, maxZ, r, g, b, a, 0, 1, 0);
        fullBrightVertex(consumer, matrix, maxX, maxY, maxZ, r, g, b, a, 0, 1, 0);
        fullBrightVertex(consumer, matrix, maxX, maxY, minZ, r, g, b, a, 0, 1, 0);

        fullBrightVertex(consumer, matrix, minX, minY, minZ, r, g, b, a, 0, 1, 0);
        fullBrightVertex(consumer, matrix, maxX, minY, minZ, r, g, b, a, 0, 1, 0);
        fullBrightVertex(consumer, matrix, maxX, minY, maxZ, r, g, b, a, 0, 1, 0);
        fullBrightVertex(consumer, matrix, minX, minY, maxZ, r, g, b, a, 0, 1, 0);

        fullBrightVertex(consumer, matrix, minX, maxY, minZ, r, g, b, a, 0, 1, 0);
        fullBrightVertex(consumer, matrix, maxX, maxY, minZ, r, g, b, a, 0, 1, 0);
        fullBrightVertex(consumer, matrix, maxX, maxY, maxZ, r, g, b, a, 0, 1, 0);
        fullBrightVertex(consumer, matrix, minX, maxY, maxZ, r, g, b, a, 0, 1, 0);
    }

    private void fullBrightVertex(VertexConsumer consumer, Matrix4f matrix,
                                  float x, float y, float z,
                                  float r, float g, float b, float a,
                                  float nx, float ny, float nz) {
        consumer.vertex(matrix, x, y, z)
            .color(r, g, b, a)
            .texture(0.5f, 0.5f)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
            .normal(nx, ny, nz);
    }
}
