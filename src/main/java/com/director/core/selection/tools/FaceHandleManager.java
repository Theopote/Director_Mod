package com.director.core.selection.tools;

import com.director.client.gizmo.Axis;
import com.director.core.selection.SelectionManager;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * v4.9 面手柄管理器：
 * <ul>
 *     <li>基于射线-AABB 的稳定拾取</li>
 *     <li>沿面法线的平滑拖拽，提供实时预览</li>
 *     <li>与 SelectionManager 预览/应用接口集成</li>
 * </ul>
 */
public class FaceHandleManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(FaceHandleManager.class);

    /**
     * 全局共享实例：用于所有选择工具的包围盒面手柄交互状态。
     */
    public static final FaceHandleManager SHARED = new FaceHandleManager();

    // 拾取半径：支持“按距离自适应”，避免远处/小目标难以命中
    private static final double HANDLE_HALF_SIZE_MIN = 0.25;
    private static final double HANDLE_HALF_SIZE_MAX = 0.65;
    private static final double HANDLE_HALF_SIZE_DISTANCE_SCALE = 0.02; // halfSize ≈ distance * scale
    private static final double PARALLEL_EPSILON = 1e-6;

    private Integer hoveredFaceIndex;
    private Integer draggingFaceIndex;

    private Box dragStartBox;
    private Vec3d dragAxisOrigin;
    private Vec3d dragAxisDir;
    private double liveOffset;
    private boolean hoveringLastFrame;

    public Integer detectHover(Box box, Vec3d rayOrigin, Vec3d rayDir) {
        if (box == null || rayOrigin == null || rayDir == null || rayDir.lengthSquared() == 0) {
            // 拖拽中不清除 hover，避免拖拽中断
            if (!isDragging()) {
                clearHover();
            }
            return hoveredFaceIndex;
        }

        // 拖拽中跳过 hover 检测，保持当前拖拽面
        if (isDragging()) {
            return draggingFaceIndex;
        }

        Vec3d dir = rayDir.normalize();
        Integer closestFace = null;
        double closestT = Double.POSITIVE_INFINITY;

        for (int face = 0; face < 6; face++) {
            Vec3d center = faceCenter(box, face);
            Double t = rayIntersectAABB(rayOrigin, dir, createHandleBox(center, rayOrigin));
            if (t != null && t > 0 && t < closestT) {
                closestT = t;
                closestFace = face;
            }
        }

        if (closestFace != null) {
            if (!closestFace.equals(hoveredFaceIndex) || !hoveringLastFrame) {
                LOGGER.debug("[FaceHandleManager] hover face {}", closestFace);
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
        dragAxisDir = faceNormal(draggingFaceIndex);
        liveOffset = 0.0;

        Vec3d dir = rayDir.normalize();
        Vec3d center = faceCenter(dragStartBox, draggingFaceIndex);
        Vec3d hit = intersectRayWithPlane(rayOrigin, dir, center, dragAxisDir);
        dragAxisOrigin = hit != null ? hit : center;

        SelectionManager manager = SelectionManager.get();
        if (manager != null) {
            manager.beginFaceResize(dragStartBox, draggingFaceIndex);
        }

        LOGGER.info("[FaceHandleManager] 开始拖拽面 {}", draggingFaceIndex);
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

        // ✅ 修正方向：当前解算得到的 offset 与“面法线正方向”相反，统一取反
        liveOffset = -offset;

        SelectionManager manager = SelectionManager.get();
        if (manager != null) {
            manager.previewFaceResize(draggingFaceIndex, liveOffset);
        }
    }

    public void endDrag() {
        if (draggingFaceIndex == null) {
            return;
        }

        SelectionManager manager = SelectionManager.get();
        if (manager != null) {
            manager.applyFaceResize(draggingFaceIndex, liveOffset);
        }

        LOGGER.info("[FaceHandleManager] 拖拽结束 face={} offset={}", draggingFaceIndex, liveOffset);

        draggingFaceIndex = null;
        dragStartBox = null;
        dragAxisOrigin = null;
        dragAxisDir = null;
        liveOffset = 0.0;
        
        // 清除悬停状态，强制重新检测（鼠标可能已经不在小方块上了）
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
        SelectionManager manager = SelectionManager.get();
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

    private void clearHover() {
        if (hoveringLastFrame) {
            LOGGER.debug("[FaceHandleManager] hover cleared");
        }
        hoveringLastFrame = false;
        hoveredFaceIndex = null;
    }

    private Vec3d faceCenter(Box box, int faceIndex) {
        double cx = (box.minX + box.maxX) * 0.5;
        double cy = (box.minY + box.maxY) * 0.5;
        double cz = (box.minZ + box.maxZ) * 0.5;

        return switch (faceIndex) {
            case 0 -> new Vec3d(box.maxX, cy, cz);
            case 1 -> new Vec3d(box.minX, cy, cz);
            case 2 -> new Vec3d(cx, box.maxY, cz);
            case 3 -> new Vec3d(cx, box.minY, cz);
            case 4 -> new Vec3d(cx, cy, box.maxZ);
            case 5 -> new Vec3d(cx, cy, box.minZ);
            default -> new Vec3d(cx, cy, cz);
        };
    }

    private Vec3d faceNormal(int faceIndex) {
        return switch (faceIndex) {
            case 1 -> new Vec3d(-1, 0, 0);
            case 2 -> new Vec3d(0, 1, 0);
            case 3 -> new Vec3d(0, -1, 0);
            case 4 -> new Vec3d(0, 0, 1);
            case 5 -> new Vec3d(0, 0, -1);
            default -> new Vec3d(1, 0, 0);
        };
    }

    public static Axis faceIndexToAxis(int faceIndex) {
        return switch (faceIndex) {
            case 2, 3 -> Axis.Y;
            case 4, 5 -> Axis.Z;
            default -> Axis.X;
        };
    }

    private Box createHandleBox(Vec3d center, Vec3d rayOrigin) {
        double half = HANDLE_HALF_SIZE_MIN;
        if (rayOrigin != null) {
            double dist = center.distanceTo(rayOrigin);
            half = Math.max(HANDLE_HALF_SIZE_MIN,
                    Math.min(HANDLE_HALF_SIZE_MAX, dist * HANDLE_HALF_SIZE_DISTANCE_SCALE));
        }
        return new Box(center.x - half, center.y - half, center.z - half,
                center.x + half, center.y + half, center.z + half);
    }

    private Box copyBox(Box original) {
        return original == null ? null : new Box(original.minX, original.minY, original.minZ,
                original.maxX, original.maxY, original.maxZ);
    }

    private Double rayIntersectAABB(Vec3d origin, Vec3d dir, Box box) {
        double tmin = Double.NEGATIVE_INFINITY;
        double tmax = Double.POSITIVE_INFINITY;

        if (Math.abs(dir.x) > PARALLEL_EPSILON) {
            double tx1 = (box.minX - origin.x) / dir.x;
            double tx2 = (box.maxX - origin.x) / dir.x;
            double txMin = Math.min(tx1, tx2);
            double txMax = Math.max(tx1, tx2);
            tmin = Math.max(tmin, txMin);
            tmax = Math.min(tmax, txMax);
        } else if (origin.x < box.minX || origin.x > box.maxX) {
            return null;
        }

        if (Math.abs(dir.y) > PARALLEL_EPSILON) {
            double ty1 = (box.minY - origin.y) / dir.y;
            double ty2 = (box.maxY - origin.y) / dir.y;
            double tyMin = Math.min(ty1, ty2);
            double tyMax = Math.max(ty1, ty2);
            tmin = Math.max(tmin, tyMin);
            tmax = Math.min(tmax, tyMax);
        } else if (origin.y < box.minY || origin.y > box.maxY) {
            return null;
        }

        if (Math.abs(dir.z) > PARALLEL_EPSILON) {
            double tz1 = (box.minZ - origin.z) / dir.z;
            double tz2 = (box.maxZ - origin.z) / dir.z;
            double tzMin = Math.min(tz1, tz2);
            double tzMax = Math.max(tz1, tz2);
            tmin = Math.max(tmin, tzMin);
            tmax = Math.min(tmax, tzMax);
        } else if (origin.z < box.minZ || origin.z > box.maxZ) {
            return null;
        }

        if (tmax < tmin || tmax < 0) {
            return null;
        }

        return (tmin >= 0) ? tmin : tmax;
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

    public double getLiveOffset() {
        return liveOffset;
    }

    public Box getDragStartBox() {
        return dragStartBox;
    }

    /**
     * 静态工具方法：根据面索引获取对应的轴方向
     */
    public static Axis getFaceAxis(int faceIndex) {
        return faceIndexToAxis(faceIndex);
    }
}