package com.director.client.gizmo;

import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import com.director.client.gizmo.handles.*;
import com.director.client.gizmo.util.GizmoVoxelizer;
import com.director.core.selection.SelectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 🧭 GizmoManager v2.3 - Gizmo 管理器
 * 
 * 管理所有 Gizmo 生命周期与输入事件
 * 
 * ✅ v2.3: 与 SelectionManager v3.0 无缝衔接
 * - 添加 setPivot() / setVisible() / applyHandleDrag() 逻辑
 * - 支持实时选区变换
 * - 拖拽逻辑与 SelectionManager.expandSelection() 联动
 * 
 * @author Director Team
 * @version 2.3
 */
public class GizmoManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(GizmoManager.class);
    private static GizmoManager INSTANCE;
    
    private GizmoContext context;
    private GizmoInteractionController controller;
    private GizmoRenderer renderer;
    
    private Consumer<GizmoContext> onApplyCallback;
    
    // ✅ v2.3: 拖拽状态管理
    private boolean dragging = false;
    private Axis activeAxis = null;
    private Vec3d lastDragWorldPos = null;
    
    // 渲染配置
    // 与 GizmoRenderer 的可见尺寸对齐（避免“箭头看得见但点不到/范围短”）
    private static final double AXIS_ARROW_LENGTH = 2.0;
    private static final double AXIS_ARROW_RADIUS = 0.15;
    private static final double PLANE_SIZE = 0.5;
    @SuppressWarnings("unused")
    private static final double CORNER_RADIUS = 0.12;
    private static final double CENTER_RADIUS = 0.15;
    
    private GizmoManager() {
        // 延迟初始化
    }
    
    public static GizmoManager get() {
        if (INSTANCE == null) {
            INSTANCE = new GizmoManager();
        }
        return INSTANCE;
    }
    
    /**
     * 激活 Gizmo
     * 
     * @param type Gizmo 类型
     * @param targetBox 目标 Box
     * @param pivot 轴心点（可为 null，使用 Box 中心）
     */
    public void activate(GizmoType type, Box targetBox, Vec3d pivot) {
        if (targetBox == null) {
            LOGGER.warn("[GizmoManager] 无法激活：targetBox 为 null");
            return;
        }
        
        // 计算轴心点
        if (pivot == null) {
            pivot = new Vec3d(
                (targetBox.minX + targetBox.maxX) / 2.0,
                (targetBox.minY + targetBox.maxY) / 2.0,
                (targetBox.minZ + targetBox.maxZ) / 2.0
            );
        }
        
        // 创建上下文
        context = new GizmoContext(type, targetBox, pivot);
        context.setActive(true);
        
        // 创建交互控制器
        controller = new GizmoInteractionController(context);
        
        // 创建渲染器
        renderer = new GizmoRenderer(context, controller);
        
        // 创建 Handle 列表
        List<GizmoHandle> handles = createHandles(context);
        controller.setHandles(handles);
        
        // 创建旋转环（如果支持）
        if (type == GizmoType.TRANSFORM || type == GizmoType.PIVOT) {
            List<com.director.client.gizmo.handles.RotationHandle> rotationHandles = 
                createRotationHandles(context);
            controller.setRotationHandles(rotationHandles);
        }
        
        // 创建 ResizeHandle（BOUNDS 类型使用）
        if (type == GizmoType.BOUNDS) {
            // ⚠️ 注意：框选工具（BoxSelectTool）的“缩放/改包围盒”由 FaceHandleManager 负责；
            // Gizmo 这里主要用于“整体移动”，因此不要注册不可见的 ResizeHandle，
            // 否则它们会在拾取优先级上抢占 AxisHandle，导致箭头 hover/拖拽失效。
            controller.setResizeHandles(java.util.List.of());
        }
        
        LOGGER.info("[GizmoManager] ✅ Gizmo 已激活：类型={}, Box={}, Pivot={}, Context={}, Renderer={}", 
                   type, targetBox, pivot, context != null, renderer != null);
        
        // 验证激活状态
        if (context != null && context.isActive()) {
            LOGGER.info("[GizmoManager] ✅ 验证：Context.isActive() = true");
        } else {
            LOGGER.error("[GizmoManager] ❌ 验证失败：Context.isActive() = false");
        }
        
        if (renderer != null) {
            LOGGER.info("[GizmoManager] ✅ 验证：Renderer 已创建");
        } else {
            LOGGER.error("[GizmoManager] ❌ 验证失败：Renderer 为 null");
        }
    }
    
    /**
     * 创建 Handle 列表
     */
    private List<GizmoHandle> createHandles(GizmoContext ctx) {
        List<GizmoHandle> handles = new ArrayList<>();
        
        Vec3d center = ctx.getPivot();
        
        if (ctx.getType() == GizmoType.BOUNDS) {
            // BOUNDS 类型：不再创建角点手柄
            // 用户只需要面中心小方块来调整选区大小，不需要角点手柄
        }
        
        // 创建轴手柄（所有类型都有）
        handles.add(new AxisHandle("axis_x", center, Axis.X, AXIS_ARROW_LENGTH, AXIS_ARROW_RADIUS));
        handles.add(new AxisHandle("axis_y", center, Axis.Y, AXIS_ARROW_LENGTH, AXIS_ARROW_RADIUS));
        handles.add(new AxisHandle("axis_z", center, Axis.Z, AXIS_ARROW_LENGTH, AXIS_ARROW_RADIUS));
        
        // 创建平面手柄（所有类型都有）
        handles.add(new PlaneHandle("plane_xy", center, Axis.X, Axis.Y, PLANE_SIZE));
        handles.add(new PlaneHandle("plane_xz", center, Axis.X, Axis.Z, PLANE_SIZE));
        handles.add(new PlaneHandle("plane_yz", center, Axis.Y, Axis.Z, PLANE_SIZE));
        
        // 创建中心手柄
        handles.add(new CenterHandle("center", center, CENTER_RADIUS));
        
        return handles;
    }
    
    /**
     * 创建旋转环列表
     */
    private List<com.director.client.gizmo.handles.RotationHandle> createRotationHandles(GizmoContext ctx) {
        List<com.director.client.gizmo.handles.RotationHandle> handles = new ArrayList<>();
        
        Vec3d center = ctx.getPivot();
        double radius = 2.0; // 旋转环半径
        
        // 创建三个旋转环（X, Y, Z）
        handles.add(new com.director.client.gizmo.handles.RotationHandle(
            center, radius, Axis.X));
        handles.add(new com.director.client.gizmo.handles.RotationHandle(
            center, radius, Axis.Y));
        handles.add(new com.director.client.gizmo.handles.RotationHandle(
            center, radius, Axis.Z));
        
        return handles;
    }
    
    /**
     * 创建 ResizeHandle 列表（用于 BOUNDS 类型）
     */
    private List<com.director.client.gizmo.handles.ResizeHandle> createResizeHandles(GizmoContext ctx) {
        List<com.director.client.gizmo.handles.ResizeHandle> handles = new ArrayList<>();
        
        Box box = ctx.getTargetBox();
        if (box == null) {
            return handles;
        }
        
        Vec3d min = new Vec3d(box.minX, box.minY, box.minZ);
        Vec3d max = new Vec3d(box.maxX, box.maxY, box.maxZ);
        Vec3d center = new Vec3d(
            (box.minX + box.maxX) / 2.0,
            (box.minY + box.maxY) / 2.0,
            (box.minZ + box.maxZ) / 2.0
        );
        
        double handleSize = 0.2; // 手柄大小
        
        // 创建六个面手柄（沿单一轴缩放）
        handles.add(new com.director.client.gizmo.handles.ResizeHandle(
            "face_x_min", com.director.client.gizmo.handles.ResizeHandle.Type.FACE,
            new Vec3d(min.x, center.y, center.z), handleSize, false, Axis.X));
        handles.add(new com.director.client.gizmo.handles.ResizeHandle(
            "face_x_max", com.director.client.gizmo.handles.ResizeHandle.Type.FACE,
            new Vec3d(max.x, center.y, center.z), handleSize, true, Axis.X));
        handles.add(new com.director.client.gizmo.handles.ResizeHandle(
            "face_y_min", com.director.client.gizmo.handles.ResizeHandle.Type.FACE,
            new Vec3d(center.x, min.y, center.z), handleSize, false, Axis.Y));
        handles.add(new com.director.client.gizmo.handles.ResizeHandle(
            "face_y_max", com.director.client.gizmo.handles.ResizeHandle.Type.FACE,
            new Vec3d(center.x, max.y, center.z), handleSize, true, Axis.Y));
        handles.add(new com.director.client.gizmo.handles.ResizeHandle(
            "face_z_min", com.director.client.gizmo.handles.ResizeHandle.Type.FACE,
            new Vec3d(center.x, center.y, min.z), handleSize, false, Axis.Z));
        handles.add(new com.director.client.gizmo.handles.ResizeHandle(
            "face_z_max", com.director.client.gizmo.handles.ResizeHandle.Type.FACE,
            new Vec3d(center.x, center.y, max.z), handleSize, true, Axis.Z));
        
        // 不再创建角点手柄
        // 用户只需要面中心小方块来调整选区大小，不需要角点手柄
        
        LOGGER.debug("[GizmoManager] 创建了 {} 个 ResizeHandle (6 个面)", handles.size());
        return handles;
    }
    
    /**
     * 停用 Gizmo
     */
    public void deactivate() {
        if (context != null) {
            // ✅ BOUNDS Gizmo（用于移动选区整体）不应在停用时“体素化 box 并重建选区”，
            // 否则会把包围盒内空气也选中，改变框选工具语义。
            // 移动本身已由 TransformApplier.applyMove → SelectionManager.offsetSelection 实时写回。
            // ✅ TRANSFORM Gizmo（用于演员编辑）也不应在停用时调用 apply()，
            // 因为演员的 Gizmo 是用于编辑 TransformClip，不应该影响方块选区。
            if (context.getType() != GizmoType.BOUNDS && context.getType() != GizmoType.TRANSFORM) {
                apply();
            }
            
            context.setActive(false);
            context = null;
            controller = null;
            renderer = null;
            
            LOGGER.info("[GizmoManager] Gizmo 已停用");
        }
    }
    
    /**
     * 每帧更新
     * 
     * @param rayOrigin 射线起点
     * @param rayDir 射线方向（归一化）
     * @param mouseDown 鼠标是否按下
     * @param mouseReleased 鼠标是否释放
     */
    public void tick(Vec3d rayOrigin, Vec3d rayDir, boolean mouseDown, boolean mouseReleased) {
        if (context == null || !context.isActive() || controller == null) {
            return;
        }
        
        controller.update(rayOrigin, rayDir, mouseDown, mouseReleased);
        
        // 更新 Handle 位置（如果 Box 或 Pivot 改变了）
        updateHandlePositions();
    }
    
    /**
     * 渲染 Gizmo
     * ✅ v4.7: 更新方法签名以支持 Camera 参数
     * 
     * @param time 动画时间
     * @param cameraPos 摄像机位置（已弃用，使用 camera 参数）
     * @param matrices MatrixStack
     * @param vcp VertexConsumerProvider
     */
    public void render(double time, Vec3d cameraPos, MatrixStack matrices, VertexConsumerProvider vcp) {
        if (context == null) {
            LOGGER.warn("[GizmoManager] render: context 为 null");
            return;
        }
        
        if (!context.isActive()) {
            LOGGER.warn("[GizmoManager] render: context.isActive() = false");
            return;
        }
        
        if (renderer == null) {
            LOGGER.error("[GizmoManager] render: renderer 为 null");
            return;
        }
        
        if (vcp == null) {
            LOGGER.error("[GizmoManager] render: VertexConsumerProvider 为 null");
            return;
        }
        
        // ✅ 获取 Camera（从 MinecraftClient）
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.gameRenderer == null || client.gameRenderer.getCamera() == null) {
            LOGGER.error("[GizmoManager] render: Camera 为 null");
            return;
        }
        
        net.minecraft.client.render.Camera camera = client.gameRenderer.getCamera();
        
        LOGGER.debug("[GizmoManager] 调用 renderer.render()");
        try {
            renderer.render(matrices, camera, vcp, time);
            LOGGER.debug("[GizmoManager] renderer.render() 调用成功");
        } catch (Exception e) {
            LOGGER.error("[GizmoManager] renderer.render() 调用失败", e);
            throw e; // 重新抛出异常，让上层处理
        }
    }
    
    /**
     * 更新 Handle 位置
     */
    private void updateHandlePositions() {
        if (controller == null || context == null) {
            return;
        }
        
        // 如果 Box 或 Pivot 改变了，需要更新 Handle 位置
        // 这里简化实现，实际应该重新创建 Handle 列表
        Vec3d newPivot = context.getPivot();
        
        // 更新中心手柄位置
        List<GizmoHandle> handles = controller.getHandles();
        for (GizmoHandle handle : handles) {
            if (handle instanceof CenterHandle) {
                handle.setPosition(newPivot);
            } else if (handle instanceof AxisHandle || handle instanceof PlaneHandle) {
                // 轴和平面手柄跟随轴心点
                handle.setPosition(newPivot);
            }
        }
        
        // BOUNDS：不使用 ResizeHandle（避免和 BoxSelectTool 的 FaceHandleManager 冲突）
    }
    
    /**
     * 更新 ResizeHandle 位置（根据当前 Box）
     * 
     * 简化实现：如果 Box 改变，重新创建 ResizeHandle 列表
     * （更简单可靠，避免位置不匹配问题）
     */
    @SuppressWarnings("unused")
    private void updateResizeHandlePositions() {
        Box box = context.getTargetBox();
        if (box == null) {
            return;
        }
        
        // 重新创建 ResizeHandle 列表（根据新的 Box）
        if (context.getType() == GizmoType.BOUNDS) {
            List<com.director.client.gizmo.handles.ResizeHandle> newResizeHandles = 
                createResizeHandles(context);
            controller.setResizeHandles(newResizeHandles);
        }
    }
    
    /**
     * 应用 Gizmo 变换（写回 SelectionManager）
     */
    public void apply() {
        if (context == null || !context.isActive()) {
            return;
        }

        // ✅ BOUNDS Gizmo 不做“用 Box 重建选区”的 apply（避免选中空气）。
        if (context.getType() == GizmoType.BOUNDS) {
            return;
        }
        
        Box newBox = context.getTargetBox();
        
        // 如果有自定义回调，使用回调
        if (onApplyCallback != null) {
            onApplyCallback.accept(context);
            return;
        }
        
        // 默认行为：写回 SelectionManager
        Set<net.minecraft.util.math.BlockPos> blocks = GizmoVoxelizer.voxelize(newBox);
        SelectionManager manager = SelectionManager.get();
        if (manager != null) {
            manager.clearSelection();
            manager.getSelectionSet().addAll(blocks);
            LOGGER.info("[GizmoManager] 已应用变换：{} 个方块", blocks.size());
        }
    }
    
    /**
     * 设置应用回调
     * 
     * @param callback 回调函数（接受 GizmoContext，执行自定义应用逻辑）
     */
    public void setOnApply(Consumer<GizmoContext> callback) {
        this.onApplyCallback = callback;
    }
    
    // ========== Getters ==========
    
    public boolean isActive() {
        return context != null && context.isActive();
    }
    
    public GizmoContext getContext() {
        return context;
    }

    /**
     * 获取 Gizmo 交互控制器
     * 
     * @return GizmoInteractionController，如果未激活则返回 null
     */
    public GizmoInteractionController getController() {
        return controller;
    }

    /**
     * 是否正在悬停/拖拽 Gizmo 的可交互手柄（用于输入层屏蔽“框选拖拽/点击”）。
     */
    public boolean isHot() {
        if (controller == null || context == null || !context.isActive()) {
            return false;
        }
        return controller.getHoveredHandle() != null || controller.getActiveHandle() != null;
    }

    /**
     * 是否正在拖拽 Gizmo 手柄。
     */
    public boolean isDraggingHandle() {
        if (controller == null || context == null || !context.isActive()) {
            return false;
        }
        return controller.getActiveHandle() != null;
    }
    
    public GizmoType getType() {
        return context != null ? context.getType() : null;
    }
    
    public Box getTargetBox() {
        return context != null ? context.getTargetBox() : null;
    }
    
    public Vec3d getPivot() {
        return context != null ? context.getPivot() : null;
    }

    /**
     * 获取当前 Gizmo 模式
     *
     * @return GizmoMode，如果未激活则返回 TRANSLATE
     */
    public GizmoMode getMode() {
        return context != null && context.getMode() != null ? context.getMode() : GizmoMode.TRANSLATE;
    }

    /**
     * 设置当前 Gizmo 模式
     *
     * @param mode GizmoMode，如果为 null 则使用 TRANSLATE
     */
    public void setMode(GizmoMode mode) {
        if (context != null) {
            context.setMode(mode);
        }
    }
    
    // ===========================================================
    // ✅ v2.3: 基础控制接口（与 SelectionManager v3.0 衔接）
    // ===========================================================
    
    /**
     * 设置 Gizmo 中心点（Pivot）
     * 用于实时同步选区中心
     */
    public void setPivot(Vec3d pivot) {
        if (context != null) {
            context.setPivot(pivot);
            LOGGER.info("[GizmoManager] 🎯 设置 Pivot: {}", pivot);
        } else {
            LOGGER.warn("[GizmoManager] ⚠️ 无法设置 Pivot：Gizmo 未激活");
        }
    }

    /**
     * 设置 Gizmo 的目标包围盒（不重建 Gizmo / 不重建 handles）。
     * <p>
     * 重要：用于选区变化或 Gizmo 拖拽过程中同步 Box，避免频繁 {@link #activate} 导致拖拽中断。
     */
    public void setTargetBox(Box box) {
        if (context != null) {
            context.setTargetBox(box);
            // ✅ BOUNDS Gizmo：永远以 targetBox 的几何中心作为 pivot
            if (context.getType() == GizmoType.BOUNDS && box != null) {
                Vec3d center = new Vec3d(
                    (box.minX + box.maxX) / 2.0,
                    (box.minY + box.maxY) / 2.0,
                    (box.minZ + box.maxZ) / 2.0
                );
                context.setPivot(center);
            }
        }
    }
    
    /**
     * 设置 Gizmo 可见性
     */
    public void setVisible(boolean visible) {
        if (context != null) {
            context.setActive(visible);
            LOGGER.info("[GizmoManager] 👁️ Gizmo 可见性 = {}", visible);
        } else {
            LOGGER.warn("[GizmoManager] ⚠️ 无法设置可见性：Gizmo 未激活");
        }
    }
    
    /**
     * 检查 Gizmo 是否可见
     */
    public boolean isVisible() {
        return isActive();
    }
    
    // ===========================================================
    // ✅ v2.3: 拖拽逻辑入口（与 SelectionManager v3.0 联动）
    // ===========================================================
    
    /**
     * 在 Gizmo 被点击后调用，用于开始拖拽。
     * 
     * @param axis 拖拽的轴向（X/Y/Z）
     * @param startWorldPos 开始拖拽时的世界坐标
     */
    public void beginDrag(Axis axis, Vec3d startWorldPos) {
        if (!isActive()) {
            LOGGER.warn("[GizmoManager] ⚠️ 无法开始拖拽：Gizmo 未激活");
            return;
        }
        
        this.activeAxis = axis;
        this.dragging = true;
        this.lastDragWorldPos = startWorldPos;
        
        LOGGER.info("[GizmoManager] 🧩 开始拖拽 - 轴={} 起点={}", axis, startWorldPos);
    }
    
    /**
     * 拖拽中持续调用（由 InputRouter 驱动）。
     * 
     * @param currentWorldPos 当前鼠标光标的世界坐标
     */
    public void applyHandleDrag(Vec3d currentWorldPos) {
        if (!dragging || activeAxis == null || !isActive()) {
            return;
        }
        
        if (lastDragWorldPos == null) {
            lastDragWorldPos = currentWorldPos;
            return;
        }
        
        // 🧮 计算在该轴方向上的拖拽距离（方块单位）
        double delta = 0.0;
        switch (activeAxis) {
            case X -> delta = currentWorldPos.x - lastDragWorldPos.x;
            case Y -> delta = currentWorldPos.y - lastDragWorldPos.y;
            case Z -> delta = currentWorldPos.z - lastDragWorldPos.z;
        }
        
        // 转换为整数方块步进
        int blockDelta = (int) Math.round(delta);
        
        if (blockDelta != 0) {
            // 交给 SelectionManager 执行选区扩展
            SelectionManager manager = SelectionManager.get();
            if (manager != null) {
                manager.expandSelection(activeAxis, blockDelta);
                
                // 更新 pivot 位置（用于实时 Gizmo 同步）
                Box newBounds = manager.getCurrentBounds();
                if (newBounds != null) {
                    Vec3d newPivot = new Vec3d(
                        (newBounds.minX + newBounds.maxX) / 2.0,
                        (newBounds.minY + newBounds.maxY) / 2.0,
                        (newBounds.minZ + newBounds.maxZ) / 2.0
                    );
                    setPivot(newPivot);
                    
                    // 更新 Gizmo 的 targetBox
                    if (context != null) {
                        context.setTargetBox(newBounds);
                    }
                    
                    LOGGER.info("[GizmoManager] 🔧 拖拽轴={} Δ={} (方块单位) -> Pivot={}", 
                               activeAxis, blockDelta, newPivot);
                }
            }
        }
        
        lastDragWorldPos = currentWorldPos;
    }
    
    /**
     * 结束拖拽。
     */
    public void endDrag() {
        if (!dragging) {
            return;
        }
        
        LOGGER.info("[GizmoManager] ✅ 结束拖拽 - 轴={} Pivot={}", activeAxis, getPivot());
        
        dragging = false;
        activeAxis = null;
        lastDragWorldPos = null;
    }
    
    // ===========================================================
    // ✅ v2.3: 清理与重置
    // ===========================================================
    
    /**
     * 清理所有状态（与 SelectionManager.clear() 联动调用）
     */
    public void clear() {
        dragging = false;
        activeAxis = null;
        lastDragWorldPos = null;
        
        // 停用 Gizmo
        deactivate();
        
        LOGGER.info("[GizmoManager] 🧹 状态已清空");
    }
}

