package com.chronoblocks.client.renderer;

import com.chronoblocks.core.selection.SelectionManager;
import com.chronoblocks.core.selection.SelectionTool;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * 🎯 工具预览管理器
 * 
 * 负责管理选择工具的预览效果，包括：
 * - 拖拽预览框
 * - 笔刷范围预览
 * - 实时预览更新
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class ToolPreviewManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ToolPreviewManager.class);
    
    /** 单例实例 */
    private static volatile ToolPreviewManager INSTANCE;
    
    /** 选择渲染器 */
    @SuppressWarnings("unused")
    private final SelectionRenderer selectionRenderer;
    
    /** 是否启用预览 */
    private boolean enabled = true;
    
    /** 当前拖拽状态 */
    private boolean isDragging = false;
    private BlockPos dragStartPos = null;
    private BlockPos dragCurrentPos = null;
    
    /** 预览颜色 (RGBA) */
    private float[] previewColor = {1.0f, 1.0f, 0.0f, 0.3f}; // 黄色半透明
    
    /** 预览轮廓颜色 (RGBA) */
    private float[] previewOutlineColor = {1.0f, 1.0f, 0.0f, 0.8f}; // 黄色轮廓
    
    /** 笔刷预览半径 */
    private float brushPreviewRadius = 3.0f;
    
    /** 预览方块集合 */
    private final Set<BlockPos> previewBlocks = new HashSet<>();
    
    private ToolPreviewManager() {
        this.selectionRenderer = SelectionRenderer.getInstance();
        LOGGER.info("[ToolPreviewManager] 初始化工具预览管理器");
    }
    
    /**
     * 获取单例实例
     */
    public static ToolPreviewManager getInstance() {
        if (INSTANCE == null) {
            synchronized (ToolPreviewManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ToolPreviewManager();
                }
            }
        }
        return INSTANCE;
    }
    
    /**
     * 开始拖拽预览
     */
    public void startDragPreview(BlockPos startPos) {
        this.isDragging = true;
        this.dragStartPos = startPos;
        this.dragCurrentPos = startPos;
        updatePreviewBlocks();
        LOGGER.debug("[ToolPreviewManager] 开始拖拽预览: {}", startPos);
    }
    
    /**
     * 更新拖拽预览
     */
    public void updateDragPreview(BlockPos currentPos) {
        if (!isDragging) return;
        
        this.dragCurrentPos = currentPos;
        updatePreviewBlocks();
        LOGGER.debug("[ToolPreviewManager] 更新拖拽预览: {}", currentPos);
    }
    
    /**
     * 结束拖拽预览
     */
    public void endDragPreview() {
        this.isDragging = false;
        this.dragStartPos = null;
        this.dragCurrentPos = null;
        this.previewBlocks.clear();
        LOGGER.debug("[ToolPreviewManager] 结束拖拽预览");
    }
    
    /**
     * 更新预览方块集合
     */
    private void updatePreviewBlocks() {
        if (!isDragging || dragStartPos == null || dragCurrentPos == null) {
            return;
        }
        
        try {
            SelectionManager selectionManager = SelectionManager.get();
            if (selectionManager == null || !selectionManager.hasCurrentTool()) {
                return;
            }
            
            SelectionTool currentTool = selectionManager.getCurrentTool();
            String toolId = currentTool.getId();
            
            previewBlocks.clear();
            
            switch (toolId) {
                case "box", "box_v2" -> {
                    // 框选预览
                    Box border = new Box(
                        Math.min(dragStartPos.getX(), dragCurrentPos.getX()),
                        Math.min(dragStartPos.getY(), dragCurrentPos.getY()),
                        Math.min(dragStartPos.getZ(), dragCurrentPos.getZ()),
                        Math.max(dragStartPos.getX(), dragCurrentPos.getX()) + 1,
                        Math.max(dragStartPos.getY(), dragCurrentPos.getY()) + 1,
                        Math.max(dragStartPos.getZ(), dragCurrentPos.getZ()) + 1
                    );
                    for (double x = border.minX; x <= border.maxX; x++) {
                        for (double y = border.minY; y <= border.maxY; y++) {
                            for (double z = border.minZ; z <= border.maxZ; z++) {
                                previewBlocks.add(new BlockPos((int)x, (int)y, (int)z));
                            }
                        }
                    }
                }
                case "brush" -> {
                    // 笔刷预览
                    double radius = brushPreviewRadius;
                    for (double x = dragCurrentPos.getX() - radius; x <= dragCurrentPos.getX() + radius; x++) {
                        for (double y = dragCurrentPos.getY() - radius; y <= dragCurrentPos.getY() + radius; y++) {
                            for (double z = dragCurrentPos.getZ() - radius; z <= dragCurrentPos.getZ() + radius; z++) {
                                double distance = Math.sqrt(
                                    Math.pow(x - dragCurrentPos.getX(), 2) +
                                    Math.pow(y - dragCurrentPos.getY(), 2) +
                                    Math.pow(z - dragCurrentPos.getZ(), 2)
                                );
                                if (distance <= radius) {
                                    previewBlocks.add(new BlockPos((int)x, (int)y, (int)z));
                                }
                            }
                        }
                    }
                }
                case "sphere" -> {
                    // 球体预览
                    double radius = 2.0;
                    for (double x = dragCurrentPos.getX() - radius; x <= dragCurrentPos.getX() + radius; x++) {
                        for (double y = dragCurrentPos.getY() - radius; y <= dragCurrentPos.getY() + radius; y++) {
                            for (double z = dragCurrentPos.getZ() - radius; z <= dragCurrentPos.getZ() + radius; z++) {
                                double distance = Math.sqrt(
                                    Math.pow(x - dragCurrentPos.getX(), 2) +
                                    Math.pow(y - dragCurrentPos.getY(), 2) +
                                    Math.pow(z - dragCurrentPos.getZ(), 2)
                                );
                                if (distance <= radius) {
                                    previewBlocks.add(new BlockPos((int)x, (int)y, (int)z));
                                }
                            }
                        }
                    }
                }
                default -> {
                    // 默认预览 - 单个方块
                    previewBlocks.add(dragCurrentPos);
                }
            }
            
        } catch (Exception e) {
            LOGGER.error("[ToolPreviewManager] 更新预览方块失败", e);
        }
    }
    
    /**
     * 渲染预览效果
     */
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, float tickDelta) {
        if (!enabled || !isDragging || previewBlocks.isEmpty()) {
            return;
        }
        
        try {
            Matrix4f matrix = matrices.peek().getPositionMatrix();
            
            // 渲染预览方块
            renderPreviewBlocks(matrix, vertexConsumers);
            
            // 渲染预览轮廓
            renderPreviewOutline(matrix, vertexConsumers);
            
        } catch (Exception e) {
            LOGGER.error("[ToolPreviewManager] 渲染预览效果失败", e);
        }
    }
    
    /**
     * 渲染预览方块
     */
    private void renderPreviewBlocks(Matrix4f matrix, VertexConsumerProvider vertexConsumers) {
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getTranslucent());
        
        for (BlockPos pos : previewBlocks) {
            renderBlockPreview(matrix, vertexConsumer, pos, previewColor);
        }
    }
    
    /**
     * 渲染预览轮廓
     */
    private void renderPreviewOutline(Matrix4f matrix, VertexConsumerProvider vertexConsumers) {
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getLines());
        
        for (BlockPos pos : previewBlocks) {
            renderBlockOutline(matrix, vertexConsumer, pos, previewOutlineColor);
        }
    }
    
    /**
     * 渲染单个方块的预览效果
     */
    private void renderBlockPreview(Matrix4f matrix, VertexConsumer vertexConsumer, BlockPos pos, float[] color) {
        Box box = new Box(pos);
        
        // 渲染方块的六个面
        renderQuad(vertexConsumer, matrix, box, color, 0, 1, 2, 3); // 底面
        renderQuad(vertexConsumer, matrix, box, color, 4, 5, 6, 7); // 顶面
        renderQuad(vertexConsumer, matrix, box, color, 0, 1, 5, 4); // 前面
        renderQuad(vertexConsumer, matrix, box, color, 2, 3, 7, 6); // 后面
        renderQuad(vertexConsumer, matrix, box, color, 0, 4, 7, 3); // 左面
        renderQuad(vertexConsumer, matrix, box, color, 1, 2, 6, 5); // 右面
    }
    
    /**
     * 渲染单个方块的轮廓
     */
    private void renderBlockOutline(Matrix4f matrix, VertexConsumer vertexConsumer, BlockPos pos, float[] color) {
        Box box = new Box(pos);
        
        // 渲染方块的12条边
        renderLine(vertexConsumer, matrix, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, color);
        renderLine(vertexConsumer, matrix, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, color);
        renderLine(vertexConsumer, matrix, box.maxX, box.maxY, box.minZ, box.minX, box.maxY, box.minZ, color);
        renderLine(vertexConsumer, matrix, box.minX, box.maxY, box.minZ, box.minX, box.minY, box.minZ, color);
        
        renderLine(vertexConsumer, matrix, box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ, color);
        renderLine(vertexConsumer, matrix, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, color);
        renderLine(vertexConsumer, matrix, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, color);
        renderLine(vertexConsumer, matrix, box.minX, box.maxY, box.maxZ, box.minX, box.minY, box.maxZ, color);
        
        renderLine(vertexConsumer, matrix, box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ, color);
        renderLine(vertexConsumer, matrix, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, color);
        renderLine(vertexConsumer, matrix, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, color);
        renderLine(vertexConsumer, matrix, box.minX, box.maxY, box.minZ, box.minX, box.maxY, box.maxZ, color);
    }
    
    /**
     * 渲染四边形面
     */
    private void renderQuad(VertexConsumer vertexConsumer, Matrix4f matrix, Box box, float[] color, int v1, int v2, int v3, int v4) {
        double[][] vertices = {
            {box.minX, box.minY, box.minZ}, // 0
            {box.maxX, box.minY, box.minZ}, // 1
            {box.maxX, box.maxY, box.minZ}, // 2
            {box.minX, box.maxY, box.minZ}, // 3
            {box.minX, box.minY, box.maxZ}, // 4
            {box.maxX, box.minY, box.maxZ}, // 5
            {box.maxX, box.maxY, box.maxZ}, // 6
            {box.minX, box.maxY, box.maxZ}  // 7
        };
        
        // Translucent需要完整的顶点属性
        vertexConsumer.vertex(matrix, (float)vertices[v1][0], (float)vertices[v1][1], (float)vertices[v1][2])
                     .color(color[0], color[1], color[2], color[3])
                     .texture(0.0f, 0.0f)
                     .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                     .light(net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE)
                     .normal(0, 1, 0);
        
        vertexConsumer.vertex(matrix, (float)vertices[v2][0], (float)vertices[v2][1], (float)vertices[v2][2])
                     .color(color[0], color[1], color[2], color[3])
                     .texture(1.0f, 0.0f)
                     .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                     .light(net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE)
                     .normal(0, 1, 0);
        
        vertexConsumer.vertex(matrix, (float)vertices[v3][0], (float)vertices[v3][1], (float)vertices[v3][2])
                     .color(color[0], color[1], color[2], color[3])
                     .texture(1.0f, 1.0f)
                     .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                     .light(net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE)
                     .normal(0, 1, 0);
        
        vertexConsumer.vertex(matrix, (float)vertices[v4][0], (float)vertices[v4][1], (float)vertices[v4][2])
                     .color(color[0], color[1], color[2], color[3])
                     .texture(0.0f, 1.0f)
                     .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                     .light(net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE)
                     .normal(0, 1, 0);
    }
    
    /**
     * 渲染线段
     */
    private void renderLine(VertexConsumer vertexConsumer, Matrix4f matrix, double x1, double y1, double z1, double x2, double y2, double z2, float[] color) {
        // Minecraft 1.21.4的Lines需要normal来正确结束顶点
        vertexConsumer.vertex(matrix, (float)x1, (float)y1, (float)z1)
                     .color(color[0], color[1], color[2], color[3])
                     .normal(0, 1, 0);
        vertexConsumer.vertex(matrix, (float)x2, (float)y2, (float)z2)
                     .color(color[0], color[1], color[2], color[3])
                     .normal(0, 1, 0);
    }
    
    /**
     * 设置笔刷预览半径
     */
    public void setBrushPreviewRadius(float radius) {
        this.brushPreviewRadius = radius;
        if (isDragging) {
            updatePreviewBlocks();
        }
    }
    
    /**
     * 获取预览方块数量
     */
    public int getPreviewBlockCount() {
        return previewBlocks.size();
    }
    
    /**
     * 检查是否正在拖拽
     */
    public boolean isDragging() {
        return isDragging;
    }
    
    /**
     * 获取拖拽起始位置
     */
    public BlockPos getDragStartPos() {
        return dragStartPos;
    }
    
    /**
     * 获取当前拖拽位置
     */
    public BlockPos getDragCurrentPos() {
        return dragCurrentPos;
    }
    
    // Getters and Setters
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        LOGGER.info("[ToolPreviewManager] 预览管理器{}", enabled ? "启用" : "禁用");
    }
    
    public float[] getPreviewColor() {
        return previewColor.clone();
    }
    
    public void setPreviewColor(float[] color) {
        this.previewColor = color.clone();
    }
    
    public float[] getPreviewOutlineColor() {
        return previewOutlineColor.clone();
    }
    
    public void setPreviewOutlineColor(float[] color) {
        this.previewOutlineColor = color.clone();
    }
    
    public float getBrushPreviewRadius() {
        return brushPreviewRadius;
    }
}
