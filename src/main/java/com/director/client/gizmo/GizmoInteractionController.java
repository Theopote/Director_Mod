package com.director.client.gizmo;

import com.director.core.DirectorServices;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import com.director.client.gizmo.handles.GizmoHandle;
import com.director.client.gizmo.handles.RotationHandle;
import com.director.client.gizmo.logic.TransformApplier;
import com.director.client.gizmo.util.SnapUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Gizmo 交互控制器。
 * <p>
 * 负责光线检测、Handle 检测优先级与拖拽状态机。
 *
 * @author Director Team
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
    private List<com.director.client.gizmo.handles.ResizeHandle> resizeHandles = new ArrayList<>();
    private TransformApplier transformApplier;

    // BOUNDS Gizmo：移动"包围盒(区域)"预览，松手后按过滤器重建选区
    private final com.director.core.selection.SelectionManager selectionManager =
        DirectorServices.get().selection();
    private Vec3d boundsMoveCarry = Vec3d.ZERO;
    
    public GizmoInteractionController(GizmoContext context) {
        this.context = context;
        // 初始化 TransformApplier
        this.transformApplier = new TransformApplier(
            DirectorServices.get().selection()
        );
    }
    
    public void setTransformApplier(TransformApplier applier) {
        this.transformApplier = applier;
    }
    
    /**
     * 更新交互状态。
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
            } else if (mouseDown && (activeHandle != null || activeRotationHandle != null)) {
                // 更新拖拽
                updateDrag(rayOrigin, rayDir);
            }
        } else {
            if (mouseDown) {
                // Mouse-down frame may arrive before a hover frame.
                // Refresh hover first so click-drag can start immediately.
                detectHover(rayOrigin, rayDir);
                // 开始拖拽
                startDrag(rayOrigin, rayDir);
            } else {
                // 检测悬停
                detectHover(rayOrigin, rayDir);
            }
        }
    }
    
    private GizmoMode currentMode() {
        if (context == null || context.getMode() == null) {
            return GizmoMode.TRANSLATE;
        }
        return context.getMode();
    }

    private boolean allowsMoveHandles() {
        GizmoMode mode = currentMode();
        return mode == GizmoMode.TRANSLATE || mode == GizmoMode.SCALE;
    }

    private boolean allowsRotationHandles() {
        return currentMode() == GizmoMode.ROTATE;
    }

    /** 检测悬停。 */
    private void detectHover(Vec3d rayOrigin, Vec3d rayDir) {
        GizmoHandle closestHandle = null;
        RotationHandle closestRotationHandle = null;
        double closestDistance = Double.POSITIVE_INFINITY;

        // 仅在 ROTATE 模式下检测旋转环，避免平移/缩放时旋转环抢占轴手柄
        if (allowsRotationHandles()) {
            for (RotationHandle rotHandle : rotationHandles) {
                double distance = rotHandle.intersectRay(rayOrigin, rayDir);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestRotationHandle = rotHandle;
                    closestHandle = null;
                }
            }
        }
        
        if (!allowsMoveHandles()) {
            hoveredHandle = null;
            if (closestRotationHandle != null) {
                state = GizmoState.HOVER;
            } else {
                state = GizmoState.IDLE;
            }
            return;
        }

        // 检测 ResizeHandle（优先级高于普通 Handle）
        for (com.director.client.gizmo.handles.ResizeHandle resizeHandle : resizeHandles) {
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
    
    /** 开始拖拽。 */
    private void startDrag(Vec3d rayOrigin, Vec3d rayDir) {
        if (allowsRotationHandles()) {
            RotationHandle closestRotationHandle = null;
            double closestDistance = Double.POSITIVE_INFINITY;
            for (RotationHandle rotHandle : rotationHandles) {
                double distance = rotHandle.intersectRay(rayOrigin, rayDir);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestRotationHandle = rotHandle;
                }
            }
            if (closestRotationHandle != null) {
                activeRotationHandle = closestRotationHandle;
                if (transformApplier != null) {
                    transformApplier.resetAccumulators();
                    if (transformApplier instanceof com.director.ui.actor.gizmo.ActorTransformApplier actorApplier) {
                        actorApplier.prepareDragSession();
                    }
                }
                closestRotationHandle.beginRotation(rayOrigin, rayDir);
                state = GizmoState.DRAGGING;
                return;
            }
        }

        if (!allowsMoveHandles()) {
            return;
        }
        
        // 检测普通 Handle
        if (hoveredHandle != null) {
            activeHandle = hoveredHandle;
            if (transformApplier != null) {
                transformApplier.resetAccumulators();
                if (transformApplier instanceof com.director.ui.actor.gizmo.ActorTransformApplier actorApplier) {
                    actorApplier.prepareDragSession();
                }
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
     * 更新拖拽。
     * <p>实时联动流程：
     * <ul>
     *   <li>计算拖拽增量（delta）</li>
     *   <li>应用变换（通过 TransformApplier）</li>
     *   <li>TransformApplier → SelectionManager.expandSelection()</li>
     *   <li>SelectionManager 自动更新预览框（通过 autoPreviewEnabled）</li>
     * </ul>
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
            
            // 应用变换（实时联动 SelectionManager）
            // - ResizeHandle → applyResize() → expandSelection() → 自动更新预览框
            // - AxisHandle/PlaneHandle → applyMove() → offsetSelection() → 自动更新预览框
            applyTransform(delta);
            
            // BOUNDS Gizmo（移动包围盒）：拖拽中不要用"当前选区 bounds"覆盖 targetBox
            // 否则会出现"Gizmo 能动但包围盒不动"（因为选区此时尚未重建）。
            // TRANSFORM Gizmo（Actor 编辑）不应从方块选区更新 Box
            if (context != null && context.getType() == GizmoType.TRANSFORM) {
                // TRANSFORM Gizmo：Box 由 ActorGizmoController.updateGizmoPosition() 管理，不从选区更新
            } else if (!(context != null && context.getType() == GizmoType.BOUNDS && isMoveHandle(activeHandle))) {
                updateBoxFromSelection();
            }
        }
    }
    
    /**
     * 结束拖拽。
     * <p>实时联动流程：结束拖拽状态；从 SelectionManager 同步 Box；（可选）清除或保持预览框显示。
     */
    private void endDrag() {
        if (activeRotationHandle != null) {
            activeRotationHandle.endRotation();
            activeRotationHandle = null;
            if (transformApplier != null) {
                transformApplier.resetAccumulators();
            }
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
            
            // TRANSFORM Gizmo（Actor 编辑）不应从方块选区更新 Box
            // 更新 Box（从 SelectionManager 同步，确保 Gizmo 与选区一致）
            if (context == null || context.getType() != GizmoType.TRANSFORM) {
                updateBoxFromSelection();
            }
            
            // 可选：清除预览框
            // 注释：若希望拖拽结束后保持预览框显示，可注释掉下面的代码
            // 当前实现：保持预览框显示，因为 SelectionManager 已自动更新了预览框
            // 如需清除，可取消注释：
            // try {
            //     com.director.client.renderer.SelectionRenderer.getInstance().clearPreview();
            // } catch (Exception e) {
            //     // 若 SelectionRenderer 不可用，忽略错误
            // }
        }
        
        // 清除悬停状态，强制重新检测（鼠标可能已经不在手柄上了）
        hoveredHandle = null;
    }
    
    /**
     * 应用变换（根据 Handle 类型）。
     * <p>使用 TransformApplier 将变换应用到选区或动画目标。
     */
    private void applyTransform(Vec3d delta) {
        if (transformApplier == null) {
            // 回退到旧方法（直接修改 Box）
            applyTransformLegacy(delta);
            return;
        }

        // BOUNDS Gizmo：轴/平面/中心手柄移动"包围盒预览"，不平移 selectionSet
        if (context != null && context.getType() == GizmoType.BOUNDS && isMoveHandle(activeHandle)) {
            applyBoundsMove(delta);
            return;
        }
        
        // 使用 TransformApplier 应用变换
        if (activeHandle instanceof com.director.client.gizmo.handles.ResizeHandle resizeHandle) {
            // ResizeHandle：按具体面向应用缩放
            Vec3d axisDelta = resizeHandle.getAxisDelta(delta);
            Axis[] axes = resizeHandle.getAxes();
            if (resizeHandle.getHandleType() == com.director.client.gizmo.handles.ResizeHandle.Type.FACE && axes.length == 1) {
                double signedDelta = switch (axes[0]) {
                    case X -> axisDelta.x;
                    case Y -> axisDelta.y;
                    case Z -> axisDelta.z;
                };
                transformApplier.applyResize(axes[0], resizeHandle.isMaxFace(), signedDelta);
            } else {
                transformApplier.applyResize(axisDelta);
            }
        } else if (activeHandle instanceof com.director.client.gizmo.handles.AxisHandle axisHandle) {
            if (context != null && context.getType() == GizmoType.TRANSFORM && currentMode() == GizmoMode.SCALE) {
                Vec3d axisDelta = axisDelta(axisHandle.getAxis(), delta.dotProduct(axisDirection(axisHandle.getAxis())));
                transformApplier.applyResize(axisDelta);
            } else {
                transformApplier.applyMove(delta);
            }
        } else if (activeHandle instanceof com.director.client.gizmo.handles.PlaneHandle) {
            if (context != null && context.getType() == GizmoType.TRANSFORM && currentMode() == GizmoMode.SCALE) {
                transformApplier.applyResize(delta);
            } else {
                transformApplier.applyMove(delta);
            }
        } else if (activeHandle instanceof com.director.client.gizmo.handles.CornerHandle corner) {
            // 角点手柄：从角点调整选区大小
            transformApplier.applyResizeFromCorner(delta, corner.getCornerSign());
        } else if (activeHandle instanceof com.director.client.gizmo.handles.CenterHandle) {
            transformApplier.applyMove(delta);
            // TRANSFORM（演员编辑）的 pivot 由 ActorGizmoController 跟随实体更新，勿在此直接改 pivot
            if (context == null || context.getType() != GizmoType.TRANSFORM) {
                Vec3d newPivot = null;
                if (context != null) {
                    newPivot = context.getPivot().add(delta);
                }
                if (context != null) {
                    context.setPivot(newPivot);
                }
            }
        }
        
        // TRANSFORM Gizmo（Actor 编辑）不应从方块选区更新 Box
        // 更新 Box（从 SelectionManager 同步）
        if (context == null || context.getType() != GizmoType.TRANSFORM) {
            updateBoxFromSelection();
        }
    }

    private boolean isMoveHandle(GizmoHandle handle) {
        return handle instanceof com.director.client.gizmo.handles.AxisHandle
            || handle instanceof com.director.client.gizmo.handles.PlaneHandle
            || handle instanceof com.director.client.gizmo.handles.CenterHandle;
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

        // 只更新"包围盒预览"，重建选区在松手时做
        selectionManager.setBoundsGizmoMovePreviewBounds(moved);
    }
    
    /** 旧版变换应用方法（直接修改 Box）。 */
    private void applyTransformLegacy(Vec3d delta) {
        Box currentBox = context.getTargetBox();
        Box newBox = currentBox;
        
        if (activeHandle instanceof com.director.client.gizmo.handles.AxisHandle) {
            newBox = com.director.client.gizmo.util.TransformUtil.translateBox(currentBox, delta);
        } else if (activeHandle instanceof com.director.client.gizmo.handles.PlaneHandle) {
            newBox = com.director.client.gizmo.util.TransformUtil.translateBox(currentBox, delta);
        } else if (activeHandle instanceof com.director.client.gizmo.handles.CornerHandle corner) {
            newBox = com.director.client.gizmo.util.TransformUtil.resizeBoxFromCorner(
                currentBox, delta, corner.getCornerSign()
            );
        } else if (activeHandle instanceof com.director.client.gizmo.handles.CenterHandle) {
            Vec3d newPivot = context.getPivot().add(delta);
            context.setPivot(newPivot);
            newBox = com.director.client.gizmo.util.TransformUtil.translateBox(currentBox, delta);
        }
        
        context.setTargetBox(newBox);
    }
    
    /** 从 SelectionManager 同步 Box。 */
    private void updateBoxFromSelection() {
        var selectionManager = DirectorServices.get().selection();
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

        // BOUNDS Gizmo：pivot 永远是包围盒几何中心
        if (context != null && context.getType() == GizmoType.BOUNDS) {
            Vec3d center = new Vec3d(
                (newBox.minX + newBox.maxX) / 2.0,
                (newBox.minY + newBox.maxY) / 2.0,
                (newBox.minZ + newBox.maxZ) / 2.0
            );
            context.setPivot(center);
        }
    }

    private static Vec3d axisDirection(Axis axis) {
        return switch (axis) {
            case X -> new Vec3d(1, 0, 0);
            case Y -> new Vec3d(0, 1, 0);
            case Z -> new Vec3d(0, 0, 1);
        };
    }

    private static Vec3d axisDelta(Axis axis, double scalar) {
        return switch (axis) {
            case X -> new Vec3d(scalar, 0.0, 0.0);
            case Y -> new Vec3d(0.0, scalar, 0.0);
            case Z -> new Vec3d(0.0, 0.0, scalar);
        };
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
    
    public void setResizeHandles(List<com.director.client.gizmo.handles.ResizeHandle> resizeHandles) {
        this.resizeHandles = new ArrayList<>(resizeHandles);
    }
    
    public List<com.director.client.gizmo.handles.ResizeHandle> getResizeHandles() {
        return new ArrayList<>(resizeHandles);
    }
}

