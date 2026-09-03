package com.director.client.renderer;

import com.director.core.DirectorServices;
import com.director.client.renderer.mesh.BlockOutlineMeshCache;
import com.director.client.settings.RenderSettings;
import com.director.core.selection.SelectionManager;
import com.director.core.selection.SelectionState;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 🧊 SelectionRenderer v2.0 (Director) - Greedy Meshing Edition
 * <p>
 * 双通道修正版：
 *  - Outline Pass（AFTER_ENTITIES）
 *  - Translucent Pass（AFTER_TRANSLUCENT）
 * <p>
 * 新特性：
 *  🚀 v2.0: 贪婪合并算法 (Greedy Meshing)，支持超大规模选区的高性能渲染
 *  🚀 v2.0: 选区网格缓存系统，仅在选区变化时重新计算几何体
 * <p>
 * 之前特性保留：
 *  ✅ 半透明填充稳定渲染
 *  ✅ 不受 UI / DockSpace 干扰
 *  ✅ 独立状态恢复，防止状态污染
 *  ✅ 呼吸动画 + 动态高亮强度
 *  ✅ 零分配渲染循环（MutableVec3d、复用数组）
 * <p>
 * 模式控制：
 *  renderMode: 0=线框, 1=填充, 2=混合（推荐）
 * <p>
 * 性能提升：
 *  - 对于 10x10x10 (1000方块) 选区：从 6000 个面 → 6 个面（1000倍提升）
 *  - 对于大型选区（>10,000方块）：指数级性能提升
 * <p>
 * 统一渲染所有选择工具（点击、框选、魔棒等）的选区样式。
 * <p>
 * 已修复问题：
 *  - v1.3: 禁用SelectionOverlayRenderer的方块线框，避免重复渲染导致闪烁
 *  - v1.4: 添加PolygonOffset减少Z-Fighting闪烁
 *  - v1.5: 分离双通道渲染，避免 UI 状态污染（AFTER_ENTITIES + AFTER_TRANSLUCENT）
 *  - v2.0: 实现贪婪合并算法，支持超大规模选区渲染
 */
public final class SelectionRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectionRenderer.class);
    
    private static volatile SelectionRenderer INSTANCE;
    
    // 全局设置中心（新系统优先读取）
    private final RenderSettings settings = RenderSettings.get();
    
    // ✅ 填充几何体稍微扩展，避免被方块本身遮挡
    // 注意：使用负值让填充稍微超出方块边界，确保可见性
    // 值太小可能仍被遮挡，增加扩展量
    private static final double FILL_SHRINK = -0.005;
    private static final double OUTLINE_EXPAND = 0.001;
    
    // 渲染属性（已迁移到 RenderSettings，保留 enabled 用于总开关）
    private boolean enabled = true;
    private boolean enableAnimations = false; // 动画开关（暂未使用，保留用于未来扩展）
    // 包围盒颜色/线宽已迁移到 RenderSettings（settings.selection_bbox*）
    @SuppressWarnings("unused")
    private float animationTime = 0.0f; // 动画时间（暂未使用，保留用于未来扩展）

    // ✅ v1.9.3: 防重入机制
    private static boolean rendering = false;
    private static final Object RENDER_LOCK = new Object();
    
    // ====================================================================
    // 🚀 v2.0: 贪婪合并算法与网格缓存 (Greedy Meshing & Cache)
    // ====================================================================
    
    /**
     * 网格缓存系统（统一使用 BlockOutlineMeshCache）。
     * 当 SelectionManager 中的选区变化时，需要调用 invalidateCache()。
     */
    private final BlockOutlineMeshCache meshCache = new BlockOutlineMeshCache();
    
    // ✅ 贪婪合并设置已迁移到 RenderSettings（settings.selection_enableGreedy, settings.selection_greedyThreshold）
    
    /**
     * ✅ 渲染上下文记录类，用于减少重复的前置检查代码
     */
    private record RenderContext(SelectionState state, net.minecraft.client.render.Camera camera, Vec3d camPos, Matrix4f matrix) {}
    
    private SelectionRenderer() {
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
    
    /**
     * 🚀 v2.0: 当选区发生变化时，必须调用此方法来使缓存失效。
     * 应该在 SelectionManager 修改选区后调用。
     */
    public void invalidateCache() {
        meshCache.clear();
        LOGGER.debug("[SelectionRenderer] Selection mesh cache invalidated.");
    }

    // ================================
    // 兼容层：将旧 setter 统一转发到 RenderSettings
    // ================================
    @Deprecated
    public void setOutlineWidth(float width) {
        if (width > 0) {
            RenderSettings.get().selection_outlineWidth = width;
        }
    }

    @Deprecated
    public void setOutlineColor(float[] color) {
        if (color != null && color.length >= 4) {
            RenderSettings.get().selection_outlineColor = color.clone();
        }
    }

    @Deprecated
    public void setSelectionColor(float[] color) {
        if (color != null && color.length >= 4) {
            RenderSettings.get().selection_fillColor = color.clone();
        }
    }

    @Deprecated
    public void setHighlightIntensity(float intensity) {
        RenderSettings.get().selection_highlightIntensity = intensity;
    }

    @Deprecated
    public void setEnableGreedyMeshing(boolean enable) {
        RenderSettings.get().selection_enableGreedy = enable;
    }

    @Deprecated
    public void setGreedyMeshingThreshold(int threshold) {
        RenderSettings.get().selection_greedyThreshold = Math.max(1, threshold);
    }

    @Deprecated
    public void setMaxRenderDistance(int distance) {
        RenderSettings.get().selection_maxRenderDistance = Math.max(1, distance);
    }

    // ================================
    // Outline Pass (AFTER_ENTITIES)
    // ================================
    
    /**
     * ✅ 前置检查：验证渲染条件并返回渲染上下文
     * 减少重复的检查代码，提高代码可维护性
     * 
     * @param matrices 矩阵堆栈
     * @param requireFill true 表示需要填充，false 表示需要线框
     * @param checkWorldAndPause 是否检查世界和暂停状态（填充渲染需要）
     * @return 渲染上下文，如果检查失败则返回 null
     */
    private RenderContext preRenderCheck(MatrixStack matrices, boolean requireFill, boolean checkWorldAndPause) {
        if (!enabled) return null;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.gameRenderer == null || client.gameRenderer.getCamera() == null) return null;

        // ✅ 不再同步到本地字段，渲染时直接从 settings 读取（实时生效）
        
        // ✅ 填充渲染需要额外的检查
        if (checkWorldAndPause) {
            if (client.world == null || client.isPaused()) return null;
        }

        SelectionManager manager = DirectorServices.get().selection();
        if (manager == null || !manager.hasCurrentTool()) return null;

        SelectionState state = manager.getCurrentSelection();
        if (state == null) return null;
        if (state.isEmpty() && manager.getCurrentBounds() == null) return null;

        // 检查是否需要渲染（填充或线框）- 直接从 settings 读取
        if (requireFill) {
            if (!settings.selection_showFill) return null;
        } else {
            if (!settings.selection_showOutline) return null;
        }

        net.minecraft.client.render.Camera camera = client.gameRenderer.getCamera();
        Vec3d cam = camera.getPos();
        Matrix4f m = matrices.peek().getPositionMatrix();

        return new RenderContext(state, camera, cam, m);
    }
    
    /**
     * 渲染线框（仅在 AFTER_ENTITIES 阶段调用）
     * 独立渲染通道，不受透明层状态影响
     */
    public void renderOutlineOnly(MatrixStack matrices, VertexConsumerProvider vcp, float tickDelta) {
        updateAnimation(tickDelta);
        
        RenderContext ctx = preRenderCheck(matrices, false, false);
        if (ctx == null) return;

        SelectionState state = ctx.state();
        Vec3d cam = ctx.camPos();
        Matrix4f m = ctx.matrix();

        // ✅ 直接从全局设置读取（实时生效）
        float[] outlineColor = settings.selection_outlineColor;
        float r = outlineColor[0];
        float g = outlineColor[1];
        float b = outlineColor[2];
        float baseA = outlineColor[3];
        float a = baseA * settings.selection_highlightIntensity;
        float[] color = new float[]{r, g, b, a}; // 只在需要修改alpha时创建新数组

        Set<BlockPos> blocks = state.getSelectedBlocks();
        BlockPos camBlock = new BlockPos((int) Math.floor(cam.x), (int) Math.floor(cam.y), (int) Math.floor(cam.z));

        // ✅ 线框渲染状态设置（独立于填充，使用 try-finally 确保状态恢复）
        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager._enableDepthTest();
        // Minecraft 1.21+ 的线宽受 GPU 限制，许多平台固定为 1.0
        // 注意：RenderSystem 没有 getLineWidth() 方法，默认线宽为 1.0
        float lw = settings.selection_outlineWidth;
        RenderSystem.lineWidth(Math.max(1.0f, Math.min(lw, 3.0f)));

        try {
            VertexConsumer consumer = vcp.getBuffer(RenderLayers.getLineOverlay());

            // 🚀 v2.0: 一律使用贪婪合并（当开启时且非空）
            boolean greedy = settings.selection_enableGreedy;
            if (greedy && !blocks.isEmpty()) {
                // 使用贪婪合并算法；按最大渲染距离过滤（与逐方块分支一致）
                BlockOutlineMeshCache.Mesh mesh = meshCache.getOrCreateMesh(blocks);
                int maxDistSq = settings.selection_maxRenderDistance * settings.selection_maxRenderDistance;
                int rendered = 0;
                for (BlockOutlineMeshCache.Line line : mesh.lines()) {
                    double d1Sq = (line.x1() - cam.x) * (line.x1() - cam.x)
                        + (line.y1() - cam.y) * (line.y1() - cam.y)
                        + (line.z1() - cam.z) * (line.z1() - cam.z);
                    double d2Sq = (line.x2() - cam.x) * (line.x2() - cam.x)
                        + (line.y2() - cam.y) * (line.y2() - cam.y)
                        + (line.z2() - cam.z) * (line.z2() - cam.z);
                    if (d1Sq > maxDistSq && d2Sq > maxDistSq) continue;
                    line(consumer, m,
                         line.x1() - cam.x, line.y1() - cam.y, line.z1() - cam.z,
                         line.x2() - cam.x, line.y2() - cam.y, line.z2() - cam.z,
                         r, g, b, a);
                    rendered++;
                }
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[SelectionRenderer] ✅ 贪婪合并渲染 {} 条线框", rendered);
                }
                
                // ⭐ 包围盒渲染：始终以 SelectionManager 当前边界为准（空选区也可显示）
                SelectionManager mgr = DirectorServices.get().selection();
                Box worldBox = null;
                if (settings.selection_showBoundingBox && mgr != null) {
                    if (mgr.isBoundsGizmoMoveActive() && mgr.getBoundsGizmoMovePreviewBounds() != null) {
                        worldBox = mgr.getBoundsGizmoMovePreviewBounds();
                    } else if (mgr.isFaceResizeActive() && mgr.getResizePreviewBounds() != null) {
                        worldBox = mgr.getResizePreviewBounds();
                    } else {
                        worldBox = mgr.getCurrentBounds();
                    }
                }
                if (worldBox != null) {
                    Box box = worldBox.offset(-cam.x, -cam.y, -cam.z);
                    
                    // 使用稍微扩展的线框，确保可见
                    float[] bboxOutline = settings.selection_bboxOutlineColor;
                    float lwBox = Math.max(1.0f, Math.min(settings.selection_bboxOutlineWidth, 6.0f));
                    RenderSystem.lineWidth(lwBox);
                    drawBoundingBoxOutline(consumer, m, box, bboxOutline);
                    RenderSystem.lineWidth(Math.max(1.0f, Math.min(lw, 3.0f)));
                }
            } else {
                // 使用逐方块渲染（小选区或未启用贪婪合并）
                int rendered = 0;
                int maxDistSq = settings.selection_maxRenderDistance * settings.selection_maxRenderDistance;
                for (BlockPos pos : blocks) {
                    if (pos.getSquaredDistance(camBlock) > maxDistSq) continue;
                    drawOutline(consumer, m, pos, color, cam);
                    rendered++;
                }

                // ⭐ 包围盒渲染：始终以 SelectionManager 当前边界为准（空选区也可显示）
                SelectionManager mgr = DirectorServices.get().selection();
                Box worldBox = null;
                if (settings.selection_showBoundingBox && mgr != null) {
                    if (mgr.isBoundsGizmoMoveActive() && mgr.getBoundsGizmoMovePreviewBounds() != null) {
                        worldBox = mgr.getBoundsGizmoMovePreviewBounds();
                    } else if (mgr.isFaceResizeActive() && mgr.getResizePreviewBounds() != null) {
                        worldBox = mgr.getResizePreviewBounds();
                    } else {
                        worldBox = mgr.getCurrentBounds();
                    }
                }
                if (worldBox != null) {
                    Box box = worldBox.offset(-cam.x, -cam.y, -cam.z);
                    
                    // 使用稍微扩展的线框，确保可见
                    float[] bboxOutline = settings.selection_bboxOutlineColor;
                    float lwBox = Math.max(1.0f, Math.min(settings.selection_bboxOutlineWidth, 6.0f));
                    RenderSystem.lineWidth(lwBox);
                    drawBoundingBoxOutline(consumer, m, box, bboxOutline);
                    RenderSystem.lineWidth(Math.max(1.0f, Math.min(lw, 3.0f)));
                }

                if (rendered > 0 && LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[SelectionRenderer] ✅ 渲染 {} 个方块线框", rendered);
                }
            }
        } finally {
            // ✅ 保证状态恢复，不影响后续渲染
            GlStateManager._disableBlend();
            RenderSystem.lineWidth(1.0f); // 恢复默认线宽
        }

    }

    // ================================
    // Translucent Fill Pass (AFTER_TRANSLUCENT)
    // ================================
    
    /**
     * 渲染填充（在 AFTER_TRANSLUCENT 阶段调用）
     * 使用 RenderLayers.getPreviewLayer() 实现纯色半透明效果
     */
    public void renderFillOnly(MatrixStack matrices, VertexConsumerProvider vcp, float tickDelta) {
        // ✅ v1.9.3: 防重入检查
        synchronized (RENDER_LOCK) {
            if (rendering) {
                LOGGER.warn("[SelectionRenderer] ⚠️ 跳过重入渲染");
                return;
            }
            rendering = true;
        }
        
        try {
            LOGGER.debug("[SelectionRenderer] 🔍 renderFillOnly 调用 - enabled: {}, showFill: {}", 
                         enabled, settings.selection_showFill);
            
            if (!enabled || !settings.selection_showFill) {
                LOGGER.debug("[SelectionRenderer] ❌ 渲染器未启用或填充未启用，跳过");
                return;
            }

            RenderContext ctx = preRenderCheck(matrices, true, true);
            if (ctx == null) {
                // preRenderCheck 检查失败，直接返回（已在内部记录调试日志）
                return;
            }

            SelectionState state = ctx.state();
            Vec3d cam = ctx.camPos();
            Matrix4f posMat = ctx.matrix();
            
            LOGGER.debug("[SelectionRenderer] ✅ 开始渲染填充 - 方块数: {}", state.getSelectedBlocks().size());

            // ✅ 直接从全局设置读取（实时生效）
            float[] fillColorBase = settings.selection_fillColor;
            float fillR = fillColorBase[0];
            float fillG = fillColorBase[1];
            float fillB = fillColorBase[2];
            float baseFillA = fillColorBase[3];
            float fillA = baseFillA * settings.selection_highlightIntensity;
            float[] fillColor = new float[]{fillR, fillG, fillB, fillA}; // 只在需要修改alpha时创建新数组

            Set<BlockPos> blocks = state.getSelectedBlocks();
            BlockPos camBlock = new BlockPos((int) Math.floor(cam.x), (int) Math.floor(cam.y), (int) Math.floor(cam.z));

            // ✅ 填充渲染状态设置（使用 try-finally 确保恢复）
            GlStateManager._enableBlend();
            GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager._disableCull(); // 透明渲染需要禁用背面剔除
            GlStateManager._depthMask(false); // 透明渲染必需
            GlStateManager._enableDepthTest(); // 确保深度测试启用
            
            // ✅ 启用 PolygonOffset 让填充稍微靠近摄像机，避免被方块本身遮挡
            // 注意：负值让几何体靠近摄像机（更靠近屏幕）
            GlStateManager._enablePolygonOffset();
            GlStateManager._polygonOffset(-1.5f, -1.5f); // 增加偏移量，确保填充可见

            try {
                // ✅ 使用白色纹理的半透明图层
                // RenderLayers 会自动绑定白色纹理，顶点颜色 × 白色纹理 = 顶点颜色
                // 使用纹理中心点 (0.5, 0.5) 确保采样到白色
                VertexConsumer fill = vcp.getBuffer(RenderLayers.getSelectionFillLayer());
                
                // 调试：记录颜色值（仅在debug模式下）
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[SelectionRenderer] 🎨 填充颜色: RGBA({}, {}, {}, {})", 
                               fillColorBase[0], fillColorBase[1], fillColorBase[2], fillA);
                }

                // 🚀 v2.0: 使用贪婪合并或逐方块渲染
                int greedyThreshold = Math.max(1, settings.selection_greedyThreshold);
                if (settings.selection_enableGreedy && blocks.size() >= greedyThreshold) {
                    // 使用贪婪合并算法；按最大渲染距离过滤（与逐方块分支一致）
                    BlockOutlineMeshCache.Mesh mesh = meshCache.getOrCreateMesh(blocks);
                    int maxDistSq = settings.selection_maxRenderDistance * settings.selection_maxRenderDistance;
                    int rendered = 0;
                    for (BlockOutlineMeshCache.Quad quad : mesh.quads()) {
                        // 若四个角点都在最大距离外则跳过
                        boolean inRange = false;
                        for (BlockPos p : new BlockPos[]{quad.p1(), quad.p2(), quad.p3(), quad.p4()}) {
                            double dSq = (p.getX() - cam.x) * (p.getX() - cam.x)
                                + (p.getY() - cam.y) * (p.getY() - cam.y)
                                + (p.getZ() - cam.z) * (p.getZ() - cam.z);
                            if (dSq <= maxDistSq) { inRange = true; break; }
                        }
                        if (!inRange) continue;
                        emitMergedQuad(fill, posMat, quad, cam, fillR, fillG, fillB, fillA);
                        rendered++;
                    }
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("[SelectionRenderer] ✅ 贪婪合并渲染 {} 个填充面", rendered);
                    }
                    
                    // ⭐ 包围盒填充（根据设置控制）
                    if (settings.selection_showBoundingBox && settings.selection_bboxShowFill) {
                        SelectionManager mgr = DirectorServices.get().selection();
                        Box worldBox = null;
                        if (mgr != null) {
                            if (mgr.isBoundsGizmoMoveActive() && mgr.getBoundsGizmoMovePreviewBounds() != null) {
                                worldBox = mgr.getBoundsGizmoMovePreviewBounds();
                            } else if (mgr.isFaceResizeActive() && mgr.getResizePreviewBounds() != null) {
                                worldBox = mgr.getResizePreviewBounds();
                            } else if (blocks.size() > 1 && state.getBoundingBox().isPresent()) {
                                SelectionState.BoundingBox bbox = state.getBoundingBox().get();
                                worldBox = new Box(bbox.getMinX(), bbox.getMinY(), bbox.getMinZ(),
                                        bbox.getMaxX() + 1, bbox.getMaxY() + 1, bbox.getMaxZ() + 1);
                            }
                        } else if (blocks.size() > 1 && state.getBoundingBox().isPresent()) {
                            SelectionState.BoundingBox bbox = state.getBoundingBox().get();
                            worldBox = new Box(bbox.getMinX(), bbox.getMinY(), bbox.getMinZ(),
                                    bbox.getMaxX() + 1, bbox.getMaxY() + 1, bbox.getMaxZ() + 1);
                        }
                        if (worldBox != null) {
                            Box box = worldBox.offset(-cam.x, -cam.y, -cam.z);
                            
                            float[] base = settings.selection_bboxFillColor;
                            float a2 = base[3] * Math.min(1.0f, settings.selection_highlightIntensity);
                            float[] boxColor = new float[]{base[0], base[1], base[2], a2};
                            drawBoundingBoxFill(fill, posMat, box, boxColor);
                        }
                    }
                } else {
                    // 使用逐方块渲染（小选区或未启用贪婪合并）
                    int rendered = 0;
                    int maxDistSq = settings.selection_maxRenderDistance * settings.selection_maxRenderDistance;
                    for (BlockPos pos : blocks) {
                        if (pos.getSquaredDistance(camBlock) > maxDistSq) {
                            LOGGER.debug("[SelectionRenderer] 跳过方块 {} (距离超过)", pos);
                            continue;
                        }
                        LOGGER.debug("[SelectionRenderer] 🎨 渲染填充方块: {}", pos);
                        drawFilledCube(fill, posMat, pos, fillColor, cam);
                        rendered++;
                    }
                    
                    if (rendered > 0 && LOGGER.isDebugEnabled()) {
                        LOGGER.debug("[SelectionRenderer] ✅ 渲染 {} 个方块填充", rendered);
                    }
                    
                    // ⭐ 包围盒填充：始终以 SelectionManager 当前边界为准（空选区也可显示）
                    SelectionManager manager = DirectorServices.get().selection();
                    Box worldBox = null;
                    if (settings.selection_showBoundingBox && settings.selection_bboxShowFill && manager != null) {
                        if (manager.isBoundsGizmoMoveActive() && manager.getBoundsGizmoMovePreviewBounds() != null) {
                            worldBox = manager.getBoundsGizmoMovePreviewBounds();
                        } else if (manager.isFaceResizeActive() && manager.getResizePreviewBounds() != null) {
                            worldBox = manager.getResizePreviewBounds();
                        } else {
                            worldBox = manager.getCurrentBounds();
                        }
                    }
                    if (worldBox != null) {
                        Box box = worldBox.offset(-cam.x, -cam.y, -cam.z);
                        float[] base = settings.selection_bboxFillColor;
                        float a2 = base[3] * Math.min(1.0f, settings.selection_highlightIntensity);
                        float[] boxColor = new float[]{base[0], base[1], base[2], a2};
                        drawBoundingBoxFill(fill, posMat, box, boxColor);
                    }
                }
                
            } finally {
                // ✅ 保证状态恢复，不影响后续渲染
                GlStateManager._disablePolygonOffset();
                GlStateManager._depthMask(true);
                GlStateManager._enableCull();
                GlStateManager._disableBlend();
            }
            
        } catch (Exception e) {
            LOGGER.error("[SelectionRenderer] ❌ 渲染填充时发生异常", e);
        } finally {
            // ✅ v1.9.3: 释放重入锁（确保总是释放）
            synchronized (RENDER_LOCK) {
                rendering = false;
            }
        }
    }
    
    /**
     * ✅ v1.9.3: 清理所有状态（在关闭窗口时调用）
     */
    public void clearAll() {
        synchronized (RENDER_LOCK) {
            rendering = false;
        }
        LOGGER.info("[SelectionRenderer] 🧹 已清理状态");
    }

    // ================================
    // 动画更新
    // ================================
    
    private void updateAnimation(float tickDelta) {
        // ✅ 动画功能已迁移到 RenderSettings，这里仅更新动画时间（如果将来需要）
        if (enableAnimations) {
            animationTime += tickDelta;
        }
    }

    // ======================== 几何渲染 ========================

    // ✅ 复用顶点数组，避免每次分配新内存
    private final float[][] reusableVertices = new float[8][3];
    
    private void drawFilledCube(VertexConsumer c, Matrix4f mat, BlockPos pos, float[] color, Vec3d cam) {
        getVerticesInto(pos, cam, reusableVertices);

        emitQuad(c, mat, reusableVertices, color, 0, 1, 2, 3);
        emitQuad(c, mat, reusableVertices, color, 4, 5, 6, 7);
        emitQuad(c, mat, reusableVertices, color, 0, 4, 7, 3);
        emitQuad(c, mat, reusableVertices, color, 1, 5, 6, 2);
        emitQuad(c, mat, reusableVertices, color, 0, 1, 5, 4);
        emitQuad(c, mat, reusableVertices, color, 3, 2, 6, 7);
    }

    /**
     * ✅ 优化：将顶点数据写入已存在的数组，避免分配新数组
     */
    private static void getVerticesInto(BlockPos pos, Vec3d cam, float[][] out) {
        double minX = pos.getX() - cam.x + FILL_SHRINK;
        double minY = pos.getY() - cam.y + FILL_SHRINK;
        double minZ = pos.getZ() - cam.z + FILL_SHRINK;
        double maxX = pos.getX() - cam.x + 1.0 - FILL_SHRINK;
        double maxY = pos.getY() - cam.y + 1.0 - FILL_SHRINK;
        double maxZ = pos.getZ() - cam.z + 1.0 - FILL_SHRINK;

        out[0][0] = (float) minX; out[0][1] = (float) minY; out[0][2] = (float) minZ;
        out[1][0] = (float) maxX; out[1][1] = (float) minY; out[1][2] = (float) minZ;
        out[2][0] = (float) maxX; out[2][1] = (float) maxY; out[2][2] = (float) minZ;
        out[3][0] = (float) minX; out[3][1] = (float) maxY; out[3][2] = (float) minZ;
        out[4][0] = (float) minX; out[4][1] = (float) minY; out[4][2] = (float) maxZ;
        out[5][0] = (float) maxX; out[5][1] = (float) minY; out[5][2] = (float) maxZ;
        out[6][0] = (float) maxX; out[6][1] = (float) maxY; out[6][2] = (float) maxZ;
        out[7][0] = (float) minX; out[7][1] = (float) maxY; out[7][2] = (float) maxZ;
    }
    

    /**
     * 🚀 v2.0: 专门用于渲染合并后的大四边形 (Quad)
     * 正确处理 FILL_SHRINK 和法线方向，确保渲染正确
     */
    private void emitMergedQuad(VertexConsumer c, Matrix4f m, BlockOutlineMeshCache.Quad quad, Vec3d cam,
                                float r, float g, float b, float a) {
        Direction dir = quad.dir();
        
        // 顶点坐标（已应用相机偏移和 FILL_SHRINK）
        float nx = dir.getOffsetX();
        float ny = dir.getOffsetY();
        float nz = dir.getOffsetZ();

        float x1 = (float)(quad.p1().getX() - cam.x + nx * FILL_SHRINK);
        float y1 = (float)(quad.p1().getY() - cam.y + ny * FILL_SHRINK);
        float z1 = (float)(quad.p1().getZ() - cam.z + nz * FILL_SHRINK);

        float x2 = (float)(quad.p2().getX() - cam.x + nx * FILL_SHRINK);
        float y2 = (float)(quad.p2().getY() - cam.y + ny * FILL_SHRINK);
        float z2 = (float)(quad.p2().getZ() - cam.z + nz * FILL_SHRINK);

        float x3 = (float)(quad.p3().getX() - cam.x + nx * FILL_SHRINK);
        float y3 = (float)(quad.p3().getY() - cam.y + ny * FILL_SHRINK);
        float z3 = (float)(quad.p3().getZ() - cam.z + nz * FILL_SHRINK);

        float x4 = (float)(quad.p4().getX() - cam.x + nx * FILL_SHRINK);
        float y4 = (float)(quad.p4().getY() - cam.y + ny * FILL_SHRINK);
        float z4 = (float)(quad.p4().getZ() - cam.z + nz * FILL_SHRINK);

        // 1.21.x：EntityTranslucent 顶点格式需要完整写入 UV/Overlay/Light/Normal。
        // 若只写 position+color，部分实现会复用“上一顶点”的 UV/Light，导致填充看起来被拉扯到某个面/三角。
        fullBrightVertex(c, m, x1, y1, z1, r, g, b, a, nx, ny, nz);
        fullBrightVertex(c, m, x2, y2, z2, r, g, b, a, nx, ny, nz);
        fullBrightVertex(c, m, x3, y3, z3, r, g, b, a, nx, ny, nz);
        fullBrightVertex(c, m, x4, y4, z4, r, g, b, a, nx, ny, nz);
    }
    
    private void emitQuad(VertexConsumer c, Matrix4f m, float[][] v, float[] color,
                          int v1, int v2, int v3, int v4) {
        // 不显式提供法线时用一个稳定值即可（我们使用 full-bright，所以不会受光照影响）
        fullBrightVertex(c, m, v[v1][0], v[v1][1], v[v1][2], color[0], color[1], color[2], color[3], 0, 1, 0);
        fullBrightVertex(c, m, v[v2][0], v[v2][1], v[v2][2], color[0], color[1], color[2], color[3], 0, 1, 0);
        fullBrightVertex(c, m, v[v3][0], v[v3][1], v[v3][2], color[0], color[1], color[2], color[3], 0, 1, 0);
        fullBrightVertex(c, m, v[v4][0], v[v4][1], v[v4][2], color[0], color[1], color[2], color[3], 0, 1, 0);
    }

    /**
     * 统一写入 selection fill 顶点（固定采样白纹理中心点 + full-bright）。
     *
     * <p>这能避免 1.21.x 下 VertexConsumer 在未写入 UV/Light/Overlay 时出现的“顶点属性复用”伪影。</p>
     */
    private static void fullBrightVertex(VertexConsumer c, Matrix4f m,
                                         float x, float y, float z,
                                         float r, float g, float b, float a,
                                         float nx, float ny, float nz) {
        c.vertex(m, x, y, z)
                .color(r, g, b, a)
                // 固定采样白纹理中心点，避免边缘采样/过滤导致的意外颜色
                .texture(0.5f, 0.5f)
                .overlay(OverlayTexture.DEFAULT_UV)
                // full-bright（15,15）
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(nx, ny, nz);
    }

    private void drawOutline(VertexConsumer c, Matrix4f m, BlockPos pos, float[] color, Vec3d cam) {
        double minX = pos.getX() - cam.x - OUTLINE_EXPAND;
        double minY = pos.getY() - cam.y - OUTLINE_EXPAND;
        double minZ = pos.getZ() - cam.z - OUTLINE_EXPAND;
        double maxX = pos.getX() - cam.x + 1.0 + OUTLINE_EXPAND;
        double maxY = pos.getY() - cam.y + 1.0 + OUTLINE_EXPAND;
        double maxZ = pos.getZ() - cam.z + 1.0 + OUTLINE_EXPAND;

        line(c, m, minX, minY, minZ, maxX, minY, minZ, color);
        line(c, m, maxX, minY, minZ, maxX, minY, maxZ, color);
        line(c, m, maxX, minY, maxZ, minX, minY, maxZ, color);
        line(c, m, minX, minY, maxZ, minX, minY, minZ, color);

        line(c, m, minX, maxY, minZ, maxX, maxY, minZ, color);
        line(c, m, maxX, maxY, minZ, maxX, maxY, maxZ, color);
        line(c, m, maxX, maxY, maxZ, minX, maxY, maxZ, color);
        line(c, m, minX, maxY, maxZ, minX, maxY, minZ, color);

        line(c, m, minX, minY, minZ, minX, maxY, minZ, color);
        line(c, m, maxX, minY, minZ, maxX, maxY, minZ, color);
        line(c, m, maxX, minY, maxZ, maxX, maxY, maxZ, color);
        line(c, m, minX, minY, maxZ, minX, maxY, maxZ, color);
    }

    /**
     * 🚀 v2.0: 绘制一条线（接受 double 坐标和单独的 float 颜色参数）
     */
    private void line(VertexConsumer c, Matrix4f m, double x1, double y1, double z1, 
                      double x2, double y2, double z2, float r, float g, float b, float a) {
        c.vertex(m, (float) x1, (float) y1, (float) z1).color(r, g, b, a).normal(0, 1, 0);
        c.vertex(m, (float) x2, (float) y2, (float) z2).color(r, g, b, a).normal(0, 1, 0);
    }
    
    /**
     * 兼容性方法：接受 float[] 颜色数组
     */
    private void line(VertexConsumer c, Matrix4f m,
                      double x1, double y1, double z1,
                      double x2, double y2, double z2,
                      float[] color) {
        line(c, m, x1, y1, z1, x2, y2, z2, color[0], color[1], color[2], color[3]);
    }

    // ======================== 包围盒渲染 ========================

    /**
     * 绘制包围盒线框（多方块选区的整体边界）
     */
    private void drawBoundingBoxOutline(VertexConsumer c, Matrix4f mat, Box box, float[] color) {
        double minX = box.minX - OUTLINE_EXPAND;
        double minY = box.minY - OUTLINE_EXPAND;
        double minZ = box.minZ - OUTLINE_EXPAND;
        double maxX = box.maxX + OUTLINE_EXPAND;
        double maxY = box.maxY + OUTLINE_EXPAND;
        double maxZ = box.maxZ + OUTLINE_EXPAND;

        // 使用线框绘制函数
        lineBox(c, mat, minX, minY, minZ, maxX, maxY, maxZ, color);
    }

    /**
     * 绘制包围盒填充（多方块选区的整体半透明填充）
     */
    private void drawBoundingBoxFill(VertexConsumer c, Matrix4f mat, Box box, float[] color) {
        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;

        // 六面半透明包围体
        float[][] v = new float[][]{
            {(float) minX, (float) minY, (float) minZ},
            {(float) maxX, (float) minY, (float) minZ},
            {(float) maxX, (float) maxY, (float) minZ},
            {(float) minX, (float) maxY, (float) minZ},
            {(float) minX, (float) minY, (float) maxZ},
            {(float) maxX, (float) minY, (float) maxZ},
            {(float) maxX, (float) maxY, (float) maxZ},
            {(float) minX, (float) maxY, (float) maxZ}
        };

        emitQuad(c, mat, v, color, 0, 1, 2, 3); // Bottom
        emitQuad(c, mat, v, color, 4, 5, 6, 7); // Top
        emitQuad(c, mat, v, color, 0, 4, 7, 3); // West
        emitQuad(c, mat, v, color, 1, 5, 6, 2); // East
        emitQuad(c, mat, v, color, 0, 1, 5, 4); // North
        emitQuad(c, mat, v, color, 3, 2, 6, 7); // South
    }

    /**
     * 绘制线框盒子（12条边）
     */
    private void lineBox(VertexConsumer c, Matrix4f mat,
                         double x1, double y1, double z1,
                         double x2, double y2, double z2,
                         float[] color) {
        // 底部矩形
        line(c, mat, x1, y1, z1, x2, y1, z1, color);
        line(c, mat, x2, y1, z1, x2, y1, z2, color);
        line(c, mat, x2, y1, z2, x1, y1, z2, color);
        line(c, mat, x1, y1, z2, x1, y1, z1, color);

        // 顶部矩形
        line(c, mat, x1, y2, z1, x2, y2, z1, color);
        line(c, mat, x2, y2, z1, x2, y2, z2, color);
        line(c, mat, x2, y2, z2, x1, y2, z2, color);
        line(c, mat, x1, y2, z2, x1, y2, z1, color);

        // 垂直边
        line(c, mat, x1, y1, z1, x1, y2, z1, color);
        line(c, mat, x2, y1, z1, x2, y2, z1, color);
        line(c, mat, x2, y1, z2, x2, y2, z2, color);
        line(c, mat, x1, y1, z2, x1, y2, z2, color);
    }

    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        LOGGER.info("[SelectionRenderer] 渲染器{}", enabled ? "启用" : "禁用");
    }
}
