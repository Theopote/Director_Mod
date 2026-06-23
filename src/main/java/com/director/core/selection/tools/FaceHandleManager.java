package com.director.core.selection.tools;

import com.director.client.gizmo.Axis;
import com.director.client.input.DirectorInputSystem;
import com.director.client.renderer.OverlayRenderHelper;
import com.director.core.DirectorServices;
import com.director.core.selection.SelectionManager;
import com.director.ui.cursor.ImGuiCursorController;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 面手柄管理器：屏幕空间拾取（穿透遮挡）、沿法线拖拽缩放、与 SelectionManager 预览/应用集成。
 */
public class FaceHandleManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(FaceHandleManager.class);

    /** 全局共享实例：用于所有选择工具的包围盒面手柄交互状态。 */
    public static final FaceHandleManager SHARED = new FaceHandleManager();

    /** 与 {@link OverlayRenderHelper} 视觉手柄对齐，略放大便于点击。 */
    private static final double HANDLE_PICK_HALF = OverlayRenderHelper.HANDLE_PICK_HALF_SIZE;
    private static final double PARALLEL_EPSILON = 1e-6;
    /** 屏幕空间悬停半径下限/上限（帧缓冲像素），仅鼠标靠近手柄中心时才显示双向箭头。 */
    private static final double SCREEN_HOVER_RADIUS_MIN_PX = 10.0;
    private static final double SCREEN_HOVER_RADIUS_MAX_PX = 28.0;
    private static final double SCREEN_HOVER_RADIUS_SCALE = 1.1;

    private Integer hoveredFaceIndex;
    private Integer draggingFaceIndex;

    private Box dragStartBox;
    private Vec3d dragAxisOrigin;
    private Vec3d dragAxisDir;
    private double liveOffset;
    private boolean hoveringLastFrame;

    public Integer detectHover(Box box, Vec3d rayOrigin, Vec3d rayDir) {
        if (box == null) {
            if (!isDragging()) {
                clearHover();
            }
            return hoveredFaceIndex;
        }

        if (isDragging()) {
            return draggingFaceIndex;
        }

        DirectorInputSystem.FramebufferPoint mouse = DirectorInputSystem.getMouseFramebufferPosition();
        if (mouse == null) {
            clearHover();
            return null;
        }

        Integer closestFace = null;
        double closestScreenDistSq = Double.POSITIVE_INFINITY;
        double closestDepth = Double.POSITIVE_INFINITY;

        for (int face = 0; face < 6; face++) {
            Vec3d center = OverlayRenderHelper.faceCenter(box, face);
            DirectorInputSystem.FramebufferPoint projected = DirectorInputSystem.projectWorldToFramebuffer(center);
            if (projected == null) {
                continue;
            }

            double radiusPx = computeScreenHoverRadius(face, center, projected);
            double dx = mouse.x() - projected.x();
            double dy = mouse.y() - projected.y();
            double screenDistSq = dx * dx + dy * dy;
            if (screenDistSq > radiusPx * radiusPx) {
                continue;
            }

            double depth = rayOrigin != null ? rayOrigin.distanceTo(center) : 0.0;
            if (screenDistSq < closestScreenDistSq - 0.25
                    || (Math.abs(screenDistSq - closestScreenDistSq) <= 0.25 && depth < closestDepth)) {
                closestScreenDistSq = screenDistSq;
                closestDepth = depth;
                closestFace = face;
            }
        }

        if (closestFace != null) {
            if (!closestFace.equals(hoveredFaceIndex) || !hoveringLastFrame) {
                LOGGER.debug("[FaceHandleManager] hover face {} (screen pick)", closestFace);
            }
            hoveredFaceIndex = closestFace;
            hoveringLastFrame = true;
        } else {
            clearHover();
        }

        return hoveredFaceIndex;
    }

    public boolean startDrag(Box box, Vec3d rayOrigin, Vec3d rayDir) {
        if (box == null || hoveredFaceIndex == null || rayOrigin == null || rayDir == null || rayDir.lengthSquared() == 0) {
            return false;
        }

        draggingFaceIndex = hoveredFaceIndex;
        dragStartBox = copyBox(box);
        // 与 adjustBounds 正 offset 语义对齐，避免在 updateDrag 中再取反
        dragAxisDir = OverlayRenderHelper.faceNormal(draggingFaceIndex).negate();
        liveOffset = 0.0;

        Vec3d dir = rayDir.normalize();
        Vec3d center = OverlayRenderHelper.faceCenter(dragStartBox, draggingFaceIndex);
        Vec3d planeNormal = OverlayRenderHelper.faceNormal(draggingFaceIndex);
        Vec3d hit = intersectRayWithPlane(rayOrigin, dir, center, planeNormal);
        dragAxisOrigin = hit != null ? hit : center;

        SelectionManager manager = DirectorServices.get().selection();
        if (manager != null) {
            manager.beginFaceResize(dragStartBox, draggingFaceIndex);
        }

        LOGGER.debug("[FaceHandleManager] 开始拖拽面 {}", draggingFaceIndex);
        return true;
    }

    public void updateDrag(Vec3d rayOrigin, Vec3d rayDir) {
        if (!isDragging() || rayOrigin == null || rayDir == null || rayDir.lengthSquared() == 0) {
            return;
        }

        Vec3d dir = rayDir.normalize();
        double offset = resolveOffset(rayOrigin, dir);
        if (Double.isNaN(offset) || Double.isInfinite(offset)) {
            return;
        }

        liveOffset = offset;

        SelectionManager manager = DirectorServices.get().selection();
        if (manager != null) {
            manager.previewFaceResize(draggingFaceIndex, liveOffset);
        }
    }

    public void endDrag() {
        if (draggingFaceIndex == null) {
            return;
        }

        SelectionManager manager = DirectorServices.get().selection();
        if (manager != null) {
            manager.applyFaceResize(draggingFaceIndex, liveOffset);
        }

        LOGGER.debug("[FaceHandleManager] 拖拽结束 face={} offset={}", draggingFaceIndex, liveOffset);

        draggingFaceIndex = null;
        dragStartBox = null;
        dragAxisOrigin = null;
        dragAxisDir = null;
        liveOffset = 0.0;
        clearHover();
    }

    /**
     * 取消拖拽（不应用修改）。用于用户关闭包围盒显示、或交互中断时的兜底。
     */
    public void cancelDrag() {
        if (draggingFaceIndex == null) {
            clearHover();
            return;
        }
        SelectionManager manager = DirectorServices.get().selection();
        if (manager != null) {
            manager.cancelFaceResize();
        }
        draggingFaceIndex = null;
        dragStartBox = null;
        dragAxisOrigin = null;
        dragAxisDir = null;
        liveOffset = 0.0;
        clearHover();
    }

    /**
     * 每帧驱动悬停/拖拽更新；鼠标释放时结束拖拽。
     *
     * @return 是否应显示沿轴 resize 光标
     */
    public boolean tickInteraction(Box bounds, Vec3d rayOrigin, Vec3d rayDir, boolean bboxVisible, boolean mouseLeftDown) {
        if (!bboxVisible) {
            if (isDragging()) {
                cancelDrag();
            } else {
                detectHover(null, null, null);
            }
            return false;
        }

        if (isDragging()) {
            if (rayOrigin != null && rayDir != null) {
                updateDrag(rayOrigin, rayDir);
            }
            if (!mouseLeftDown) {
                endDrag();
                if (bounds != null && rayOrigin != null && rayDir != null) {
                    detectHover(bounds, rayOrigin, rayDir);
                }
                return getHoveredFaceIndex() != null;
            }
            return true;
        }

        if (bounds == null) {
            detectHover(null, null, null);
            return false;
        }

        detectHover(bounds, rayOrigin, rayDir);
        return getHoveredFaceIndex() != null;
    }

    /** 根据当前悬停/拖拽面同步 ImGui resize 光标；仅悬停且鼠标靠近手柄时切换。 */
    public void syncCursor(boolean bboxVisible) {
        ImGuiCursorController cursor = ImGuiCursorController.get();
        if (!bboxVisible) {
            cursor.setCursorType(ImGuiCursorController.CursorType.DEFAULT);
            return;
        }

        if (isDragging()) {
            cursor.setCursorByAxis(getFaceAxis(draggingFaceIndex));
            return;
        }

        if (hoveredFaceIndex == null) {
            cursor.setCursorType(ImGuiCursorController.CursorType.DEFAULT);
            return;
        }

        cursor.setCursorByAxis(getFaceAxis(hoveredFaceIndex));
    }

    private void clearHover() {
        if (hoveringLastFrame) {
            LOGGER.debug("[FaceHandleManager] hover cleared");
        }
        hoveringLastFrame = false;
        hoveredFaceIndex = null;
    }

    private static double computeScreenHoverRadius(int face, Vec3d center,
                                                   DirectorInputSystem.FramebufferPoint centerScreen) {
        Vec3d tangentOffset = faceTangentOffset(face);
        DirectorInputSystem.FramebufferPoint edge = DirectorInputSystem.projectWorldToFramebuffer(center.add(tangentOffset));
        double projectedHalf = SCREEN_HOVER_RADIUS_MIN_PX;
        if (edge != null) {
            double edx = edge.x() - centerScreen.x();
            double edy = edge.y() - centerScreen.y();
            projectedHalf = Math.sqrt(edx * edx + edy * edy);
        }
        return Math.max(SCREEN_HOVER_RADIUS_MIN_PX,
                Math.min(SCREEN_HOVER_RADIUS_MAX_PX, projectedHalf * SCREEN_HOVER_RADIUS_SCALE));
    }

    private static Vec3d faceTangentOffset(int face) {
        return switch (face) {
            case 0, 1 -> new Vec3d(0, FaceHandleManager.HANDLE_PICK_HALF, 0);
            case 2, 3, 4, 5 -> new Vec3d(FaceHandleManager.HANDLE_PICK_HALF, 0, 0);
            default -> Vec3d.ZERO;
        };
    }

    public static Axis faceIndexToAxis(int faceIndex) {
        return switch (faceIndex) {
            case 2, 3 -> Axis.Y;
            case 4, 5 -> Axis.Z;
            default -> Axis.X;
        };
    }

    private Box copyBox(Box original) {
        return original == null ? null : new Box(original.minX, original.minY, original.minZ,
                original.maxX, original.maxY, original.maxZ);
    }

    private Vec3d intersectRayWithPlane(Vec3d rayOrigin, Vec3d rayDir, Vec3d planePoint, Vec3d planeNormal) {
        double denom = planeNormal.dotProduct(rayDir);
        if (Math.abs(denom) < PARALLEL_EPSILON) {
            return null;
        }
        double t = planeNormal.dotProduct(planePoint.subtract(rayOrigin)) / denom;
        if (t < 0) {
            return null;
        }
        return rayOrigin.add(rayDir.multiply(t));
    }

    private double resolveOffset(Vec3d rayOrigin, Vec3d rayDir) {
        Vec3d w0 = rayOrigin.subtract(dragAxisOrigin);
        double b = dragAxisDir.dotProduct(rayDir);
        double d = dragAxisDir.dotProduct(w0);
        double e = rayDir.dotProduct(w0);
        double denom = 1.0 - b * b;
        if (Math.abs(denom) > PARALLEL_EPSILON) {
            return (b * e - d) / denom;
        }
        return -d;
    }

    public Integer getHoveredFaceIndex() {
        return hoveredFaceIndex;
    }

    public boolean isDragging() {
        return draggingFaceIndex != null;
    }

    public Integer getDraggingFaceIndex() {
        return draggingFaceIndex;
    }

    public static Axis getFaceAxis(int faceIndex) {
        return faceIndexToAxis(faceIndex);
    }
}
