package com.chronoblocks.client.renderer;

import com.chronoblocks.core.selection.SelectionManager;
import com.chronoblocks.core.selection.SelectionState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * 负责渲染选中方块的半透明填充和轮廓高亮。
 */
public final class SelectionRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectionRenderer.class);
    
    private static final double FILL_SHRINK = 0.002;
    private static final double OUTLINE_EXPAND = 0.001;
    private static volatile SelectionRenderer INSTANCE;
    
    private boolean enabled = true;
    private float[] selectionColor = {0.05f, 0.6f, 1.0f, 0.35f};
    private float[] outlineColor = {0.05f, 0.6f, 1.0f, 0.9f};
    private float[] previewColor = {1.0f, 1.0f, 0.0f, 0.35f};
    private float outlineWidth = 1.5f;
    private boolean showSelectionCount = true;
    private boolean enableAnimations = true;
    private float animationTime = 0.0f;
    private float highlightIntensity = 1.0f;
    private boolean enableFill = false; // 临时关闭填充以避免 FrameGraph 阶段的 BufferBuilder 状态问题
    
    private SelectionRenderer() {
        LOGGER.info("[SelectionRenderer] 初始化选择渲染器");
    }
    
    public static SelectionRenderer getInstance() {
        if (INSTANCE == null) {
            synchronized (SelectionRenderer.class) {
                if (INSTANCE == null) {
                    INSTANCE = new SelectionRenderer();
                }
            }
        }
        return INSTANCE;
    }
    
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, float tickDelta) {
        if (!enabled) {
            LOGGER.debug("[SelectionRenderer] 渲染器未启用");
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.gameRenderer == null || client.gameRenderer.getCamera() == null) {
            LOGGER.debug("[SelectionRenderer] 客户端或渲染器未就绪");
            return;
        }

        SelectionManager selectionManager = SelectionManager.get();
        if (selectionManager == null) {
            LOGGER.debug("[SelectionRenderer] SelectionManager为空");
            return;
        }
        
        if (!selectionManager.hasCurrentTool()) {
            LOGGER.debug("[SelectionRenderer] 没有当前工具");
            return;
        }
        
        SelectionState selectionState = selectionManager.getCurrentSelection();
        if (selectionState == null) {
            LOGGER.debug("[SelectionRenderer] SelectionState为空");
            return;
        }
        
        if (selectionState.isEmpty()) {
            LOGGER.debug("[SelectionRenderer] 选择状态为空");
            return;
        }
        
        LOGGER.info("[SelectionRenderer] ======= 开始渲染选择效果 =======");
        LOGGER.info("[SelectionRenderer] 选择方块数量: {}", selectionState.size());
            
        if (enableAnimations) {
            animationTime += tickDelta;
            highlightIntensity = 0.75f + 0.25f * (float) Math.sin(animationTime * 2.0f);
        } else {
            highlightIntensity = 1.0f;
        }

        Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f positionMatrix = entry.getPositionMatrix();
        Matrix3f normalMatrix = entry.getNormalMatrix();

        float[] fillColor = selectionColor.clone();
        fillColor[3] *= highlightIntensity;

        float[] lineColor = outlineColor.clone();
        lineColor[3] *= highlightIntensity;

        // 注意：1.21.4 的 FrameGraph 阶段对某些图层有状态限制；暂时禁用填充
        VertexConsumer fillConsumer = null;
        VertexConsumer lineConsumer = vertexConsumers.getBuffer(RenderLayer.getLines());

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.lineWidth(outlineWidth);

        Set<BlockPos> selectedBlocks = selectionState.getSelectedBlocks();
        LOGGER.info("[SelectionRenderer] 渲染 {} 个方块", selectedBlocks.size());
        
        int renderedCount = 0;
        for (BlockPos pos : selectedBlocks) {
            try {
                if (enableFill && fillConsumer != null) {
                    drawFilledCube(fillConsumer, positionMatrix, normalMatrix, pos, fillColor, cameraPos);
                }
                // 若 SelectionOverlayRenderer 已开启，则避免重复绘制轮廓（避免双重颜色叠加）
                if (!com.chronoblocks.client.renderer.SelectionOverlayRenderer.get().isEnabled()) {
                    drawOutline(lineConsumer, positionMatrix, pos, lineColor, cameraPos);
                }
                renderedCount++;
            } catch (Exception e) {
                LOGGER.error("[SelectionRenderer] 渲染方块 {} 失败", pos, e);
            }
        }
        
        LOGGER.info("[SelectionRenderer] 成功渲染 {} / {} 个方块", renderedCount, selectedBlocks.size());

        RenderSystem.disableBlend();

        if (showSelectionCount) {
            renderSelectionCount(selectionState.size());
        }
        
        LOGGER.info("[SelectionRenderer] ======= 渲染选择效果结束 =======");
    }

    private void drawFilledCube(VertexConsumer consumer,
                                Matrix4f positionMatrix,
                                Matrix3f normalMatrix,
                                BlockPos pos,
                                float[] color,
                                Vec3d cameraPos) {
        float[][] vertices = getFloats(pos, cameraPos);

        emitQuad(consumer, positionMatrix, normalMatrix, vertices, color, 0, 1, 2, 3, 0.0f, 0.0f, -1.0f);
        emitQuad(consumer, positionMatrix, normalMatrix, vertices, color, 4, 5, 6, 7, 0.0f, 0.0f, 1.0f);
        emitQuad(consumer, positionMatrix, normalMatrix, vertices, color, 0, 4, 7, 3, -1.0f, 0.0f, 0.0f);
        emitQuad(consumer, positionMatrix, normalMatrix, vertices, color, 1, 5, 6, 2, 1.0f, 0.0f, 0.0f);
        emitQuad(consumer, positionMatrix, normalMatrix, vertices, color, 0, 1, 5, 4, 0.0f, -1.0f, 0.0f);
        emitQuad(consumer, positionMatrix, normalMatrix, vertices, color, 3, 2, 6, 7, 0.0f, 1.0f, 0.0f);
    }

    private static float[] @NotNull [] getFloats(BlockPos pos, Vec3d cameraPos) {
        double minX = pos.getX() - cameraPos.x + FILL_SHRINK;
        double minY = pos.getY() - cameraPos.y + FILL_SHRINK;
        double minZ = pos.getZ() - cameraPos.z + FILL_SHRINK;
        double maxX = pos.getX() - cameraPos.x + 1.0 - FILL_SHRINK;
        double maxY = pos.getY() - cameraPos.y + 1.0 - FILL_SHRINK;
        double maxZ = pos.getZ() - cameraPos.z + 1.0 - FILL_SHRINK;

        return new float[][]{
            {(float) minX, (float) minY, (float) minZ},
            {(float) maxX, (float) minY, (float) minZ},
            {(float) maxX, (float) maxY, (float) minZ},
            {(float) minX, (float) maxY, (float) minZ},
            {(float) minX, (float) minY, (float) maxZ},
            {(float) maxX, (float) minY, (float) maxZ},
            {(float) maxX, (float) maxY, (float) maxZ},
            {(float) minX, (float) maxY, (float) maxZ}
        };
    }

    private void emitQuad(VertexConsumer consumer,
                          Matrix4f positionMatrix,
                          Matrix3f normalMatrix,
                          float[][] vertices,
                          float[] color,
                          int v1, int v2, int v3, int v4,
                          float nx, float ny, float nz) {
        // 计算变换后的法向量
        Vector3f normal = new Vector3f(nx, ny, nz);
        normal.mul(normalMatrix);

        // 顶点1
        consumer.vertex(positionMatrix, vertices[v1][0], vertices[v1][1], vertices[v1][2])
                .color(color[0], color[1], color[2], color[3])
                .texture(0.0f, 0.0f)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(normal.x, normal.y, normal.z);

        // 顶点2
        consumer.vertex(positionMatrix, vertices[v2][0], vertices[v2][1], vertices[v2][2])
                .color(color[0], color[1], color[2], color[3])
                .texture(1.0f, 0.0f)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(normal.x, normal.y, normal.z);

        // 顶点3
        consumer.vertex(positionMatrix, vertices[v3][0], vertices[v3][1], vertices[v3][2])
                .color(color[0], color[1], color[2], color[3])
                .texture(1.0f, 1.0f)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(normal.x, normal.y, normal.z);

        // 顶点4
        consumer.vertex(positionMatrix, vertices[v4][0], vertices[v4][1], vertices[v4][2])
                .color(color[0], color[1], color[2], color[3])
                .texture(0.0f, 1.0f)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(normal.x, normal.y, normal.z);
    }

    private void drawOutline(VertexConsumer consumer,
                             Matrix4f positionMatrix,
                             BlockPos pos,
                             float[] color,
                             Vec3d cameraPos) {
        double minX = pos.getX() - cameraPos.x - OUTLINE_EXPAND;
        double minY = pos.getY() - cameraPos.y - OUTLINE_EXPAND;
        double minZ = pos.getZ() - cameraPos.z - OUTLINE_EXPAND;
        double maxX = pos.getX() - cameraPos.x + 1.0 + OUTLINE_EXPAND;
        double maxY = pos.getY() - cameraPos.y + 1.0 + OUTLINE_EXPAND;
        double maxZ = pos.getZ() - cameraPos.z + 1.0 + OUTLINE_EXPAND;

        emitLine(consumer, positionMatrix, minX, minY, minZ, maxX, minY, minZ, color);
        emitLine(consumer, positionMatrix, maxX, minY, minZ, maxX, minY, maxZ, color);
        emitLine(consumer, positionMatrix, maxX, minY, maxZ, minX, minY, maxZ, color);
        emitLine(consumer, positionMatrix, minX, minY, maxZ, minX, minY, minZ, color);

        emitLine(consumer, positionMatrix, minX, maxY, minZ, maxX, maxY, minZ, color);
        emitLine(consumer, positionMatrix, maxX, maxY, minZ, maxX, maxY, maxZ, color);
        emitLine(consumer, positionMatrix, maxX, maxY, maxZ, minX, maxY, maxZ, color);
        emitLine(consumer, positionMatrix, minX, maxY, maxZ, minX, maxY, minZ, color);

        emitLine(consumer, positionMatrix, minX, minY, minZ, minX, maxY, minZ, color);
        emitLine(consumer, positionMatrix, maxX, minY, minZ, maxX, maxY, minZ, color);
        emitLine(consumer, positionMatrix, maxX, minY, maxZ, maxX, maxY, maxZ, color);
        emitLine(consumer, positionMatrix, minX, minY, maxZ, minX, maxY, maxZ, color);
    }

    private void emitLine(VertexConsumer consumer,
                          Matrix4f positionMatrix,
                          double x1, double y1, double z1,
                          double x2, double y2, double z2,
                          float[] color) {
        // Minecraft 1.21.4的Lines需要normal来正确结束顶点
        consumer.vertex(positionMatrix, (float) x1, (float) y1, (float) z1)
                .color(color[0], color[1], color[2], color[3])
                .normal(0, 1, 0);

        consumer.vertex(positionMatrix, (float) x2, (float) y2, (float) z2)
                .color(color[0], color[1], color[2], color[3])
                .normal(0, 1, 0);
    }

    private void renderSelectionCount(int count) {
        LOGGER.debug("[SelectionRenderer] 当前选区包含 {} 个方块", count);
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        LOGGER.info("[SelectionRenderer] 渲染器{}", enabled ? "启用" : "禁用");
    }
    
    public float[] getSelectionColor() {
        return selectionColor.clone();
    }
    
    public void setSelectionColor(float[] color) {
        this.selectionColor = color.clone();
    }
    
    public float[] getOutlineColor() {
        return outlineColor.clone();
    }
    
    public void setOutlineColor(float[] color) {
        this.outlineColor = color.clone();
    }
    
    public float[] getPreviewColor() {
        return previewColor.clone();
    }
    
    public void setPreviewColor(float[] color) {
        this.previewColor = color.clone();
    }
    
    public float getOutlineWidth() {
        return outlineWidth;
    }
    
    public void setOutlineWidth(float width) {
        this.outlineWidth = width;
    }
    
    public boolean isShowSelectionCount() {
        return showSelectionCount;
    }
    
    public void setShowSelectionCount(boolean show) {
        this.showSelectionCount = show;
    }
    
    public boolean isEnableAnimations() {
        return enableAnimations;
    }
    
    public void setEnableAnimations(boolean enable) {
        this.enableAnimations = enable;
    }
}