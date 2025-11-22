package com.chronoblocks.client.gizmo;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import com.chronoblocks.client.gizmo.handles.GizmoHandle;
import com.chronoblocks.client.gizmo.handles.RotationHandle;
import com.chronoblocks.client.gizmo.logic.TransformApplier;
import com.chronoblocks.client.gizmo.util.SnapUtil;

import java.util.ArrayList;
import java.util.List;

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
            
            // 更新 Box（从 SelectionManager 同步）
            updateBoxFromSelection();
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
            activeHandle = null;
            context.setActiveHandle(null);
            if (transformApplier != null) {
                transformApplier.resetAccumulators();
            }
            state = GizmoState.IDLE;
            
            // 更新 Box（从 SelectionManager 同步，确保 Gizmo 与选区一致）
            updateBoxFromSelection();
            
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
        
        // 使用 TransformApplier 应用变换
        if (activeHandle instanceof com.chronoblocks.client.gizmo.handles.ResizeHandle) {
            // ResizeHandle：应用缩放
            com.chronoblocks.client.gizmo.handles.ResizeHandle resizeHandle = 
                (com.chronoblocks.client.gizmo.handles.ResizeHandle) activeHandle;
            Vec3d axisDelta = resizeHandle.getAxisDelta(delta);
            transformApplier.applyResize(axisDelta);
        } else if (activeHandle instanceof com.chronoblocks.client.gizmo.handles.AxisHandle) {
            // 轴手柄：平移选区
            transformApplier.applyMove(delta);
        } else if (activeHandle instanceof com.chronoblocks.client.gizmo.handles.PlaneHandle) {
            // 平面手柄：在平面上平移选区
            transformApplier.applyMove(delta);
        } else if (activeHandle instanceof com.chronoblocks.client.gizmo.handles.CornerHandle) {
            // 角点手柄：从角点调整选区大小
            com.chronoblocks.client.gizmo.handles.CornerHandle corner = 
                (com.chronoblocks.client.gizmo.handles.CornerHandle) activeHandle;
            transformApplier.applyResizeFromCorner(delta, corner.getCornerSign());
        } else if (activeHandle instanceof com.chronoblocks.client.gizmo.handles.CenterHandle) {
            // 中心手柄：平移选区（移动轴心点会同时平移选区）
            transformApplier.applyMove(delta);
            // 更新轴心点
            Vec3d newPivot = context.getPivot().add(delta);
            context.setPivot(newPivot);
        }
        
        // 更新 Box（从 SelectionManager 同步）
        updateBoxFromSelection();
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
        } else if (activeHandle instanceof com.chronoblocks.client.gizmo.handles.CornerHandle) {
            com.chronoblocks.client.gizmo.handles.CornerHandle corner = 
                (com.chronoblocks.client.gizmo.handles.CornerHandle) activeHandle;
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
        
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        
        for (net.minecraft.util.math.BlockPos pos : blocks) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        
        Box newBox = new Box(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
        context.setTargetBox(newBox);
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

