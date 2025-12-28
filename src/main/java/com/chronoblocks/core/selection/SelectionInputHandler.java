package com.chronoblocks.core.selection;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 🎮 SelectionInputHandler v2.10 (角度偏移修正 - FOV × 2)
 * - 🔥 核心思路：鼠标位置 = 相机角度 + 偏移角度
 * - 🛠️ 关键修复：deltaPixel / screenSize × FOV × 2（屏幕边缘 = FOV/2）
 * - 🛠️ 类似原版：当鼠标在中心时，偏移为 0，等同于原版十字星
 * - 🛠️ 自动适配：自动处理 FOV 和宽高比
 * - 🛠️ 优化：降低防抖和拖拽阈值，提高点击响应性
 * - 🛠️ 增强：详细调试日志显示像素偏移和角度偏移
 */
public class SelectionInputHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectionInputHandler.class);

    private final MinecraftClient client;
    private final SelectionManager selectionManager;

    // 状态
    private boolean isDragging = false;
    private BlockPos dragStart = null;
    private long lastClickTime = 0;

    private static final long CLICK_DEBOUNCE_MS = 50;   // 降低防抖时间，提高响应性

    public SelectionInputHandler() {
        this.client = MinecraftClient.getInstance();
        this.selectionManager = SelectionManager.get();
    }

    /* -------------------- 统一的射线方法 -------------------- */

    /**
     * 🔥 最简单方法：将鼠标位置转换为角度偏移（类似 Minecraft 原版）
     * <p>
     * 原理：
     * 1. Minecraft 原版：固定屏幕中心 = camera.getPitch/Yaw
     * 2. 我们的方法：鼠标位置 = camera.getPitch/Yaw + 偏移角度
     * 3. 偏移角度 = 鼠标距离中心的像素 × FOV / 屏幕尺寸
     */
    private BlockHitResult raycastFromMouse() {
        ClientPlayerEntity player = client.player;
        World world = client.world;
        if (player == null || world == null) return null;

        // 0) 优先复用渲染阶段已计算的射线，保证与“掠过高亮”完全一致
        try {
            BlockHitResult current = com.chronoblocks.client.input.ChronoBlocksInputSystem.getCurrentRay();
            if (current != null && current.getType() == HitResult.Type.BLOCK) {
                return current;
            }
        } catch (Throwable ignored) {
            // 兼容初始化阶段
        }

        // 1) 读取鼠标逻辑坐标并换算为帧缓冲像素
        double[] mouseLogicalX = new double[1];
        double[] mouseLogicalY = new double[1];
        GLFW.glfwGetCursorPos(client.getWindow().getHandle(), mouseLogicalX, mouseLogicalY);

        int windowW = client.getWindow().getWidth();
        int windowH = client.getWindow().getHeight();
        int fbWidth = client.getWindow().getFramebufferWidth();
        int fbHeight = client.getWindow().getFramebufferHeight();
        if (windowW <= 0 || windowH <= 0 || fbWidth <= 0 || fbHeight <= 0) return null;

        double mouseXpx = mouseLogicalX[0] * fbWidth / (double) windowW;
        double mouseYpx = mouseLogicalY[0] * fbHeight / (double) windowH;

        // 2) NDC（注意Y翻转）
        float ndcX = (float)(mouseXpx / fbWidth * 2.0 - 1.0);
        float ndcY = (float)(1.0 - mouseYpx / fbHeight * 2.0);

        // 4. 获取相机参数
        Camera camera = client.gameRenderer.getCamera();
        Vec3d cameraPos = camera.getPos();
        // 3) 1.21.10+：使用 GameRenderer 的基础投影矩阵（替代 RenderSystem.getProjectionMatrix）
        GameRenderer gr = client.gameRenderer;
        float fov = client.options.getFov().getValue().floatValue();
        Matrix4f invProj = new Matrix4f(gr.getBasicProjectionMatrix(fov)).invert();
        Vector4f nearClip = new Vector4f(ndcX, ndcY, -1.0f, 1.0f).mul(invProj);
        Vector4f farClip  = new Vector4f(ndcX, ndcY,  1.0f, 1.0f).mul(invProj);
        if (nearClip.w != 0.0f) nearClip.div(nearClip.w);
        if (farClip.w  != 0.0f)  farClip.div(farClip.w);

        // 视空间方向
        float vx = farClip.x - nearClip.x;
        float vy = farClip.y - nearClip.y;
        float vz = farClip.z - nearClip.z;
        float lenInv = (float)(1.0 / Math.max(1e-8, Math.sqrt(vx*vx + vy*vy + vz*vz)));
        vx *= lenInv; vy *= lenInv; vz *= lenInv;

        // 相机旋转到世界方向（四元数旋转）
        var q = camera.getRotation();
        float qx = q.x, qy = q.y, qz = q.z, qw = q.w;
        float cx1 = qy * vz - qz * vy;
        float cy1 = qz * vx - qx * vz;
        float cz1 = qx * vy - qy * vx;
        float rx = vx + 2.0f * (qy * cz1 - qz * cy1 + qw * cx1);
        float ry = vy + 2.0f * (qz * cx1 - qx * cz1 + qw * cy1);
        float rz = vz + 2.0f * (qx * cy1 - qy * cx1 + qw * cz1);
        Vec3d rayDir = new Vec3d(rx, ry, rz).normalize();

        // 9. 执行射线追踪
        double maxDist = client.options.getViewDistance().getValue() * 16.0;
        Vec3d endPos = cameraPos.add(rayDir.multiply(maxDist));

        BlockHitResult hit = world.raycast(new RaycastContext(
                cameraPos,
                endPos,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                player
        ));

        return (hit != null && hit.getType() == HitResult.Type.BLOCK) ? hit : null;
    }

    /* -------------------- 点击 -------------------- */

    public void handleMouseClick(boolean isLeftClick, boolean isShiftDown) {
        if (!isLeftClick) return;
        if (selectionManager == null || !selectionManager.hasCurrentTool()) return;

        long now = System.currentTimeMillis();

        // 如果正在拖拽，不处理点击
        if (isDragging) {
            LOGGER.debug("[Click] 正在拖拽，跳过点击");
            return;
        }

        // 防抖检查
        if (now - lastClickTime < CLICK_DEBOUNCE_MS) {
            LOGGER.debug("[Click] 点击防抖 (间隔={}ms < {}ms)", now - lastClickTime, CLICK_DEBOUNCE_MS);
            return;
        }
        lastClickTime = now;

        BlockHitResult hit = raycastFromMouse();
        if (hit == null) {
            LOGGER.debug("[Click] 射线未命中方块");
            return;
        }

        BlockPos pos = hit.getBlockPos();
        LOGGER.info("[Click] ✅ 点击选中: {}", pos);
        selectionManager.handleClick(client.world, pos, isShiftDown);
    }
    
    /**
     * 处理右键点击事件（用于两点式建盒）
     */
    public void handleMouseRightClick() {
        if (selectionManager == null || !selectionManager.hasCurrentTool()) return;
        
        BlockHitResult hit = raycastFromMouse();
        if (hit == null) {
            LOGGER.debug("[RightClick] 射线未命中方块");
            return;
        }
        
        BlockPos pos = hit.getBlockPos();
        LOGGER.debug("[RightClick] ✅ 右键点击: {}", pos);
        selectionManager.handleRightClick(client.world, pos);
    }

    /* -------------------- 拖拽开始 / 更新 / 结束 -------------------- */

    public void handleMouseDragStart(boolean isLeftClick) {
        if (!isLeftClick) return;
        if (selectionManager == null || !selectionManager.hasCurrentTool()) return;
        
        // 检查当前工具是否是 BoxSelectTool，并且鼠标悬停在面中心小方块上
        // 如果是，则不设置 dragStart，让面中心小方块的拖拽逻辑处理
        if (selectionManager.getCurrentTool() instanceof com.chronoblocks.core.selection.tools.boxselecttool.BoxSelectTool boxTool) {
            // 检查是否悬停在面中心小方块上
            Integer hoveredFace = boxTool.getHoveredFaceIndex();
            if (hoveredFace != null) {
                LOGGER.debug("[Drag] 鼠标悬停在面中心小方块上，跳过框选拖拽开始");
                return; // 阻止框选拖拽开始，让面中心小方块的拖拽逻辑处理
            }
        }

        BlockHitResult hit = raycastFromMouse();
        if (hit == null) {
            LOGGER.debug("[Drag] 起点：射线未命中");
            return;
        }

        dragStart = hit.getBlockPos();
        // 注意：暂不设置 isDragging，等鼠标真正移动后再设置
        LOGGER.debug("[Drag] 起点记录: {}", dragStart);
    }

    public void handleMouseDragUpdate() {
        if (selectionManager == null || client.world == null) return;
        if (dragStart == null) return;
        
        // 检查当前工具是否是 BoxSelectTool，并且正在拖拽面中心小方块
        // 如果是，则不处理框选拖拽，让面中心小方块的拖拽逻辑处理
        if (selectionManager.getCurrentTool() instanceof com.chronoblocks.core.selection.tools.boxselecttool.BoxSelectTool boxTool) {
            if (boxTool.isDraggingFaceHandle()) {
                LOGGER.debug("[Drag] 正在拖拽面中心小方块，跳过框选拖拽");
                return; // 阻止框选拖拽
            }
        }

        BlockHitResult hit = raycastFromMouse();
        if (hit == null) return;

        BlockPos currentPos = hit.getBlockPos();

        // 只有当鼠标移动到不同方块时，才真正开始拖拽
        if (!isDragging) {
            if (!dragStart.equals(currentPos)) {
                isDragging = true;
                LOGGER.info("[Drag] ✅ 开始拖拽: {} -> {}", dragStart, currentPos);
                selectionManager.handleDragStart(client.world, dragStart);
            } else {
                // 还在起点，不算拖拽
                return;
            }
        }

        selectionManager.handleDragUpdate(client.world, currentPos);
    }

    public void handleMouseDragEnd(boolean isLeftClick) {
        if (!isLeftClick) return;

        // 如果从未真正开始拖拽，清理状态
        if (!isDragging) {
            dragStart = null;
            LOGGER.debug("[Drag] 未真正拖拽，清理状态");
            return;
        }

        if (selectionManager == null || client.world == null) {
            isDragging = false;
            dragStart = null;
            return;
        }

        BlockHitResult hit = raycastFromMouse();
        if (hit == null) {
            isDragging = false;
            dragStart = null;
            LOGGER.debug("[Drag] 终点：射线未命中");
            return;
        }

        BlockPos end = hit.getBlockPos();
        LOGGER.info("[Drag] ✅ 拖拽完成: {} -> {}", dragStart, end);

        selectionManager.handleDragEnd(client.world, end);

        isDragging = false;
        dragStart = null;
    }

    public void tick() {
        if (selectionManager != null && client.world != null) {
            selectionManager.tick(client.world);
            
            // 更新魔棒工具的悬停预览
            if (selectionManager.hasCurrentTool()) {
                SelectionTool currentTool = selectionManager.getCurrentTool();
                BlockHitResult hit = raycastFromMouse();
                if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                    currentTool.onHover(client.world, hit.getBlockPos());
                } else {
                    currentTool.onHover(client.world, null);
                }
            }
        }
    }

    /* -------------------- getters -------------------- */

    public boolean isDragging() {
        return isDragging;
    }

    public BlockPos getDragStart() {
        return dragStart;
    }

    public SelectionManager getSelectionManager() {
        return selectionManager;
    }
}