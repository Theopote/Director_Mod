package com.director.core.selection.tools.boxselecttool;

import com.director.client.gizmo.Axis;
import com.director.client.gizmo.GizmoManager;
import com.director.client.gizmo.GizmoType;
import com.director.client.renderer.ToolPreviewManager;
import com.director.client.settings.RenderSettings;
import com.director.core.selection.*;
import com.director.core.selection.algorithms.RegionSelectors;
import com.director.core.selection.events.SelectionEvents;
import com.director.core.selection.block_filters.BlockFilters;
import com.director.core.selection.history.SelectionHistoryManager;
import com.director.core.selection.tools.FaceHandleManager;
import com.director.core.selection.tools.SelectionPhase;
import com.director.ui.theme.Icons;
import com.director.core.util.SnapUtil;
import com.director.ui.cursor.ImGuiCursorController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * 📦 BoxSelectTool v2.0
 * <p>
 * —— Director 框选工具增强版
 * <p>
 * 功能特性：
 * ✅ 两点式建盒（左键第一次设置起点，第二次设置终点）
 * ✅ 包围盒生成时自动在几何中心激活 Gizmo，用于移动选区
 * ✅ 一方块步进（网格对齐）
 * ✅ 支持新建选区 / 加选 / 减选 / 交集 模式
 * ✅ 实时渲染预览（ToolPreviewManager）
 * ✅ 自动同步 SelectionEvents（用于 Renderer / Gizmo / Timeline）
 * ✅ 模块化 Gizmo 激活
 * ✅ 操作性能优化（高效交集算法）
 * ✅ 历史记录支持（撤销/重做）
 *
 * @author Director
 * @version 2.0
 */
public class BoxSelectTool implements SelectionTool, SelectionOperationSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(BoxSelectTool.class);

    // ====================== 状态机 ======================
    private SelectionPhase phase = SelectionPhase.IDLE;
    
    // ====================== 两点式建盒状态 ======================
    private BlockPos firstCorner = null;  // 第一角点（左键点击）
    private BlockPos previewCorner = null; // 预览中的第二角点（鼠标移动实时更新）
    
    // ====================== Gizmo 编辑状态 ======================
    @SuppressWarnings("unused")
    private boolean gizmoActive = false;
    
    // ====================== 面中心小方块管理器 ======================
    private final FaceHandleManager faceHandleManager = FaceHandleManager.SHARED;

    // ====================== 操作模式 ======================
    private SelectionOperation operation = SelectionOperation.NEW;

    @Override
    public String getId() {
        return "box";
    }

    @Override
    public String getDisplayName() {
        return "框选工具";
    }

    @Override
    public String getDescription() {
        return "左键两点框选立方体区域，支持加选/减选/交集模式，支持实时预览与 Gizmo 操作。";
    }

    @Override
    public String getIcon() {
        return Icons.BOX;
    }

    // ====================== 事件处理 ======================

    @Override
    public void onClick(World world, BlockPos pos, boolean isShiftDown) {
        if (world == null || pos == null) return;

        // 如果正在拖拽面中心小方块，不处理点击
        if (faceHandleManager.isDragging()) {
            LOGGER.debug("[BoxSelectTool] 正在拖拽面中心小方块，跳过点击");
            return;
        }

        // 检查是否点击在面中心小方块上
        Box bounds = SelectionManager.get().getCurrentBounds();
        if (bounds != null) {
            Vec3d[] ray = getMouseRay();
            if (ray != null && ray.length == 2) {
                Integer hoveredFace = faceHandleManager.detectHover(bounds, ray[0], ray[1]);
                if (hoveredFace != null) {
                    // 开始拖拽面中心小方块
                    if (faceHandleManager.startDrag(bounds, ray[0], ray[1])) {
                        phase = SelectionPhase.GIZMO_EDIT;
                        updateMouseCursor(true);
                        LOGGER.info("[BoxSelectTool] ✅ 开始拖拽面中心小方块: face={}", hoveredFace);
                        return;
                    }
                }
            }
        }

        // 两点式：第一次左键设置起点，第二次左键直接确认终点并应用
        if (firstCorner == null) {
            firstCorner = pos.toImmutable();
            previewCorner = firstCorner;
            phase = SelectionPhase.FIRST_POINT_SET;

            // 兜底：确保工具预览管理器处于启用状态，避免状态残留导致预览不可见。
            ToolPreviewManager.get().setEnabled(true);
            clearPreview();

            // 显示第一角点预览（单个方块）
            Box previewBox = makeInclusiveBlockBox(firstCorner, firstCorner).expand(0.01);
            ToolPreviewManager.get().setPreviewBox(previewBox);

            LOGGER.info("[BoxSelectTool] ✅ 第一角点已设置: {} (左键点击第二角点完成选择)", firstCorner);
            return;
        }

        Box box = makeInclusiveBlockBox(firstCorner, pos);
        applyBoxSelection(world, box);
        clearPreview();
        LOGGER.info("[BoxSelectTool] ✅ 两点式建盒完成: {} -> {}", firstCorner, pos);
        firstCorner = null;
        previewCorner = null;
        phase = SelectionPhase.IDLE;
    }
    
    @Override
    public void onRightClick(World world, BlockPos pos) {
        if (world == null) return;

        // 右键用于取消当前两点式框选（不参与确认第二角点）
        if (firstCorner != null) {
            clearPreview();
            LOGGER.info("[BoxSelectTool] 取消两点式框选，已清除第一角点: {}", firstCorner);
            firstCorner = null;
            previewCorner = null;
            phase = SelectionPhase.IDLE;
        }
    }

    @Override
    public void onDragStart(World world, BlockPos start) {
        if (world == null || start == null) return;
        
        // 如果正在拖拽面中心小方块，不处理拖拽开始
        if (faceHandleManager.isDragging()) {
            LOGGER.debug("[BoxSelectTool] 正在拖拽面中心小方块，跳过拖拽开始");
            return;
        }
        
        // 检查是否点击在面中心小方块上，如果是则开始拖拽面
        Box bounds = SelectionManager.get().getCurrentBounds();
        if (bounds != null) {
            Vec3d[] ray = getMouseRay();
            if (ray != null && ray.length == 2) {
                Integer hoveredFace = faceHandleManager.detectHover(bounds, ray[0], ray[1]);
                if (hoveredFace != null) {
                    // 开始拖拽面中心小方块
                    if (faceHandleManager.startDrag(bounds, ray[0], ray[1])) {
                        phase = SelectionPhase.GIZMO_EDIT;
                        updateMouseCursor(true);
                        LOGGER.info("[BoxSelectTool] ✅ 开始拖拽面中心小方块: face={}", hoveredFace);
                        return; // 阻止框选拖拽
                    }
                }
            }
        }
        
        // 普通左键拖拽不再触发框选建盒；两点式框选由 onClick 完成。
    }

    @Override
    public void onDragUpdate(World world, BlockPos current) {
        if (world == null || current == null) return;
        
        // 如果正在拖拽面中心小方块，更新拖拽
        if (faceHandleManager.isDragging()) {
            Vec3d[] ray = getMouseRay();
            if (ray != null && ray.length == 2) {
                faceHandleManager.updateDrag(ray[0], ray[1]);
            }
            updateMouseCursor(true);
            return; // 阻止框选拖拽更新
        }

        // Gizmo 拖拽更新由 GizmoInteractionController 处理，这里不需要额外处理
    }

    @Override
    public void onDragEnd(World world, BlockPos end) {
        if (world == null || end == null) return;
        
        // 如果正在拖拽面中心小方块，结束拖拽
        if (faceHandleManager.isDragging()) {
            faceHandleManager.endDrag();
            phase = SelectionPhase.IDLE;
            
            // 拖拽结束后，立即检测当前是否还在悬停
            // 如果不在小方块上，恢复默认光标；如果还在，保持双向箭头光标
            Integer hoveredFace = null;
            if (!SelectionManager.get().getSelectionSet().isEmpty()) {
                Box bounds = SelectionManager.get().getCurrentBounds();
                if (bounds != null) {
                    Vec3d[] ray = getMouseRay();
                    if (ray != null && ray.length == 2) {
                        hoveredFace = faceHandleManager.detectHover(bounds, ray[0], ray[1]);
                    }
                }
            }
            updateMouseCursor(hoveredFace != null);
            
            LOGGER.info("[BoxSelectTool] ✅ 面中心小方块拖拽结束，当前悬停: {}", hoveredFace);
            return;
        }
        
        // Gizmo 拖拽结束由 GizmoInteractionController 处理，这里不需要额外处理
    }

    @Override
    public void tick(World world) {
        // 实时预览渲染由 ToolPreviewManager 管理
        boolean bboxVisible = RenderSettings.get().selection_showBoundingBox;

        // 如果用户关闭“显示包围盒”，立即取消面手柄拖拽/预览，避免继续渲染/误应用 resize
        if (!bboxVisible && faceHandleManager.isDragging()) {
            faceHandleManager.cancelDrag();
            phase = SelectionPhase.IDLE;
            updateMouseCursor(false);
        }

        // ✅ 两点式建盒：实时预览（参考 formacraft 的做法：选点后持续更新 end）
        // 当第一角点已设置且还未左键确认终点时，使用当前鼠标指向的方块作为临时第二角点更新预览框。
        if (phase == SelectionPhase.FIRST_POINT_SET && firstCorner != null && !faceHandleManager.isDragging()) {
            BlockPos hovered = getHoveredBlockPos();
            if (hovered != null) {
                previewCorner = hovered.toImmutable();
            }
            if (previewCorner != null) {
                Box preview = makeDragBoxWithMinHeight(firstCorner, previewCorner).expand(0.01);
                ToolPreviewManager.get().setPreviewBox(preview);
            }
        }
        
        // 获取鼠标射线（面手柄和 Gizmo 都需要）
        Vec3d[] ray = getMouseRay();
        Vec3d rayOrigin = null;
        Vec3d rayDir = null;
        if (ray != null && ray.length == 2) {
            rayOrigin = ray[0];
            rayDir = ray[1];
        }

        // =====================================================
        // ✅ 面中心小方块拖拽：改为 tick 驱动（不依赖 BlockPos 变化）
        // =====================================================
        // 说明：之前依赖 onDragUpdate/onDragEnd，很多输入实现只会在 BlockPos 变化时触发更新，导致“能变光标但拖不动”。
        // 这里改为每帧根据射线更新 offset，并用 GLFW 轮询鼠标释放来结束拖拽，确保拖拽必然生效且能正确退出。
        if (faceHandleManager.isDragging()) {
            try {
                // 1) 每帧更新拖拽 offset（实时预览）
                if (rayOrigin != null && rayDir != null) {
                    faceHandleManager.updateDrag(rayOrigin, rayDir);
                }

                // 2) 轮询鼠标左键：释放则结束拖拽并应用 resize
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null && client.getWindow() != null) {
                    long window = client.getWindow().getHandle();
                    boolean mouseDown = org.lwjgl.glfw.GLFW.glfwGetMouseButton(window, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT)
                            == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                    if (!mouseDown) {
                        faceHandleManager.endDrag();
                        phase = SelectionPhase.IDLE;

                        // 结束后立即重新检测 hover（可能鼠标已经离开）并刷新光标
                        Integer hoveredFace = null;
                        Box bounds = SelectionManager.get().getCurrentBounds();
                        if (bounds != null && rayOrigin != null && rayDir != null) {
                            hoveredFace = faceHandleManager.detectHover(bounds, rayOrigin, rayDir);
                        }
                        updateMouseCursor(bboxVisible && hoveredFace != null);
                    } else {
                        // 拖拽中始终保持 resize 光标
                        updateMouseCursor(true);
                    }
                }
            } catch (Exception e) {
                // 拖拽异常时兜底：取消拖拽并恢复光标，避免卡住
                try {
                    faceHandleManager.cancelDrag();
                } catch (Exception ignored) {}
                phase = SelectionPhase.IDLE;
                updateMouseCursor(false);
            }
            // 拖拽面手柄时不再继续 hover/gizmo 检测，避免干扰
            return;
        }
        
        // 检测面中心小方块的鼠标悬停
        Integer hoveredFace = null;
        if (bboxVisible && world != null && !SelectionManager.get().getSelectionSet().isEmpty()) {
            Box bounds = SelectionManager.get().getCurrentBounds();
            if (bounds != null && rayOrigin != null && rayDir != null) {
                // 检测悬停
                hoveredFace = faceHandleManager.detectHover(bounds, rayOrigin, rayDir);
                if (hoveredFace != null) {
                    LOGGER.info("[BoxSelectTool] ✅ 检测到悬停在面中心小方块: face={}, bounds={}", hoveredFace, bounds);
                } else {
                    LOGGER.debug("[BoxSelectTool] 未检测到悬停: rayOrigin={}, rayDir={}", rayOrigin, rayDir);
                }
            }
        }
        
        // 更新光标
        boolean shouldShowResizeCursor = bboxVisible && (faceHandleManager.isDragging() || hoveredFace != null);
        updateMouseCursor(shouldShowResizeCursor);
        
        // 更新 Gizmo 交互（悬停高亮和拖动）
        if (rayOrigin != null && rayDir != null) {
            updateGizmoInteraction(rayOrigin, rayDir);
        }
    }
    
    /**
     * 更新 Gizmo 交互状态（每帧调用）
     */
    private void updateGizmoInteraction(Vec3d rayOrigin, Vec3d rayDir) {
        try {
            com.director.client.gizmo.GizmoManager gizmoManager = com.director.client.gizmo.GizmoManager.get();
            if (gizmoManager == null || !gizmoManager.isActive()) {
                return;
            }
            
            // 检测鼠标状态
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            if (client == null || client.mouse == null) {
                return;
            }
            
            // 获取鼠标按下状态（通过 GLFW）
            long window = client.getWindow().getHandle();
            boolean mouseDown = org.lwjgl.glfw.GLFW.glfwGetMouseButton(window, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) 
                    == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            boolean mouseReleased = org.lwjgl.glfw.GLFW.glfwGetMouseButton(window, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) 
                    == org.lwjgl.glfw.GLFW.GLFW_RELEASE;
            
            // 调用 GizmoManager.tick()
            gizmoManager.tick(rayOrigin, rayDir, mouseDown, mouseReleased);
        } catch (Exception e) {
            LOGGER.debug("[BoxSelectTool] 更新 Gizmo 交互失败", e);
        }
    }
    
    /**
     * 获取与 ImGui 鼠标位置对应的世界射线（相机原点 + 方向）
     * v5.1: 修复为基于实际鼠标位置而非屏幕中心，确保 hover 检测准确
     * @return [rayOrigin, rayDir] 或 fallback
     */
    private Vec3d[] getMouseRay() {
        try {
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            if (client == null || client.gameRenderer == null || client.getWindow() == null) {
                return new Vec3d[]{Vec3d.ZERO, new Vec3d(0, 0, -1)};
            }

            var camera = client.gameRenderer.getCamera();
            double mouseX = client.mouse.getX();
            double mouseY = client.mouse.getY();

            // 视口尺寸
            double width = client.getWindow().getFramebufferWidth();
            double height = client.getWindow().getFramebufferHeight();

            // FOV（度转弧度）
            double fov = client.options.getFov().getValue() * Math.PI / 180.0;
            double aspect = width / height;

            // 归一化设备坐标 (-1, 1)
            double ndcX = (2.0 * mouseX / width - 1.0);
            double ndcY = (1.0 - 2.0 * mouseY / height); // 注意：屏幕Y是反的

            // 投影到相机平面（透视投影）
            double tanFov = Math.tan(fov / 2.0);
            double px = ndcX * tanFov * aspect;
            double py = ndcY * tanFov;

            // 相机方向向量（使用四元数旋转）
            var q = camera.getRotation();
            // 前向向量在相机空间是 (0, 0, -1)，用四元数旋转到世界空间
            Vec3d camDir = rotateByQuaternion(new Vec3d(0, 0, -1), q);
            Vec3d camRight = camDir.crossProduct(new Vec3d(0, 1, 0)).normalize();
            Vec3d camUp = camRight.crossProduct(camDir).normalize();

            // 构建射线方向
            Vec3d rayDir = camDir
                    .add(camRight.multiply(px))
                    .add(camUp.multiply(py))
                    .normalize();

            return new Vec3d[]{camera.getPos(), rayDir};
        } catch (Exception e) {
            LOGGER.debug("[BoxSelectTool] 获取鼠标射线失败", e);
            return null;
        }
    }
    
    /**
     * 使用四元数旋转向量
     */
    private Vec3d rotateByQuaternion(Vec3d v, org.joml.Quaternionf q) {
        float x = (float) v.x;
        float y = (float) v.y;
        float z = (float) v.z;
        float qx = q.x, qy = q.y, qz = q.z, qw = q.w;
        
        // v' = v + 2*cross(q.xyz, cross(q.xyz, v) + qw*v)
        float cx1 = qy * z - qz * y;
        float cy1 = qz * x - qx * z;
        float cz1 = qx * y - qy * x;
        float rx = x + 2.0f * (qy * cz1 - qz * cy1 + qw * cx1);
        float ry = y + 2.0f * (qz * cx1 - qx * cz1 + qw * cy1);
        float rz = z + 2.0f * (qx * cy1 - qy * cx1 + qw * cz1);
        
        return new Vec3d(rx, ry, rz);
    }
    
    /**
     * 更新 ImGui 光标类型（取代原有的 GLFW 光标逻辑）
     */
    private void updateMouseCursor(boolean shouldShowResizeCursor) {
        ImGuiCursorController cursor = ImGuiCursorController.get();

        if (!shouldShowResizeCursor) {
            cursor.setCursorType(ImGuiCursorController.CursorType.DEFAULT);
            return;
        }

        Integer targetFace = faceHandleManager.getHoveredFaceIndex();
        if (targetFace == null && faceHandleManager.isDragging()) {
            targetFace = faceHandleManager.getDraggingFaceIndex();
        }

        if (targetFace == null) {
            cursor.setCursorType(ImGuiCursorController.CursorType.DEFAULT);
            return;
        }

        Axis axis = FaceHandleManager.getFaceAxis(targetFace);
        cursor.setCursorByAxis(axis);
    }

    private BlockPos getHoveredBlockPos() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.world == null || client.player == null || client.options == null) {
                return null;
            }

            // 优先复用输入系统当前射线命中，保证与点击/掠过逻辑一致。
            try {
                BlockHitResult current = com.director.client.input.DirectorInputSystem.getCurrentRay();
                if (current != null && current.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
                    return current.getBlockPos();
                }
            } catch (Throwable ignored) {
                // 输入系统尚未就绪时走下方回退射线
            }

            Vec3d[] ray = getMouseRay();
            if (ray == null || ray.length != 2) {
                return null;
            }

            double maxDist = client.options.getViewDistance().getValue() * 16.0;
            Vec3d endPos = ray[0].add(ray[1].multiply(maxDist));
            BlockHitResult hit = client.world.raycast(new net.minecraft.world.RaycastContext(
                    ray[0],
                    endPos,
                    net.minecraft.world.RaycastContext.ShapeType.OUTLINE,
                    net.minecraft.world.RaycastContext.FluidHandling.NONE,
                    client.player
            ));

            if (hit != null && hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
                return hit.getBlockPos();
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void clearSelection() {
        SelectionManager.get().clearSelection();
        clearPreview();
        clearDrag();
        clearGizmo();
        firstCorner = null;
        previewCorner = null;
        phase = SelectionPhase.IDLE;
        SelectionEvents.fireChanged(SelectionChangeType.SELECTION_CLEARED);
        LOGGER.debug("[BoxSelectTool] 清除选区");
    }

    @Override
    public boolean shouldRenderOverlay() {
        SelectionManager manager = SelectionManager.get();
        return phase != SelectionPhase.IDLE
            || !manager.getSelectionSet().isEmpty()
            || manager.getCurrentBounds() != null;
    }

    @Override
    public SelectionState getCurrentSelection() {
        SelectionManager manager = SelectionManager.get();
        return SelectionState.of(manager.getSelectionSet().asUnmodifiableSet(), getId());
    }

    @Override
    public void onActivate(World world) {
        clearPreview();
        clearDrag();
        clearGizmo();
        firstCorner = null;
        previewCorner = null;
        phase = SelectionPhase.IDLE;
        LOGGER.debug("[BoxSelectTool] 工具激活 (模式: {})", operation.name());
    }

    @Override
    public void onDeactivate(World world) {
        clearPreview();
        clearDrag();
        clearGizmo();
        firstCorner = null;
        previewCorner = null;
        phase = SelectionPhase.IDLE;
        LOGGER.debug("[BoxSelectTool] 工具停用");
    }

    // ====================== 辅助逻辑 ======================

    /**
     * 两点（BlockPos）创建"含体积"的方块盒（max 边 +1，以覆盖整块）
     */
    private static Box makeInclusiveBlockBox(BlockPos a, BlockPos b) {
        int minX = Math.min(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxX = Math.max(a.getX(), b.getX()) + 1;
        int maxY = Math.max(a.getY(), b.getY()) + 1;
        int maxZ = Math.max(a.getZ(), b.getZ()) + 1;
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * 拖拽式：最小高度=1 方块（若 Y 相同或相邻，强制厚度=1）
     */
    private static Box makeDragBoxWithMinHeight(BlockPos a, BlockPos b) {
        Box raw = makeInclusiveBlockBox(a, b);
        return SnapUtil.ensureMinThicknessOne(raw);
    }
    
    /**
     * 应用盒子选择：根据 operation 合并到 SelectionSet，触发事件，激活 Gizmo（Bounds）
     */
    private void applyBoxSelection(World world, Box box) {
        if (world == null || box == null) return;

        SelectionManager manager = SelectionManager.get();
        SelectionSet set = manager.getSelectionSet();

        if (operation == SelectionOperation.NEW) {
            manager.setSelectionBoundsOverride(box);
        } else {
            manager.clearSelectionBoundsOverride();
        }
        
        // 📊 记录操作前状态（用于历史记录）
        SelectionState beforeState = SelectionState.of(set.asUnmodifiableSet(), getId());

        // 框选工具：选中框内所有非空气方块，这样用户可以看到被选中的方块样式。
        // 框选工具允许自由调整选框大小（不受方块分布影响），但最终选区只包含实际存在的方块。
        Set<BlockPos> found = RegionSelectors.selectInBox(world, box, BlockFilters.nonAir());

        int beforeSize = set.size();
        
        // 快速模式运算
        switch (operation) {
            case NEW -> {
                // 新建选区：清除现有选区，然后添加新选区
                set.clear();
                set.addAll(found);
            }
            case ADD -> set.addAll(found);
            case SUBTRACT -> {
                // SelectionSet 没有 removeAll，使用 removeIf
                Set<BlockPos> toRemove = new HashSet<>(found);
                set.removeIf(toRemove::contains);
            }
            case INTERSECT -> {
                // ⚡ 高效交集：只保留 found 中存在的方块
                Set<BlockPos> toKeep = new HashSet<>(found);
                // 移除不在交集中的方块
                set.removeIf(pos -> !toKeep.contains(pos));
            }
        }

        int afterSize = set.size();
        int delta = switch (operation) {
            case NEW, ADD -> Math.max(0, afterSize - beforeSize);
            case SUBTRACT, INTERSECT -> Math.max(0, beforeSize - afterSize);
        };
        LOGGER.info("[BoxSelectTool] 框选完成 ({} 模式) → 影响 {} 个方块", operation.name(), delta);

        // 📊 记录操作到历史管理器
        try {
            SelectionState afterState = SelectionState.of(set.asUnmodifiableSet(), getId());
            SelectionHistoryManager historyManager = SelectionHistoryManager.getInstance();
            historyManager.recordOperation(beforeState, afterState, operation.name(), getId());
        } catch (Exception e) {
            LOGGER.warn("[BoxSelectTool] 记录历史失败", e);
        }

        // 🔔 触发事件同步
        SelectionEvents.fireChanged(SelectionChangeType.SELECTION_UPDATED);

        // 🧭 激活或停用 Bounds Gizmo（在包围盒几何中心，用于移动选区）
        Box bounds = manager.getCurrentBounds();
        if (bounds == null) {
            clearGizmo();
        } else if (delta > 0 || operation == SelectionOperation.NEW) {
            // 选区变化或新建包围盒时，激活/更新 Gizmo
            Vec3d pivot = new Vec3d(
                (bounds.minX + bounds.maxX) / 2.0,
                (bounds.minY + bounds.maxY) / 2.0,
                (bounds.minZ + bounds.maxZ) / 2.0
            );
            try {
                GizmoManager.get().activate(GizmoType.BOUNDS, bounds, pivot);
                gizmoActive = true;
                LOGGER.info("[BoxSelectTool] ✅ 已激活 Gizmo（几何中心）：Box={}, Pivot={}", bounds, pivot);
            } catch (Exception e) {
                LOGGER.warn("[BoxSelectTool] 激活 Gizmo 失败", e);
                gizmoActive = false;
            }
        }
    }

    // ====================== Gizmo 相关方法 ======================
    // Gizmo 现在在包围盒生成时自动在几何中心激活，用于移动选区
    // 拖拽操作由 GizmoInteractionController 处理，通过 TransformApplier 移动选区
    
    // ====================== 清理方法 ======================
    
    /**
     * 清理拖拽态与预览
     */
    private void clearDrag() {
        // 保留方法用于兼容既有调用；当前框选工具已不使用普通框选拖拽状态。
    }
    
    /**
     * 清理预览
     */
    private void clearPreview() {
        previewCorner = null;
        ToolPreviewManager.get().clearPreview();
    }
    
    /**
     * 清理 Gizmo
     */
    private void clearGizmo() {
        gizmoActive = false;
        // 停用 Gizmo，确保所有手柄和 Gizmo 元素消失
        try {
            com.director.client.gizmo.GizmoManager.get().deactivate();
        } catch (Exception e) {
            LOGGER.debug("[BoxSelectTool] 停用 Gizmo 失败", e);
        }
        // 恢复默认鼠标光标
        updateMouseCursor(false);
    }

    // ====================== Getters & Setters ======================

    public boolean isDragging() {
        return phase == SelectionPhase.DRAGGING;
    }

    @Override
    public void setOperation(SelectionOperation op) {
        this.operation = op;
        LOGGER.debug("[BoxSelectTool] 操作模式切换为: {}", op.name());
    }

    @Override
    public SelectionOperation getOperation() {
        return operation;
    }

    /**
     * 检查是否正在拖拽面中心小方块
     * 用于 SelectionInputHandler 判断是否需要阻止框选拖拽
     */
    public boolean isDraggingFaceHandle() {
        return faceHandleManager.isDragging();
    }

    /**
     * 输入层入口：当鼠标已悬停在“面中心小方块”上时，尝试立即开始面拖拽。
     *
     * <p>原因：SelectionInputHandler 的“框选拖拽”只会在 BlockPos 变化后才触发 handleDragStart，
     * 这会导致面手柄拖拽无法启动。这里提供一个“按下即开始”的入口。</p>
     */
    public boolean tryBeginFaceHandleDrag() {
        try {
            if (!RenderSettings.get().selection_showBoundingBox) {
                return false;
            }
            Box bounds = SelectionManager.get().getCurrentBounds();
            if (bounds == null) {
                return false;
            }
            Vec3d[] ray = getMouseRay();
            if (ray == null || ray.length != 2) {
                return false;
            }
            Integer hoveredFace = faceHandleManager.detectHover(bounds, ray[0], ray[1]);
            if (hoveredFace == null) {
                return false;
            }
            if (faceHandleManager.startDrag(bounds, ray[0], ray[1])) {
                phase = SelectionPhase.GIZMO_EDIT;
                updateMouseCursor(true);
                LOGGER.info("[BoxSelectTool] ✅ 输入层触发：开始拖拽面中心小方块 face={}", hoveredFace);
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 获取当前悬停的面索引（用于渲染双向箭头）
     * @return 悬停的面索引（0-5），如果没有悬停则返回 null
     */
    public Integer getHoveredFaceIndex() {
        return faceHandleManager.getHoveredFaceIndex();
    }
    
    /**
     * 获取当前拖拽的面索引（用于渲染双向箭头）
     * @return 拖拽的面索引（0-5），如果没有拖拽则返回 null
     */
    public Integer getDraggingFaceIndex() {
        return faceHandleManager.getDraggingFaceIndex();
    }
}
