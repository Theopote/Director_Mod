package com.chronoblocks.client.renderer;
import com.chronoblocks.client.renderer.mesh.BlockOutlineMeshCache;
import com.chronoblocks.core.selection.SelectionManager;
import com.chronoblocks.core.selection.SelectionTool;
import com.chronoblocks.core.selection.tools.geometry.GeometryConfig;
import com.chronoblocks.core.selection.tools.geometry.GeometryShape;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 🎯 工具预览管理器
 * <p>
 * 负责管理选择工具的预览效果，包括：
 * - 拖拽预览框
 * - 笔刷范围预览
 * - 实时预览更新
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class ToolPreviewManager {
    // 全局设置中心（新系统优先读取）
    private final com.chronoblocks.client.settings.RenderSettings settings = com.chronoblocks.client.settings.RenderSettings.get();
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ToolPreviewManager.class);
    
    /** 单例实例 */
    private static volatile ToolPreviewManager INSTANCE;
    
    /** 是否启用预览 */
    private boolean enabled = true;
    
    /** 当前拖拽状态 */
    private boolean isDragging = false;
    private BlockPos dragStartPos = null;
    private BlockPos dragCurrentPos = null;

    /** 当前选区预览包围盒 */
    private boolean selectionPreviewActive = false;
    private Box selectionPreviewBox = null;
    private float[] selectionPreviewFillColor = {0.2f, 0.7f, 1.0f, 0.25f};
    private float[] selectionPreviewOutlineColor = {0.2f, 0.7f, 1.0f, 0.75f};
    
    /** 预览方块集合 */
    private final Set<BlockPos> previewBlocks = new HashSet<>();
    
    /** 魔棒工具的悬停预览方块（独立存储，用于hover预览） */
    private Set<BlockPos> magicWandHoverBlocks = new HashSet<>();
    
    /** 魔棒预览深度映射（用于渐变效果） */
    private Map<BlockPos, Integer> magicWandDepthMap = new HashMap<>();
    
    /** 魔棒预览最大深度 */
    private int magicWandMaxDepth = 0;
    
    /** 魔棒预览是否激活 */
    private boolean magicWandPreviewActive = false;

    // ==================== 刷选工具（Brush）预览 ====================
    // ======================= 刷选 / 几何体积预览 =======================
    /** 刷选/几何工具的"空间范围预览"方块集合（统一体积预览） */
    private final Object brushVolumeLock = new Object();
    private Set<BlockPos> brushVolumeBlocks = Set.of();
    private boolean brushVolumeActive = false;

    // ======================= 面选择预览 =======================
    private Set<BlockPos> facePreviewBlocks = Set.of();
    private boolean facePreviewActive = false;
    private FacePreviewTint facePreviewTint = FacePreviewTint.BASE;

    private Set<BlockPos> faceHoverBlocks = new HashSet<>();
    private Map<BlockPos, Integer> faceHoverDepthMap = new HashMap<>();
    private BlockPos faceHoverCenter = null;
    private Direction faceHoverNormal = null;
    private boolean faceHoverPreviewActive = false;
    private float faceHoverAlpha = 0.0f;
    
    // ======================= 套索预览 =======================
    private final Object lassoLock = new Object();
    private List<BlockPos> lassoPathWorld = List.of();
    private boolean lassoPathClosed = false;
    private Set<BlockPos> lassoFillBlocks = Set.of();
    private boolean lassoPreviewActive = false;

    // ======================= 几何预览 =======================
    private final Object geometryLock = new Object();
    private Set<BlockPos> geometryPreviewBlocks = Set.of();
    private GeometryShape geometryPreviewShape = GeometryShape.SPHERE;
    private GeometryConfig geometryPreviewConfig = null;
    private boolean geometryPreviewActive = false;
    private final float[] geometryLineColor = {0.1f, 0.65f, 1.0f, 0.9f};


    /** 是否显示填充（统一控制预览盒/面预览/面 Hover 等的填充） */
    // 预览默认轮廓颜色（作为 RenderSettings 缺省回退）
    private static final float[] DEFAULT_PREVIEW_OUTLINE_COLOR = {1.0f, 1.0f, 0.0f, 0.8f};
    private static final float[] DEFAULT_VOLUME_FILL_COLOR = {0.2f, 0.6f, 1.0f, 0.25f};
    
    // 通用贪婪网格缓存（基于方块集合，用于预览外轮廓线）
    private final BlockOutlineMeshCache previewMeshCache = new BlockOutlineMeshCache();

    private ToolPreviewManager() {
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
     * 便捷方法：获取单例实例（别名方法，与 SelectionEvents API 保持一致）
     */
    public static ToolPreviewManager get() {
        return getInstance();
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
    
    // ==================== 魔棒预览专用 API ====================
    
    /**
     * 更新魔棒悬停预览（带深度信息）
     * 
     * @param blocks 预览的方块集合
     * @param depthMap 深度映射（可选，用于渐变效果）
     */
    public void updateMagicWandPreview(Set<BlockPos> blocks, Map<BlockPos, Integer> depthMap) {
        if (blocks == null || blocks.isEmpty()) {
            this.magicWandHoverBlocks = new HashSet<>();
            this.magicWandDepthMap = new HashMap<>();
            this.magicWandMaxDepth = 0;
            this.magicWandPreviewActive = false;
        } else {
            this.magicWandHoverBlocks = new HashSet<>(blocks);
            this.magicWandDepthMap = depthMap != null ? new HashMap<>(depthMap) : new HashMap<>();
            this.magicWandMaxDepth = this.magicWandDepthMap.values().stream()
                .max(Integer::compareTo).orElse(0);
            this.magicWandPreviewActive = true;
        }
        LOGGER.debug("[ToolPreviewManager] 更新魔棒预览: {} 个方块，最大深度={}", 
            blocks != null ? blocks.size() : 0, magicWandMaxDepth);
    }

    /**
     * 清除魔棒预览
     */
    public void clearMagicWandPreview() {
        this.magicWandHoverBlocks.clear();
        this.magicWandPreviewActive = false;
        LOGGER.debug("[ToolPreviewManager] 清除魔棒预览");
    }


    /**
     * 判断魔棒预览是否激活
     */
    public boolean isMagicWandPreviewActive() {
        return magicWandPreviewActive && !magicWandHoverBlocks.isEmpty();
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
            if ("click".equalsIgnoreCase(toolId)) {
                previewBlocks.clear();
                return;
            }
            
            previewBlocks.clear();
            
            switch (toolId) {
                case "box":
                case "box_v3": {
                    // 框选工具的实时预览由 BoxSelectTool 通过 setPreviewBox() 管理，避免这里 O(n^3) 扫描体积方块
                    // （SelectionManager 已对 box/box_v3 跳过 start/update/endDragPreview 调用）
                    previewBlocks.clear();
                    break;
                }
                case "sphere": {
                    populateSpherePreview(dragCurrentPos);
                    break;
                }
                // "geometry" 工具已合并到刷选工具中，不再单独处理
                // case "geometry": 已移除
                default: // 默认预览 - 单个方块
                    previewBlocks.add(dragCurrentPos);
                    break;
            }
            
        } catch (Exception e) {
            LOGGER.error("[ToolPreviewManager] 更新预览方块失败", e);
        }
    }
    
    /**
     * 渲染预览效果
     */
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, float tickDelta) {
        if (!enabled) {
            return;
        }
        
        try {
            // ✅ 不再同步到本地字段，渲染时直接从 settings 读取（实时生效）

            Matrix4f matrix = matrices.peek().getPositionMatrix();
            Vec3d cameraPos = getCameraPosition();

            SelectionManager selectionManager = SelectionManager.get();
            String activeToolId = "";
            if (selectionManager != null && selectionManager.hasCurrentTool()) {
                SelectionTool tool = selectionManager.getCurrentTool();
                if (tool != null) {
                    activeToolId = tool.getId();
                }
            }
            boolean isClickTool = "click".equalsIgnoreCase(activeToolId);

            // 1. 渲染拖拽预览（线框）
            if (isDragging && !previewBlocks.isEmpty() && !isClickTool) {
                renderPreviewOutline(matrix, vertexConsumers);
            }
            
            // 2. 渲染魔棒悬停预览（淡蓝色，区别于拖拽预览）
            if (isMagicWandPreviewActive()) {
                renderMagicWandPreview(matrix, vertexConsumers);
            }

            // 3. 渲染刷选/几何体积预览（统一空间范围）
            renderBrushVolumePreview(matrix, vertexConsumers, cameraPos);

            renderGeometryPreview(matrix, vertexConsumers);

            renderLassoPreview(matrices, vertexConsumers, tickDelta);

            tickFaceHoverAlpha(tickDelta);
            if (faceHoverAlpha > 0.01f && !faceHoverBlocks.isEmpty()) {
                renderFaceHoverPreview(matrix, vertexConsumers, faceHoverAlpha);
            }

            if (facePreviewActive && !facePreviewBlocks.isEmpty()) {
                renderFacePreview(matrix, vertexConsumers);
            }

            if (selectionPreviewActive && selectionPreviewBox != null) {
                renderSelectionPreviewBox(matrix, vertexConsumers, cameraPos);
            }
            
        } catch (Exception e) {
            LOGGER.error("[ToolPreviewManager] 渲染预览效果失败", e);
        }
    }

    /**
     * 渲染魔棒悬停预览（支持渐变效果）
     */
    private void renderMagicWandPreview(Matrix4f matrix, VertexConsumerProvider vertexConsumers) {
        if (vertexConsumers == null || magicWandHoverBlocks.isEmpty()) {
            return;
        }
        if (!settings.tool_showOutline) {
            return;
        }

        // 颜色在本方法中统一通过 outlineBaseColor 控制
        float[] outlineBaseColor = {0.2f, 0.6f, 1.0f, 0.7f};

        VertexConsumer lineConsumer = vertexConsumers.getBuffer(RenderLayers.getLineOverlay());

        boolean useGradient = magicWandMaxDepth > 0 && !magicWandDepthMap.isEmpty();

        // 使用贪婪合并后的外轮廓线；填充可选，这里仅线框
        float[] outlineColorBase = new float[]{outlineBaseColor[0], outlineBaseColor[1], outlineBaseColor[2], outlineBaseColor[3]};
        BlockOutlineMeshCache.Mesh mesh = previewMeshCache.getOrCreateMesh(magicWandHoverBlocks);
        for (BlockOutlineMeshCache.Line ln : mesh.lines()) {
            if (useGradient) {
                // 若需要基于深度渐变，可按端点临近方块的深度决定透明度；这里先保持统一色
            }
            renderLine(lineConsumer, matrix,
                ln.x1(), ln.y1(), ln.z1(),
                ln.x2(), ln.y2(), ln.z2(),
                    outlineColorBase);
        }

        // 注意：工具预览不需要包围盒
    }

    /**
     * 渲染刷选/几何体积预览（统一 3D 空间范围）
     */
    private void renderBrushVolumePreview(Matrix4f matrix, VertexConsumerProvider vertexConsumers, Vec3d cameraPos) {
        if (vertexConsumers == null) {
            return;
        }
        if (!settings.tool_showFill && !settings.tool_showOutline) {
            return;
        }

        Set<BlockPos> blocksSnapshot;
        synchronized (brushVolumeLock) {
            if (!brushVolumeActive || brushVolumeBlocks.isEmpty()) {
                return;
            }
            blocksSnapshot = brushVolumeBlocks;
        }

        BlockOutlineMeshCache.Mesh mesh = previewMeshCache.getOrCreateMesh(blocksSnapshot);

        if (settings.tool_showFill && !mesh.quads().isEmpty()) {
            float[] base = settings.tool_fillColor != null ? settings.tool_fillColor : DEFAULT_VOLUME_FILL_COLOR;
            VertexConsumer fillConsumer = vertexConsumers.getBuffer(RenderLayers.getSelectionFillLayer());
            for (BlockOutlineMeshCache.Quad quad : mesh.quads()) {
                renderVolumeQuad(fillConsumer, matrix, quad, base, cameraPos);
            }
        }

        if (settings.tool_showOutline && !mesh.lines().isEmpty()) {
            float[] outline = settings.tool_outlineColor != null ? settings.tool_outlineColor : DEFAULT_PREVIEW_OUTLINE_COLOR;
            VertexConsumer lineConsumer = vertexConsumers.getBuffer(RenderLayers.getLineOverlay());
            for (BlockOutlineMeshCache.Line ln : mesh.lines()) {
                renderLine(lineConsumer, matrix,
                    ln.x1() - cameraPos.x, ln.y1() - cameraPos.y, ln.z1() - cameraPos.z,
                    ln.x2() - cameraPos.x, ln.y2() - cameraPos.y, ln.z2() - cameraPos.z,
                    outline);
            }
        }
    }

    private void renderVolumeQuad(VertexConsumer consumer, Matrix4f matrix,
                                  BlockOutlineMeshCache.Quad quad, float[] color, Vec3d cameraPos) {
        if (consumer == null || quad == null || color == null || color.length < 4) {
            return;
        }

        float nx = quad.dir() != null ? quad.dir().getOffsetX() : 0f;
        float ny = quad.dir() != null ? quad.dir().getOffsetY() : 0f;
        float nz = quad.dir() != null ? quad.dir().getOffsetZ() : 0f;

        emitVolumeVertex(consumer, matrix, quad.p1(), color, nx, ny, nz, cameraPos);
        emitVolumeVertex(consumer, matrix, quad.p2(), color, nx, ny, nz, cameraPos);
        emitVolumeVertex(consumer, matrix, quad.p3(), color, nx, ny, nz, cameraPos);
        emitVolumeVertex(consumer, matrix, quad.p4(), color, nx, ny, nz, cameraPos);
    }

    private void emitVolumeVertex(VertexConsumer consumer, Matrix4f matrix,
                                  BlockPos pos, float[] color,
                                  float nx, float ny, float nz, Vec3d cameraPos) {
        consumer.vertex(matrix,
                (float) (pos.getX() - cameraPos.x),
                (float) (pos.getY() - cameraPos.y),
                (float) (pos.getZ() - cameraPos.z))
            .color(color[0], color[1], color[2], color[3])
            .texture(0.5f, 0.5f)
            .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
            .light(net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE)
            .normal(nx, ny, nz);
    }

    private void renderGeometryPreview(Matrix4f matrix, VertexConsumerProvider vertexConsumers) {
        Set<BlockPos> blocksSnapshot;
        GeometryShape shapeSnapshot;
        GeometryConfig configSnapshot;
        synchronized (geometryLock) {
            if (!geometryPreviewActive || geometryPreviewBlocks.isEmpty()) {
                return;
            }
            blocksSnapshot = geometryPreviewBlocks;
            shapeSnapshot = geometryPreviewShape;
            configSnapshot = geometryPreviewConfig;
        }

        VertexConsumer lineConsumer = vertexConsumers.getBuffer(RenderLayers.getGeometryLineLayer());

        // 使用贪婪合并后的外轮廓线，替代逐方块线框
        if (settings.tool_showOutline) {
            BlockOutlineMeshCache.Mesh mesh = previewMeshCache.getOrCreateMesh(blocksSnapshot);
            for (BlockOutlineMeshCache.Line ln : mesh.lines()) {
                renderLine(lineConsumer, matrix,
                    ln.x1(), ln.y1(), ln.z1(),
                    ln.x2(), ln.y2(), ln.z2(),
                    geometryLineColor);
            }
        }

        // 注意：工具预览不需要包围盒

        if (shapeSnapshot != null && configSnapshot != null) {
            // 可选：未来根据形状类型渲染额外的轴向辅助线等，这里留作扩展钩子
        }
    }

    private void renderBoundingBox(Matrix4f matrix, VertexConsumer lineConsumer, Set<BlockPos> blocks, float[] color) {
        if (blocks == null || blocks.isEmpty()) {
            return;
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (BlockPos pos : blocks) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        float x1 = minX;
        float y1 = minY;
        float z1 = minZ;
        float x2 = maxX + 1.0f;
        float y2 = maxY + 1.0f;
        float z2 = maxZ + 1.0f;

        drawLine(lineConsumer, matrix, x1, y1, z1, x2, y1, z1, color);
        drawLine(lineConsumer, matrix, x2, y1, z1, x2, y1, z2, color);
        drawLine(lineConsumer, matrix, x2, y1, z2, x1, y1, z2, color);
        drawLine(lineConsumer, matrix, x1, y1, z2, x1, y1, z1, color);

        drawLine(lineConsumer, matrix, x1, y2, z1, x2, y2, z1, color);
        drawLine(lineConsumer, matrix, x2, y2, z1, x2, y2, z2, color);
        drawLine(lineConsumer, matrix, x2, y2, z2, x1, y2, z2, color);
        drawLine(lineConsumer, matrix, x1, y2, z2, x1, y2, z1, color);

        drawLine(lineConsumer, matrix, x1, y1, z1, x1, y2, z1, color);
        drawLine(lineConsumer, matrix, x2, y1, z1, x2, y2, z1, color);
        drawLine(lineConsumer, matrix, x2, y1, z2, x2, y2, z2, color);
        drawLine(lineConsumer, matrix, x1, y1, z2, x1, y2, z2, color);
    }
    
    /**
     * 绘制一条线段
     */
    private void drawLine(VertexConsumer consumer, Matrix4f matrix,
                         float x1, float y1, float z1,
                         float x2, float y2, float z2,
                         float[] color) {
        renderLine(consumer, matrix, x1, y1, z1, x2, y2, z2, color);
    }
    
    /**
     * 渲染预览轮廓
     */
    private void renderPreviewOutline(Matrix4f matrix, VertexConsumerProvider vertexConsumers) {
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayers.getLineOverlay());

        // 使用贪婪合并后的外轮廓线
        if (!settings.tool_showOutline) {
            return;
        }
        if (!previewBlocks.isEmpty()) {
            BlockOutlineMeshCache.Mesh mesh = previewMeshCache.getOrCreateMesh(previewBlocks);
            for (BlockOutlineMeshCache.Line ln : mesh.lines()) {
                renderLine(vertexConsumer, matrix,
                    ln.x1(), ln.y1(), ln.z1(),
                    ln.x2(), ln.y2(), ln.z2(),
                    (settings.tool_outlineColor != null ? settings.tool_outlineColor : DEFAULT_PREVIEW_OUTLINE_COLOR));
            }
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
    

    private void renderFacePreview(Matrix4f matrix, VertexConsumerProvider vertexConsumers) {
        if (vertexConsumers == null || facePreviewBlocks.isEmpty()) {
            return;
        }

        float[] fillColor = facePreviewTint.fillColor;
        float[] outlineColor = facePreviewTint.outlineColor;

        VertexConsumer fillConsumer = vertexConsumers.getBuffer(RenderLayers.getPreviewLayer());
        VertexConsumer lineConsumer = vertexConsumers.getBuffer(RenderLayers.getLineOverlay());

        if (settings.tool_showFill) {
            for (BlockPos pos : facePreviewBlocks) {
                renderBlockPreview(matrix, fillConsumer, pos, fillColor);
            }
        }
        // 使用贪婪合并生成的外轮廓线，避免内部线与公用边
        if (settings.tool_showOutline) {
            BlockOutlineMeshCache.Mesh mesh = previewMeshCache.getOrCreateMesh(facePreviewBlocks);
            for (BlockOutlineMeshCache.Line ln : mesh.lines()) {
                renderLine(lineConsumer, matrix,
                    ln.x1(), ln.y1(), ln.z1(),
                    ln.x2(), ln.y2(), ln.z2(),
                    outlineColor);
            }
        }
    }

    private void tickFaceHoverAlpha(float tickDelta) {
        float target = faceHoverPreviewActive ? 1.0f : 0.0f;
        float speed = MathHelper.clamp(tickDelta * 8.0f, 0.0f, 1.0f);
        faceHoverAlpha = MathHelper.lerp(speed, faceHoverAlpha, target);
        if (!faceHoverPreviewActive && faceHoverAlpha <= 0.01f) {
            faceHoverAlpha = 0.0f;
            faceHoverBlocks.clear();
            faceHoverDepthMap.clear();
            faceHoverCenter = null;
            faceHoverNormal = null;
        }
    }

    private void renderFaceHoverPreview(Matrix4f matrix, VertexConsumerProvider vertexConsumers, float alpha) {
        if (vertexConsumers == null || alpha <= 0.01f || faceHoverBlocks.isEmpty()) {
            return;
        }

        VertexConsumer fillConsumer = vertexConsumers.getBuffer(RenderLayers.getPreviewLayer());
        VertexConsumer lineConsumer = vertexConsumers.getBuffer(RenderLayers.getLineOverlay());

        final float baseR = 0.2f;
        final float baseG = 0.7f;
        final float baseB = 1.0f;
        final float baseA = 0.25f;

        final float outlineR = 1.0f;
        final float outlineG = 0.9f;
        final float outlineB = 0.4f;
        final float outlineA = 0.9f;

        final float arrowR = 0.2f;
        final float arrowG = 0.7f;
        final float arrowB = 1.0f;
        final float arrowA = 1.0f;

        int maxDepth = 0;
        if (!faceHoverDepthMap.isEmpty()) {
            for (int depth : faceHoverDepthMap.values()) {
                if (depth > maxDepth) {
                    maxDepth = depth;
                }
            }
        }
        boolean useGradient = maxDepth > 0;

        if (settings.tool_showFill) {
            for (BlockPos pos : faceHoverBlocks) {
                float intensity = 1.0f;
                float alphaFactor = 1.0f;
                if (useGradient) {
                    int depth = faceHoverDepthMap.getOrDefault(pos, 0);
                    float t = (float) depth / maxDepth;
                    intensity = 1.0f - t * 0.6f;
                    alphaFactor = 1.0f - t * 0.5f;
                }

                float[] color = new float[]{
                    baseR * intensity,
                    baseG * intensity,
                    baseB * intensity,
                    baseA * alphaFactor * alpha
                };
                renderBlockPreview(matrix, fillConsumer, pos, color);
            }
        }

        // 外轮廓线（贪婪合并结果）：更清晰的轮廓，不显示内部线
        if (settings.tool_showOutline) {
            BlockOutlineMeshCache.Mesh mesh = previewMeshCache.getOrCreateMesh(faceHoverBlocks);
            for (BlockOutlineMeshCache.Line ln : mesh.lines()) {
                renderLine(lineConsumer, matrix,
                    ln.x1(), ln.y1(), ln.z1(),
                    ln.x2(), ln.y2(), ln.z2(),
                    new float[]{
                        outlineR,
                        outlineG,
                        outlineB,
                        outlineA * alpha
                    });
            }
        }

        if (settings.tool_showOutline) {
            renderBoundingBox(matrix, lineConsumer, faceHoverBlocks, new float[]{
                outlineR,
                outlineG,
                outlineB,
                outlineA * alpha
            });
        }

        if (faceHoverCenter != null && faceHoverNormal != null) {
            renderFaceHoverNormalArrow(matrix, lineConsumer, faceHoverCenter, faceHoverNormal, new float[]{
                arrowR,
                arrowG,
                arrowB,
                arrowA * alpha
            });
        }
    }

    private void renderSelectionPreviewBox(Matrix4f matrix, VertexConsumerProvider vertexConsumers, Vec3d cameraPos) {
        Box box = selectionPreviewBox;
        if (vertexConsumers == null || box == null) {
            return;
        }

        Box adjusted = box.offset(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        VertexConsumer fillConsumer = vertexConsumers.getBuffer(RenderLayers.getPreviewLayer());
        VertexConsumer lineConsumer = vertexConsumers.getBuffer(RenderLayers.getLineOverlay());

        if (settings.tool_showFill) {
            drawSelectionPreviewFill(fillConsumer, matrix, adjusted, selectionPreviewFillColor);
        }
        if (settings.tool_showOutline) {
            drawSelectionPreviewEdges(lineConsumer, matrix, adjusted, selectionPreviewOutlineColor);
        }
    }

    private void drawSelectionPreviewFill(VertexConsumer consumer, Matrix4f matrix, Box box, float[] color) {
        if (consumer == null || color == null || color.length < 4) {
            return;
        }

        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;

        float r = color[0];
        float g = color[1];
        float b = color[2];
        float a = color[3];

        consumer.vertex(matrix, (float) minX, (float) minY, (float) maxZ)
                .color(r, g, b, a)
                .texture(0.5f, 0.5f)
                .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                .light(net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(0, 0, 1);
        consumer.vertex(matrix, (float) maxX, (float) minY, (float) maxZ)
                .color(r, g, b, a)
                .texture(0.5f, 0.5f)
                .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                .light(net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(0, 0, 1);
        consumer.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ)
                .color(r, g, b, a)
                .texture(0.5f, 0.5f)
                .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                .light(net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(0, 0, 1);
        consumer.vertex(matrix, (float) minX, (float) maxY, (float) maxZ)
                .color(r, g, b, a)
                .texture(0.5f, 0.5f)
                .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                .light(net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(0, 0, 1);

        consumer.vertex(matrix, (float) maxX, (float) minY, (float) minZ)
                .color(r, g, b, a)
                .texture(0.5f, 0.5f)
                .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                .light(net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(0, 0, -1);
        consumer.vertex(matrix, (float) minX, (float) minY, (float) minZ)
                .color(r, g, b, a)
                .texture(0.5f, 0.5f)
                .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                .light(net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(0, 0, -1);
        consumer.vertex(matrix, (float) minX, (float) maxY, (float) minZ)
                .color(r, g, b, a)
                .texture(0.5f, 0.5f)
                .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                .light(net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(0, 0, -1);
        consumer.vertex(matrix, (float) maxX, (float) maxY, (float) minZ)
                .color(r, g, b, a)
                .texture(0.5f, 0.5f)
                .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                .light(net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(0, 0, -1);

        consumer.vertex(matrix, (float) minX, (float) minY, (float) minZ)
                .color(r, g, b, a)
                .texture(0.5f, 0.5f)
                .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                .light(net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(-1, 0, 0);
        consumer.vertex(matrix, (float) minX, (float) minY, (float) maxZ)
                .color(r, g, b, a)
                .texture(0.5f, 0.5f)
                .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                .light(net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(-1, 0, 0);
        consumer.vertex(matrix, (float) minX, (float) maxY, (float) maxZ)
                .color(r, g, b, a)
                .texture(0.5f, 0.5f)
                .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                .light(net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(-1, 0, 0);
        consumer.vertex(matrix, (float) minX, (float) maxY, (float) minZ)
                .color(r, g, b, a)
                .texture(0.5f, 0.5f)
                .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                .light(net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(-1, 0, 0);

        consumer.vertex(matrix, (float) maxX, (float) minY, (float) maxZ)
                .color(r, g, b, a)
                .texture(0.5f, 0.5f)
                .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                .light(net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(1, 0, 0);
        consumer.vertex(matrix, (float) maxX, (float) minY, (float) minZ)
                .color(r, g, b, a)
                .texture(0.5f, 0.5f)
                .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                .light(net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(1, 0, 0);
        consumer.vertex(matrix, (float) maxX, (float) maxY, (float) minZ)
                .color(r, g, b, a)
                .texture(0.5f, 0.5f)
                .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                .light(net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(1, 0, 0);
        consumer.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ)
                .color(r, g, b, a)
                .texture(0.5f, 0.5f)
                .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                .light(net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(1, 0, 0);

        consumer.vertex(matrix, (float) minX, (float) maxY, (float) maxZ)
                .color(r, g, b, a)
                .texture(0.5f, 0.5f)
                .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                .light(net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(0, 1, 0);
        consumer.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ)
                .color(r, g, b, a)
                .texture(0.5f, 0.5f)
                .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                .light(net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(0, 1, 0);
        consumer.vertex(matrix, (float) maxX, (float) maxY, (float) minZ)
                .color(r, g, b, a)
                .texture(0.5f, 0.5f)
                .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                .light(net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(0, 1, 0);
        consumer.vertex(matrix, (float) minX, (float) maxY, (float) minZ)
                .color(r, g, b, a)
                .texture(0.5f, 0.5f)
                .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                .light(net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(0, 1, 0);

        consumer.vertex(matrix, (float) minX, (float) minY, (float) minZ)
                .color(r, g, b, a)
                .texture(0.5f, 0.5f)
                .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                .light(net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(0, -1, 0);
        consumer.vertex(matrix, (float) maxX, (float) minY, (float) minZ)
                .color(r, g, b, a)
                .texture(0.5f, 0.5f)
                .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                .light(net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(0, -1, 0);
        consumer.vertex(matrix, (float) maxX, (float) minY, (float) maxZ)
                .color(r, g, b, a)
                .texture(0.5f, 0.5f)
                .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                .light(net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(0, -1, 0);
        consumer.vertex(matrix, (float) minX, (float) minY, (float) maxZ)
                .color(r, g, b, a)
                .texture(0.5f, 0.5f)
                .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                .light(net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(0, -1, 0);
    }

    private void drawSelectionPreviewEdges(VertexConsumer consumer, Matrix4f matrix, Box box, float[] color) {
        if (consumer == null || color == null || color.length < 4) {
            return;
        }

        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;

        renderLine(consumer, matrix, minX, minY, minZ, maxX, minY, minZ, color);
        renderLine(consumer, matrix, maxX, minY, minZ, maxX, minY, maxZ, color);
        renderLine(consumer, matrix, maxX, minY, maxZ, minX, minY, maxZ, color);
        renderLine(consumer, matrix, minX, minY, maxZ, minX, minY, minZ, color);

        renderLine(consumer, matrix, minX, maxY, minZ, maxX, maxY, minZ, color);
        renderLine(consumer, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, color);
        renderLine(consumer, matrix, maxX, maxY, maxZ, minX, maxY, maxZ, color);
        renderLine(consumer, matrix, minX, maxY, maxZ, minX, maxY, minZ, color);

        renderLine(consumer, matrix, minX, minY, minZ, minX, maxY, minZ, color);
        renderLine(consumer, matrix, maxX, minY, minZ, maxX, maxY, minZ, color);
        renderLine(consumer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, color);
        renderLine(consumer, matrix, minX, minY, maxZ, minX, maxY, maxZ, color);
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
    
    private void renderFaceHoverNormalArrow(Matrix4f matrix,
                                            VertexConsumer lineConsumer,
                                            BlockPos center,
                                            Direction normal,
                                            float[] color) {
        double cx = center.getX() + 0.5 + normal.getOffsetX() * 0.501;
        double cy = center.getY() + 0.5 + normal.getOffsetY() * 0.501;
        double cz = center.getZ() + 0.5 + normal.getOffsetZ() * 0.501;

        double shaftHalf = 0.45;
        double tipX = cx + normal.getOffsetX() * shaftHalf;
        double tipY = cy + normal.getOffsetY() * shaftHalf;
        double tipZ = cz + normal.getOffsetZ() * shaftHalf;
        double baseX = cx - normal.getOffsetX() * shaftHalf * 0.6;
        double baseY = cy - normal.getOffsetY() * shaftHalf * 0.6;
        double baseZ = cz - normal.getOffsetZ() * shaftHalf * 0.6;

        renderLine(lineConsumer, matrix, baseX, baseY, baseZ, tipX, tipY, tipZ, color);

        double headSize = 0.25;
        double[][] sideOffsets = switch (normal.getAxis()) {
            case X -> new double[][]{{0, 1, 0}, {0, 0, 1}};
            case Y -> new double[][]{{1, 0, 0}, {0, 0, 1}};
            case Z -> new double[][]{{1, 0, 0}, {0, 1, 0}};
        };

        for (double[] offset : sideOffsets) {
            double hx = tipX - normal.getOffsetX() * headSize + offset[0] * headSize;
            double hy = tipY - normal.getOffsetY() * headSize + offset[1] * headSize;
            double hz = tipZ - normal.getOffsetZ() * headSize + offset[2] * headSize;
            renderLine(lineConsumer, matrix, tipX, tipY, tipZ, hx, hy, hz, color);
        }
    }

    /**
     * 渲染线段
     */
    private void renderLine(VertexConsumer vertexConsumer, Matrix4f matrix,
                             double x1, double y1, double z1,
                             double x2, double y2, double z2,
                             float[] color) {
        // Minecraft 1.21.4 的 lines 格式要求提供法线分量
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);

        float nx;
        float ny;
        float nz;
        if (length > 1.0e-6) {
            nx = (float) (dx / length);
            ny = (float) (dy / length);
            nz = (float) (dz / length);
        } else {
            nx = 0.0f;
            ny = 1.0f;
            nz = 0.0f;
        }

        vertexConsumer.vertex(matrix, (float) x1, (float) y1, (float) z1)
            .color(color[0], color[1], color[2], color[3])
            .normal(nx, ny, nz);
        vertexConsumer.vertex(matrix, (float) x2, (float) y2, (float) z2)
            .color(color[0], color[1], color[2], color[3])
            .normal(nx, ny, nz);
    }

    private void renderLassoPreview(MatrixStack matrices, VertexConsumerProvider vertexConsumers, float tickDelta) {
        List<BlockPos> pathCopy;
        Set<BlockPos> fillCopy;
        boolean closed;
        synchronized (lassoLock) {
            if (!lassoPreviewActive) {
                return;
            }
            pathCopy = new ArrayList<>(lassoPathWorld);
            fillCopy = new HashSet<>(lassoFillBlocks);
            closed = lassoPathClosed;
        }

        if (pathCopy.isEmpty() && fillCopy.isEmpty()) {
            return;
        }

        long time = System.currentTimeMillis();
        float phase = (time % 2000L) / 2000.0f;
        float pathAlpha = MathHelper.clamp(0.55f + 0.25f * MathHelper.sin(phase * MathHelper.TAU * 2.0f), 0.3f, 0.85f);
        float fillAlpha = MathHelper.clamp(0.25f + 0.12f * MathHelper.sin(phase * MathHelper.TAU), 0.15f, 0.5f);
        float[] pathColor = new float[]{0.0f, 0.65f, 1.0f, pathAlpha};
        float[] fillColor = new float[]{0.0f, 0.55f, 1.0f, fillAlpha};

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        if (!fillCopy.isEmpty()) {
            VertexConsumer fillConsumer = vertexConsumers.getBuffer(RenderLayers.getPreviewLayer());
            for (BlockPos pos : fillCopy) {
                renderBlockPreview(matrix, fillConsumer, pos, fillColor);
            }
        }

        if (pathCopy.size() >= 2) {
            VertexConsumer lineConsumer = vertexConsumers.getBuffer(RenderLayer.getLines());
            BlockPos previous = pathCopy.getFirst();
            for (int i = 1; i < pathCopy.size(); i++) {
                BlockPos current = pathCopy.get(i);
                renderLine(lineConsumer, matrix,
                    previous.getX() + 0.5, previous.getY() + 0.5, previous.getZ() + 0.5,
                    current.getX() + 0.5, current.getY() + 0.5, current.getZ() + 0.5,
                    pathColor);
                previous = current;
            }
            if (closed && pathCopy.size() > 2) {
                BlockPos first = pathCopy.getFirst();
                renderLine(lineConsumer, matrix,
                    previous.getX() + 0.5, previous.getY() + 0.5, previous.getZ() + 0.5,
                    first.getX() + 0.5, first.getY() + 0.5, first.getZ() + 0.5,
                    pathColor);
            }
        }
    }

    
    /**
     * 设置笔刷预览半径
     */
    // ======================= 面选择 API =======================

    public void updateFacePreview(Set<BlockPos> blocks, FacePreviewTint tint) {
        if (blocks == null || blocks.isEmpty()) {
            facePreviewBlocks = Set.of();
            facePreviewActive = false;
            facePreviewTint = FacePreviewTint.BASE;
            return;
        }
        facePreviewBlocks = Set.copyOf(blocks);
        facePreviewTint = tint != null ? tint : FacePreviewTint.BASE;
        facePreviewActive = true;
    }

    public void clearFacePreview() {
        facePreviewBlocks = Set.of();
        facePreviewActive = false;
        facePreviewTint = FacePreviewTint.BASE;
    }

    public void updateFaceHoverPreview(Set<BlockPos> blocks,
                                       Map<BlockPos, Integer> depthMap,
                                       BlockPos center,
                                       Direction faceNormal) {
        if (blocks == null || blocks.isEmpty() || center == null || faceNormal == null) {
            clearFaceHoverPreview();
            return;
        }
        faceHoverBlocks = new HashSet<>(blocks);
        faceHoverDepthMap = depthMap != null ? new HashMap<>(depthMap) : new HashMap<>();
        faceHoverCenter = center.toImmutable();
        faceHoverNormal = faceNormal;
        faceHoverPreviewActive = true;
        faceHoverAlpha = Math.max(faceHoverAlpha, 0.05f);
        LOGGER.debug("[ToolPreviewManager] 更新面 Hover 预览: {} 个方块, normal={}", blocks.size(), faceNormal);
    }

    public void clearFaceHoverPreview() {
        faceHoverPreviewActive = false;
        LOGGER.debug("[ToolPreviewManager] 清除面 Hover 预览（渐隐）");
    }

    public void clearFaceHoverPreviewImmediately() {
        faceHoverPreviewActive = false;
        faceHoverAlpha = 0.0f;
        faceHoverBlocks.clear();
        faceHoverDepthMap.clear();
        faceHoverCenter = null;
        faceHoverNormal = null;
        LOGGER.debug("[ToolPreviewManager] 清除面 Hover 预览（立即）");
    }

    // ======================= 套索预览 API =======================

    public void updateLassoPath(List<BlockPos> path, boolean closed) {
        synchronized (lassoLock) {
            if (path == null || path.isEmpty()) {
                lassoPathWorld = List.of();
                lassoPathClosed = false;
            } else {
                List<BlockPos> copy = new ArrayList<>(path.size());
                for (BlockPos pos : path) {
                    copy.add(pos.toImmutable());
                }
                lassoPathWorld = Collections.unmodifiableList(copy);
                lassoPathClosed = closed;
            }
            lassoPreviewActive = !lassoPathWorld.isEmpty() || !lassoFillBlocks.isEmpty();
        }
    }

    public void updateLassoFill(Iterable<? extends BlockPos> blocks) {
        synchronized (lassoLock) {
            if (blocks == null) {
                lassoFillBlocks = Set.of();
            } else {
                Set<BlockPos> copy = new LinkedHashSet<>();
                for (BlockPos pos : blocks) {
                    if (pos != null) {
                        copy.add(pos.toImmutable());
                    }
                }
                lassoFillBlocks = Collections.unmodifiableSet(copy);
            }
            lassoPreviewActive = !lassoPathWorld.isEmpty() || !lassoFillBlocks.isEmpty();
        }
    }

    public void clearLassoPreview() {
        synchronized (lassoLock) {
            lassoPathWorld = List.of();
            lassoPathClosed = false;
            lassoFillBlocks = Set.of();
            lassoPreviewActive = false;
        }
    }

    // ======================= 几何预览 API =======================

    public void updateGeometryPreview(Set<BlockPos> blocks, GeometryShape shape, GeometryConfig cfg) {
        synchronized (geometryLock) {
            if (blocks == null || blocks.isEmpty()) {
                geometryPreviewBlocks = Set.of();
                geometryPreviewActive = false;
            } else {
                geometryPreviewBlocks = Collections.unmodifiableSet(new LinkedHashSet<>(blocks));
                geometryPreviewActive = true;
            }
            geometryPreviewShape = shape != null ? shape : GeometryShape.SPHERE;
            geometryPreviewConfig = cfg != null ? cfg.copy() : null;
        }
    }

    public void clearGeometryPreview() {
        synchronized (geometryLock) {
            geometryPreviewBlocks = Set.of();
            geometryPreviewActive = false;
            geometryPreviewConfig = null;
        }
    }

    // ======================= 刷选体积预览 API =======================

    /**
     * 更新刷选工具的体积预览。
     * 这里的 blocks 只用于渲染，不会修改 SelectionSet。
     */
    public void updateBrushVolumePreview(Set<BlockPos> blocks) {
        synchronized (brushVolumeLock) {
            if (blocks == null || blocks.isEmpty()) {
                brushVolumeBlocks = Set.of();
                brushVolumeActive = false;
            } else {
                // 使用有序集合，保证网格生成稳定（BlockOutlineMeshCache 内部也会去重）
                brushVolumeBlocks = Collections.unmodifiableSet(new LinkedHashSet<>(blocks));
                brushVolumeActive = true;
            }
        }
        // 体积变化 => 让网格缓存失效
        previewMeshCache.clear();
    }

    /**
     * 清除刷选体积预览。
     */
    public void clearBrushVolumePreview() {
        updateBrushVolumePreview(null);
    }

    private Vec3d getCameraPosition() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.gameRenderer != null && client.gameRenderer.getCamera() != null) {
            return client.gameRenderer.getCamera().getPos();
        }
        return Vec3d.ZERO;
    }
    
    /**
     * 检查是否正在拖拽
     */
    public boolean isDragging() {
        return isDragging;
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

    // 兼容层：统一改写到 RenderSettings（过时）
    @Deprecated
    public float[] getPreviewOutlineColor() {
        return settings != null && settings.tool_outlineColor != null
            ? settings.tool_outlineColor.clone()
            : DEFAULT_PREVIEW_OUTLINE_COLOR.clone();
    }
    
    @Deprecated
    public void setPreviewOutlineColor(float[] color) {
        if (settings != null && color != null && color.length >= 4) {
            settings.tool_outlineColor = color.clone();
        }
    }

    public void setPreviewBox(Box box) {
        if (box == null) {
            clearPreview();
            return;
        }
        this.selectionPreviewBox = box;
        this.selectionPreviewActive = true;
    }

    public void clearPreview() {
        this.selectionPreviewBox = null;
        this.selectionPreviewActive = false;
    }

    // ====================== 便捷方法（委托给 SelectionRenderer） ======================

    private void populateSpherePreview(BlockPos center) {
        double effectiveRadius = Math.min(2.0, 32.0);
        int radiusInt = (int) Math.ceil(effectiveRadius);
        for (int x = -radiusInt; x <= radiusInt; x++) {
            for (int y = -radiusInt; y <= radiusInt; y++) {
                for (int z = -radiusInt; z <= radiusInt; z++) {
                    double distance = Math.sqrt(x * x + y * y + z * z);
                    if (distance <= effectiveRadius) {
                        previewBlocks.add(center.add(x, y, z));
                    }
                }
            }
        }
    }

    public enum FacePreviewTint {
        BASE(new float[]{0.2f, 0.6f, 1.0f, 0.35f}, new float[]{0.2f, 0.6f, 1.0f, 0.7f}),
        ADDITIVE(new float[]{1.0f, 0.6f, 0.2f, 0.35f}, new float[]{1.0f, 0.6f, 0.2f, 0.7f}),
        BOUNDARY(new float[]{1.0f, 0.2f, 0.2f, 0.35f}, new float[]{1.0f, 0.2f, 0.2f, 0.7f});

        private final float[] fillColor;
        private final float[] outlineColor;

        FacePreviewTint(float[] fillColor, float[] outlineColor) {
            this.fillColor = fillColor;
            this.outlineColor = outlineColor;
        }
    }
}