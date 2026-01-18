package com.chronoblocks.client.gizmo;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import com.chronoblocks.client.gizmo.handles.GizmoHandle;
import com.chronoblocks.client.gizmo.handles.RotationHandle;
import com.chronoblocks.client.gizmo.logic.TransformApplier;
import com.chronoblocks.client.gizmo.util.SnapUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 🧭 GizmoInteractionController - Gizmo 交互控制器
 * 
 * 负责光线检测、Handle 检测优先级、拖拽状态机
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class GizmoInteractionController {
    private GizmoContext context;
    private GizmoState state = GizmoState.IDLE;
    private GizmoHandle hoveredHandle = null;
    private GizmoHandle activeHandle = null;
    private RotationHandle activeRotationHandle = null;
    private List<GizmoHandle> handles = new ArrayList<>();
    private List<RotationHandle> rotationHandles = new ArrayList<>();
    private List<com.chronoblocks.client.gizmo.handles.ResizeHandle> resizeHandles = new ArrayList<>();
    private TransformApplier transformApplier;

    // BOUNDS Gizmo：移动“包围盒(区域)”预览，松手后按过滤器重建选区
    private final com.chronoblocks.core.selection.SelectionManager selectionManager =
        com.chronoblocks.core.selection.SelectionManager.get();
    private Vec3d boundsMoveCarry = Vec3d.ZERO;
    
    public GizmoInteractionController(GizmoContext context) {
        this.context = context;
        // 初始化 TransformApplier
        this.transformApplier = new TransformApplier(
            com.chronoblocks.core.selection.SelectionManager.get()
        );
    }
    
    public void setTransformApplier(TransformApplier applier) {
        this.transformApplier = applier;
    }
    
    /**
     * 更新交互状态
     * 
     * @param rayOrigin 射线起点
     * @param rayDir 射线方向（归一化）
     * @param mouseDown 鼠标是否按下
     * @param mouseReleased 鼠标是否释放
     */
    public void update(Vec3d rayOrigin, Vec3d rayDir, boolean mouseDown, boolean mouseReleased) {
        if (state == GizmoState.DRAGGING) {
            if (mouseReleased) {
                // 结束拖拽
                endDrag();
            } else if (mouseDown && activeHandle != null) {
                // 更新拖拽
                updateDrag(rayOrigin, rayDir);
            }
        } else {
            if (mouseDown) {
                // 开始拖拽
                startDrag(rayOrigin, rayDir);
            } else {
                // 检测悬停
                detectHover(rayOrigin, rayDir);
            }
        }
    }
    
    /**
     * 检测悬停
     */
    private void detectHover(Vec3d rayOrigin, Vec3d rayDir) {
        GizmoHandle closestHandle = null;
        RotationHandle closestRotationHandle = null;
        double closestDistance = Double.POSITIVE_INFINITY;
        
        // 优先检测旋转环（如果存在）
        for (RotationHandle rotHandle : rotationHandles) {
            double distance = rotHandle.intersectRay(rayOrigin, rayDir);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestRotationHandle = rotHandle;
                closestHandle = null; // 旋转环优先
            }
        }
        
        // 检测 ResizeHandle（优先级高于普通 Handle）
        for (com.chronoblocks.client.gizmo.handles.ResizeHandle resizeHandle : resizeHandles) {
            HitResult hit = resizeHandle.intersectRay(rayOrigin, rayDir);
            if (hit.isHit() && hit.getDistance() < closestDistance) {
                closestDistance = hit.getDistance();
                closestHandle = resizeHandle;
                closestRotationHandle = null;
            }
        }
        
        // 检测普通 Handle
        for (GizmoHandle handle : handles) {
            HitResult hit = handle.intersectRay(rayOrigin, rayDir);
            if (hit.isHit() && hit.getDistance() < closestDistance) {
                closestDistance = hit.getDistance();
                closestHandle = handle;
                closestRotationHandle = null; // 普通 Handle 优先于旋转环
            }
        }
        
        if (closestRotationHandle != null) {
            // 旋转环悬停（当前简化，不显示悬停状态）
            hoveredHandle = null;
            state = GizmoState.HOVER;
        } else if (closestHandle != null) {
            if (hoveredHandle != closestHandle) {
                hoveredHandle = closestHandle;
                state = GizmoState.HOVER;
            }
        } else {
            hoveredHandle = null;
            state = GizmoState.IDLE;
        }
    }
    
    /**
     * 开始拖拽
     */
    private void startDrag(Vec3d rayOrigin, Vec3d rayDir) {
        // 优先检测旋转环
        for (RotationHandle rotHandle : rotationHandles) {
            double distance = rotHandle.intersectRay(rayOrigin, rayDir);
            if (distance < Double.POSITIVE_INFINITY) {
                activeRotationHandle = rotHandle;
                rotHandle.beginRotation(rayOrigin, rayDir);
                state = GizmoState.DRAGGING;
                return;
            }
        }
        
        // 检测普通 Handle
        if (hoveredHandle != null) {
            activeHandle = hoveredHandle;
            if (transformApplier != null) {
                transformApplier.resetAccumulators();
            }
            if (context != null && context.getType() == GizmoType.BOUNDS && isMoveHandle(activeHandle)) {
                boundsMoveCarry = Vec3d.ZERO;
                selectionManager.setBoundsGizmoMovePreviewBounds(context.getTargetBox());
            }
            activeHandle.onDragStart(rayOrigin, rayDir);
            context.setActiveHandle(activeHandle);
            state = GizmoState.DRAGGING;
        }
    }
    
    /**
     * 更新拖拽
     * 
     * 实时联动流程：
     * - 计算拖拽增量（delta）
     * - 应用变换（通过 TransformApplier）
     * - TransformApplier → SelectionManager.expandSelection()
     * - SelectionManager 自动更新预览框（通过 autoPreviewEnabled）
     */
    private void updateDrag(Vec3d rayOrigin, Vec3d rayDir) {
        if (activeRotationHandle != null) {
            // 更新旋转
            activeRotationHandle.updateRotation(rayOrigin, rayDir);
            double deltaAngle = activeRotationHandle.getDeltaAngle();
            
            // 应用旋转（通过 TransformApplier）
            if (transformApplier != null && Math.abs(deltaAngle) > 1e-6) {
                transformApplier.applyRotation(deltaAngle, activeRotationHandle.getAxis());
            }
        } else if (activeHandle != null) {
            Vec3d delta = activeHandle.onDragUpdate(rayOrigin, rayDir);
            
            // 应用吸附
            if (context.getSnapStep() > 0) {
                delta = SnapUtil.snapVec(delta, context.getSnapStep(), null);
            }
            
            // 🧩 应用变换（实时联动 SelectionManager）
            // - ResizeHandle → applyResize() → expandSelection() → 自动更新预览框
            // - AxisHandle/PlaneHandle → applyMove() → offsetSelection() → 自动更新预览框
            applyTransform(delta);
            
            // ✅ BOUNDS Gizmo（移动包围盒）拖拽中不要用“当前选区 bounds”覆盖 targetBox，
            // 否则会出现“Gizmo 能动但包围盒不动”的现象（因为选区此时还没重建）。
            // ⭐ TRANSFORM Gizmo（Actor 编辑）不应该从方块选区更新 Box
            if (context != null && context.getType() == GizmoType.TRANSFORM) {
                // TRANSFORM Gizmo：Box 由 ActorGizmoController.updateGizmoPosition() 管理，不从选区更新
            } else if (!(context != null && context.getType() == GizmoType.BOUNDS && isMoveHandle(activeHandle))) {
                updateBoxFromSelection();
            }
        }
    }
    
    /**
     * 结束拖拽
     * 
     * 实时联动流程：
     * - 结束拖拽状态
     * - 更新 Box（从 SelectionManager 同步）
     * - （可选）清除预览框或保持显示
     */
    private void endDrag() {
        if (activeRotationHandle != null) {
            activeRotationHandle.endRotation();
            activeRotationHandle = null;
            state = GizmoState.IDLE;
        } else if (activeHandle != null) {
            activeHandle.onDragEnd();
            if (context != null && context.getType() == GizmoType.BOUNDS && isMoveHandle(activeHandle)) {
                Box moved = context.getTargetBox();
                selectionManager.clearBoundsGizmoMovePreviewBounds();
                selectionManager.rebuildSelectionFromBoundsForBoundsGizmo(moved);
            }
            activeHandle = null;
            if (context != null) {
                context.setActiveHandle(null);
            }
            if (transformApplier != null) {
                transformApplier.resetAccumulators();
            }
            state = GizmoState.IDLE;
            
            // ⭐ TRANSFORM Gizmo（Actor 编辑）不应该从方块选区更新 Box
            // 更新 Box（从 SelectionManager 同步，确保 Gizmo 与选区一致）
            if (context == null || context.getType() != GizmoType.TRANSFORM) {
                updateBoxFromSelection();
            }
            
            // 🧩 可选：清除预览框
            // 注释：如果希望拖拽结束后保持预览框显示，可以注释掉下面的代码
            // 当前实现：保持预览框显示，因为 SelectionManager 已经自动更新了预览框
            // 如果需要清除，可以取消注释：
            // try {
            //     com.chronoblocks.client.renderer.SelectionRenderer.getInstance().clearPreview();
            // } catch (Exception e) {
            //     // 如果 SelectionRenderer 不可用，忽略错误
            // }
        }
        
        // 清除悬停状态，强制重新检测（鼠标可能已经不在手柄上了）
        hoveredHandle = null;
    }
    
    /**
     * 应用变换（根据 Handle 类型）
     * 
     * 使用 TransformApplier 将变换应用到选区或动画目标
     */
    private void applyTransform(Vec3d delta) {
        if (transformApplier == null) {
            // 回退到旧方法（直接修改 Box）
            applyTransformLegacy(delta);
            return;
        }

        // ✅ BOUNDS Gizmo：轴/平面/中心手柄移动“包围盒预览”，不平移 selectionSet
        if (context != null && context.getType() == GizmoType.BOUNDS && isMoveHandle(activeHandle)) {
            applyBoundsMove(delta);
            return;
        }
        
        // 使用 TransformApplier 应用变换
        if (activeHandle instanceof com.chronoblocks.client.gizmo.handles.ResizeHandle resizeHandle) {
            // ResizeHandle：应用缩放
            Vec3d axisDelta = resizeHandle.getAxisDelta(delta);
            transformApplier.applyResize(axisDelta);
        } else if (activeHandle instanceof com.chronoblocks.client.gizmo.handles.AxisHandle) {
            // 轴手柄：平移选区
            transformApplier.applyMove(delta);
        } else if (activeHandle instanceof com.chronoblocks.client.gizmo.handles.PlaneHandle) {
            // 平面手柄：在平面上平移选区
            transformApplier.applyMove(delta);
        } else if (activeHandle instanceof com.chronoblocks.client.gizmo.handles.CornerHandle corner) {
            // 角点手柄：从角点调整选区大小
            transformApplier.applyResizeFromCorner(delta, corner.getCornerSign());
        } else if (activeHandle instanceof com.chronoblocks.client.gizmo.handles.CenterHandle) {
            // 中心手柄：平移选区（移动轴心点会同时平移选区）
            transformApplier.applyMove(delta);
            // 更新轴心点
            Vec3d newPivot = context.getPivot().add(delta);
            context.setPivot(newPivot);
        }
        
        // ⭐ TRANSFORM Gizmo（Actor 编辑）不应该从方块选区更新 Box
        // 更新 Box（从 SelectionManager 同步）
        if (context == null || context.getType() != GizmoType.TRANSFORM) {
            updateBoxFromSelection();
        }
    }

    private boolean isMoveHandle(GizmoHandle handle) {
        return handle instanceof com.chronoblocks.client.gizmo.handles.AxisHandle
            || handle instanceof com.chronoblocks.client.gizmo.handles.PlaneHandle
            || handle instanceof com.chronoblocks.client.gizmo.handles.CenterHandle;
    }

    private int extractStep(double carry) {
        if (carry >= 1.0) return (int) Math.floor(carry);
        if (carry <= -1.0) return (int) Math.ceil(carry);
        return 0;
    }

    private void applyBoundsMove(Vec3d delta) {
        if (context == null) return;

        // 吸附保持一致
        if (context.getSnapStep() > 0) {
            delta = SnapUtil.snapVec(delta, context.getSnapStep(), null);
        }

        boundsMoveCarry = boundsMoveCarry.add(delta);

        int stepX = extractStep(boundsMoveCarry.x);
        int stepY = extractStep(boundsMoveCarry.y);
        int stepZ = extractStep(boundsMoveCarry.z);
        if (stepX == 0 && stepY == 0 && stepZ == 0) {
            return;
        }

        boundsMoveCarry = new Vec3d(boundsMoveCarry.x - stepX, boundsMoveCarry.y - stepY, boundsMoveCarry.z - stepZ);

        Box box = context.getTargetBox();
        Vec3d pivot = context.getPivot();
        if (box == null || pivot == null) return;

        Box moved = box.offset(stepX, stepY, stepZ);
        Vec3d movedPivot = pivot.add(stepX, stepY, stepZ);

        context.setTargetBox(moved);
        context.setPivot(movedPivot);

        // 只更新“包围盒预览”，重建选区在松手时做
        selectionManager.setBoundsGizmoMovePreviewBounds(moved);
    }
    
    /**
     * 旧版变换应用方法（直接修改 Box）
     */
    private void applyTransformLegacy(Vec3d delta) {
        Box currentBox = context.getTargetBox();
        Box newBox = currentBox;
        
        if (activeHandle instanceof com.chronoblocks.client.gizmo.handles.AxisHandle) {
            newBox = com.chronoblocks.client.gizmo.util.TransformUtil.translateBox(currentBox, delta);
        } else if (activeHandle instanceof com.chronoblocks.client.gizmo.handles.PlaneHandle) {
            newBox = com.chronoblocks.client.gizmo.util.TransformUtil.translateBox(currentBox, delta);
        } else if (activeHandle instanceof com.chronoblocks.client.gizmo.handles.CornerHandle corner) {
            newBox = com.chronoblocks.client.gizmo.util.TransformUtil.resizeBoxFromCorner(
                currentBox, delta, corner.getCornerSign()
            );
        } else if (activeHandle instanceof com.chronoblocks.client.gizmo.handles.CenterHandle) {
            Vec3d newPivot = context.getPivot().add(delta);
            context.setPivot(newPivot);
            newBox = com.chronoblocks.client.gizmo.util.TransformUtil.translateBox(currentBox, delta);
        }
        
        context.setTargetBox(newBox);
    }
    
    /**
     * 从 SelectionManager 同步 Box
     */
    private void updateBoxFromSelection() {
        var selectionManager = com.chronoblocks.core.selection.SelectionManager.get();
        var selectionSet = selectionManager.getSelectionSet();
        
        if (selectionSet.isEmpty()) {
            return;
        }
        
        var blocks = selectionSet.asUnmodifiableSet();
        if (blocks.isEmpty()) {
            return;
        }

        Box newBox = getBox(blocks);
        context.setTargetBox(newBox);

        // ✅ BOUNDS Gizmo：pivot 永远是包围盒几何中心
        if (context != null && context.getType() == GizmoType.BOUNDS) {
            Vec3d center = new Vec3d(
                (newBox.minX + newBox.maxX) / 2.0,
                (newBox.minY + newBox.maxY) / 2.0,
                (newBox.minZ + newBox.maxZ) / 2.0
            );
            context.setPivot(center);
        }
    }

    private static @NotNull Box getBox(Set<BlockPos> blocks) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (BlockPos pos : blocks) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        return new Box(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    // ========== Getters & Setters ==========
    
    public void setHandles(List<GizmoHandle> handles) {
        this.handles = new ArrayList<>(handles);
    }
    
    public GizmoState getState() {
        return state;
    }
    
    public GizmoHandle getHoveredHandle() {
        return hoveredHandle;
    }
    
    public GizmoHandle getActiveHandle() {
        return activeHandle;
    }
    
    public List<GizmoHandle> getHandles() {
        return new ArrayList<>(handles);
    }
    
    public void setRotationHandles(List<RotationHandle> rotationHandles) {
        this.rotationHandles = new ArrayList<>(rotationHandles);
    }
    
    public List<RotationHandle> getRotationHandles() {
        return new ArrayList<>(rotationHandles);
    }
    
    public RotationHandle getActiveRotationHandle() {
        return activeRotationHandle;
    }
    
    public TransformApplier getTransformApplier() {
        return transformApplier;
    }
    
    public void setResizeHandles(List<com.chronoblocks.client.gizmo.handles.ResizeHandle> resizeHandles) {
        this.resizeHandles = new ArrayList<>(resizeHandles);
    }
    
    public List<com.chronoblocks.client.gizmo.handles.ResizeHandle> getResizeHandles() {
        return new ArrayList<>(resizeHandles);
    }
}

