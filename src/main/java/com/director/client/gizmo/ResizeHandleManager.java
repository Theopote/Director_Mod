package com.director.client.gizmo;

import com.director.client.renderer.OverlayRenderHelper;
import com.director.core.selection.SelectionManager;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 🧱 ResizeHandleManager v1.0
 *
 * 用于 GizmoRenderer 层的「面 / 角点」拖拽行为管理。
 *
 * ✅ 主要功能：
 * - 统一管理 6 个面手柄 + 8 个角点手柄
 * - 支持单轴（面）与多轴（角点）缩放
 * - 自动调用 SelectionManager.expandSelection(axis, delta)
 * - 实时预览刷新（通过 SelectionRenderer）
 * - 当前选中手柄高亮渲染
 *
 * 🔧 工作流：
 * 1️⃣ GizmoRenderer 调用 renderHandles() 绘制手柄
 * 2️⃣ onMouseDown() 检测命中哪个手柄（面 / 角）
 * 3️⃣ onMouseDrag() 计算 Δ 并驱动 expandSelection()
 * 4️⃣ onMouseUp() 清除预览与状态
 *
 * @author Director
 * @version 1.0
 */
public class ResizeHandleManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResizeHandleManager.class);
    
    // =====================================================
    // 📦 内部状态
    // =====================================================
    private boolean isDragging = false;
    private HandleType activeHandleType = null;
    private Set<Axis> activeAxes = new HashSet<>();
    private Vec3d dragStartPos = Vec3d.ZERO;
    private double lastWorldCoord = 0;
    
    // 手柄尺寸与颜色参数
    private static final double HANDLE_SIZE = 0.2;
    private static final float[] COLOR_FACE = {0.3f, 0.7f, 1.0f, 0.9f};      // 青色面手柄
    private static final float[] COLOR_CORNER = {1.0f, 0.8f, 0.3f, 0.9f};   // 黄色角点手柄
    private static final float[] COLOR_ACTIVE = {1.0f, 1.0f, 1.0f, 1.0f};   // 白色激活
    
    // =====================================================
    // 🔍 手柄类型定义
    // =====================================================
    public enum HandleType {
        // 面手柄（单轴）
        FACE_X_POS, FACE_X_NEG,
        FACE_Y_POS, FACE_Y_NEG,
        FACE_Z_POS, FACE_Z_NEG,
        // 角点手柄（多轴）
        CORNER_XYZ_PPP, CORNER_XYZ_PPN, CORNER_XYZ_PNP, CORNER_XYZ_PNN,
        CORNER_XYZ_NPP, CORNER_XYZ_NPN, CORNER_XYZ_NNP, CORNER_XYZ_NNN
    }
    
    // =====================================================
    // 🎨 绘制所有手柄
    // =====================================================
    /**
     * 渲染所有手柄（6 个面 + 8 个角点）
     * 
     * @param matrices MatrixStack
     * @param vcp VertexConsumerProvider
     * @param box 选区边界
     */
    public void renderHandles(MatrixStack matrices, VertexConsumerProvider vcp, Box box) {
        if (box == null) return;
        
        Vec3d center = new Vec3d(
            (box.minX + box.maxX) / 2.0,
            (box.minY + box.maxY) / 2.0,
            (box.minZ + box.maxZ) / 2.0
        );
        
        double halfX = (box.maxX - box.minX) / 2.0;
        double halfY = (box.maxY - box.minY) / 2.0;
        double halfZ = (box.maxZ - box.minZ) / 2.0;
        
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumer lineConsumer = vcp.getBuffer(net.minecraft.client.render.RenderLayer.getLines());
        // --- 6 个面手柄 ---
        renderHandle(lineConsumer, matrix, center.add(+halfX, 0, 0), HandleType.FACE_X_POS);
        renderHandle(lineConsumer, matrix, center.add(-halfX, 0, 0), HandleType.FACE_X_NEG);
        renderHandle(lineConsumer, matrix, center.add(0, +halfY, 0), HandleType.FACE_Y_POS);
        renderHandle(lineConsumer, matrix, center.add(0, -halfY, 0), HandleType.FACE_Y_NEG);
        renderHandle(lineConsumer, matrix, center.add(0, 0, +halfZ), HandleType.FACE_Z_POS);
        renderHandle(lineConsumer, matrix, center.add(0, 0, -halfZ), HandleType.FACE_Z_NEG);
        
        // --- 8 个角点手柄已移除，不再绘制 ---
        // 用户只需要面中心小方块来调整选区大小，不需要角点手柄
    }
    
    private void renderHandle(VertexConsumer consumer, Matrix4f matrix, Vec3d pos, HandleType type) {
        boolean isActive = (activeHandleType == type);
        float[] color = getHandleColor(type, isActive);
        OverlayRenderHelper.drawHandle(consumer, matrix, pos, color, isActive);
    }
    
    private float[] getHandleColor(HandleType type, boolean isActive) {
        if (isActive) {
            return COLOR_ACTIVE;
        }
        
        // 面手柄：青色，角点手柄：黄色
        if (type.name().startsWith("FACE")) {
            return COLOR_FACE;
        } else {
            return COLOR_CORNER;
        }
    }
    
    // =====================================================
    // 🖱️ 交互逻辑
    // =====================================================
    
    /**
     * 当鼠标按下时，检测命中手柄
     * 
     * @param rayOrigin 射线起点
     * @param rayDir 射线方向（归一化）
     * @param box 选区边界
     */
    public void onMouseDown(Vec3d rayOrigin, Vec3d rayDir, Box box) {
        if (box == null) return;
        
        HandleType type = detectHandle(rayOrigin, rayDir, box);
        if (type != null) {
            this.activeHandleType = type;
            this.activeAxes = mapHandleToAxes(type);
            this.isDragging = true;
            
            // 计算起始位置（手柄中心点投影到射线上）
            Vec3d handlePos = getHandlePosition(box, type);
            Vec3d toHandle = handlePos.subtract(rayOrigin);
            double t = toHandle.dotProduct(rayDir);
            this.dragStartPos = rayOrigin.add(rayDir.multiply(t));
            this.lastWorldCoord = getAxisCoord(dragStartPos, activeAxes.iterator().next());
            
            LOGGER.debug("[ResizeHandleManager] 选中手柄: {}, 轴: {}", type, activeAxes);
        }
    }
    
    /**
     * 鼠标拖拽中：实时更新
     * 
     * @param rayOrigin 射线起点
     * @param rayDir 射线方向（归一化）
     */
    public void onMouseDrag(Vec3d rayOrigin, Vec3d rayDir) {
        if (!isDragging || activeAxes.isEmpty()) return;
        
        // 计算当前命中点（投影到手柄平面）
        Vec3d handlePos = getHandlePositionFromBox();
        if (handlePos == null) return;
        
        // 计算当前坐标
        Axis primaryAxis = activeAxes.iterator().next();
        Vec3d currentHit = calculateDragPoint(rayOrigin, rayDir, handlePos, primaryAxis);
        double current = getAxisCoord(currentHit, primaryAxis);
        
        // 计算增量（四舍五入到整数）
        int delta = (int) Math.round(current - lastWorldCoord);
        
        if (delta != 0) {
            // 🧩 实时联动：调用 SelectionManager.expandSelection()
            // 对于多轴手柄，需要分别沿每个轴扩展
            for (Axis axis : activeAxes) {
                SelectionManager.get().expandSelection(axis, isMaxFaceHandle(activeHandleType, axis), delta);
            }
            
            lastWorldCoord = current;
            LOGGER.debug("[ResizeHandleManager] 拖拽 {} 轴 Δ={}", activeAxes, delta);
        }
    }
    
    /**
     * 鼠标释放：结束拖拽
     */
    public void onMouseUp() {
        if (!isDragging) return;
        
        LOGGER.debug("[ResizeHandleManager] 拖拽结束: {}", activeHandleType);
        
        isDragging = false;
        activeAxes.clear();
        activeHandleType = null;
        
        // 🧩 可选：清除预览框
        // 注释：当前实现保持预览框显示，因为选区已更新
        // 如果需要清除，可以取消注释：
        // SelectionRenderer.getInstance().clearPreview();
    }
    
    // =====================================================
    // 🔍 检测逻辑
    // =====================================================
    
    /**
     * 检测命中哪个手柄
     */
    private HandleType detectHandle(Vec3d rayOrigin, Vec3d rayDir, Box box) {
        Vec3d center = new Vec3d(
            (box.minX + box.maxX) / 2.0,
            (box.minY + box.maxY) / 2.0,
            (box.minZ + box.maxZ) / 2.0
        );
        
        double halfX = (box.maxX - box.minX) / 2.0;
        double halfY = (box.maxY - box.minY) / 2.0;
        double halfZ = (box.maxZ - box.minZ) / 2.0;
        
        HandleType closestHandle = null;
        double closestDistance = Double.POSITIVE_INFINITY;
        
        // 检测面手柄（6 个）
        HandleType[] faceHandles = {
            HandleType.FACE_X_POS, HandleType.FACE_X_NEG,
            HandleType.FACE_Y_POS, HandleType.FACE_Y_NEG,
            HandleType.FACE_Z_POS, HandleType.FACE_Z_NEG
        };
        
        Vec3d[] facePositions = {
            center.add(+halfX, 0, 0), center.add(-halfX, 0, 0),
            center.add(0, +halfY, 0), center.add(0, -halfY, 0),
            center.add(0, 0, +halfZ), center.add(0, 0, -halfZ)
        };
        
        for (int i = 0; i < faceHandles.length; i++) {
            double distance = com.director.client.gizmo.util.RaycastUtil.rayToSphereDistance(
                rayOrigin, rayDir, facePositions[i], HANDLE_SIZE);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestHandle = faceHandles[i];
            }
        }
        
        // 检测角点手柄（8 个）
        HandleType[] cornerHandles = {
            HandleType.CORNER_XYZ_PPP, HandleType.CORNER_XYZ_PPN,
            HandleType.CORNER_XYZ_PNP, HandleType.CORNER_XYZ_PNN,
            HandleType.CORNER_XYZ_NPP, HandleType.CORNER_XYZ_NPN,
            HandleType.CORNER_XYZ_NNP, HandleType.CORNER_XYZ_NNN
        };
        
        Vec3d[] cornerPositions = {
            center.add(+halfX, +halfY, +halfZ), center.add(+halfX, +halfY, -halfZ),
            center.add(+halfX, -halfY, +halfZ), center.add(+halfX, -halfY, -halfZ),
            center.add(-halfX, +halfY, +halfZ), center.add(-halfX, +halfY, -halfZ),
            center.add(-halfX, -halfY, +halfZ), center.add(-halfX, -halfY, -halfZ)
        };
        
        for (int i = 0; i < cornerHandles.length; i++) {
            double distance = com.director.client.gizmo.util.RaycastUtil.rayToSphereDistance(
                rayOrigin, rayDir, cornerPositions[i], HANDLE_SIZE);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestHandle = cornerHandles[i];
            }
        }
        
        return closestHandle;
    }
    
    /**
     * 将手柄类型映射为对应轴
     */
    private Set<Axis> mapHandleToAxes(HandleType type) {
        return switch (type) {
            case FACE_X_POS, FACE_X_NEG -> Set.of(Axis.X);
            case FACE_Y_POS, FACE_Y_NEG -> Set.of(Axis.Y);
            case FACE_Z_POS, FACE_Z_NEG -> Set.of(Axis.Z);
            case CORNER_XYZ_PPP, CORNER_XYZ_PPN, CORNER_XYZ_PNP, CORNER_XYZ_PNN,
                 CORNER_XYZ_NPP, CORNER_XYZ_NPN, CORNER_XYZ_NNP, CORNER_XYZ_NNN -> Set.of(Axis.X, Axis.Y, Axis.Z);
        };
    }

    private boolean isMaxFaceHandle(HandleType type, Axis axis) {
        if (type == null) {
            return true;
        }
        return switch (axis) {
            case X -> type != HandleType.FACE_X_NEG;
            case Y -> type != HandleType.FACE_Y_NEG;
            case Z -> type != HandleType.FACE_Z_NEG;
        };
    }
    
    /**
     * 获取手柄位置（从当前 Box）
     */
    private Vec3d getHandlePosition(Box box, HandleType type) {
        Vec3d center = new Vec3d(
            (box.minX + box.maxX) / 2.0,
            (box.minY + box.maxY) / 2.0,
            (box.minZ + box.maxZ) / 2.0
        );
        
        double halfX = (box.maxX - box.minX) / 2.0;
        double halfY = (box.maxY - box.minY) / 2.0;
        double halfZ = (box.maxZ - box.minZ) / 2.0;
        
        return switch (type) {
            case FACE_X_POS -> center.add(+halfX, 0, 0);
            case FACE_X_NEG -> center.add(-halfX, 0, 0);
            case FACE_Y_POS -> center.add(0, +halfY, 0);
            case FACE_Y_NEG -> center.add(0, -halfY, 0);
            case FACE_Z_POS -> center.add(0, 0, +halfZ);
            case FACE_Z_NEG -> center.add(0, 0, -halfZ);
            case CORNER_XYZ_PPP -> center.add(+halfX, +halfY, +halfZ);
            case CORNER_XYZ_PPN -> center.add(+halfX, +halfY, -halfZ);
            case CORNER_XYZ_PNP -> center.add(+halfX, -halfY, +halfZ);
            case CORNER_XYZ_PNN -> center.add(+halfX, -halfY, -halfZ);
            case CORNER_XYZ_NPP -> center.add(-halfX, +halfY, +halfZ);
            case CORNER_XYZ_NPN -> center.add(-halfX, +halfY, -halfZ);
            case CORNER_XYZ_NNP -> center.add(-halfX, -halfY, +halfZ);
            case CORNER_XYZ_NNN -> center.add(-halfX, -halfY, -halfZ);
        };
    }
    
    /**
     * 获取手柄位置（从当前上下文，简化实现）
     */
    private Vec3d getHandlePositionFromBox() {
        // 简化实现：从 SelectionManager 获取当前边界
        Box bounds = SelectionManager.get().getCurrentBounds();
        if (bounds == null || activeHandleType == null) {
            return null;
        }
        return getHandlePosition(bounds, activeHandleType);
    }
    
    /**
     * 计算拖拽点（投影到手柄平面）
     */
    private Vec3d calculateDragPoint(Vec3d rayOrigin, Vec3d rayDir, Vec3d handlePos, Axis axis) {
        // 对于面手柄，投影到垂直于轴的平面
        // 对于角点手柄，使用简化投影
        Vec3d normal = getAxisNormal(axis);
        Vec3d intersection = com.director.client.gizmo.util.RaycastUtil.rayToPlaneIntersection(
            rayOrigin, rayDir, handlePos, normal);
        
        if (intersection != null) {
            return intersection;
        }
        
        // 回退：使用固定距离投影
        return rayOrigin.add(rayDir.multiply(5.0));
    }
    
    /**
     * 获取轴法向量
     */
    private Vec3d getAxisNormal(Axis axis) {
        return switch (axis) {
            case X -> new Vec3d(1, 0, 0);
            case Y -> new Vec3d(0, 1, 0);
            case Z -> new Vec3d(0, 0, 1);
        };
    }
    
    /**
     * 获取点的轴坐标
     */
    private double getAxisCoord(Vec3d pos, Axis axis) {
        return switch (axis) {
            case X -> pos.x;
            case Y -> pos.y;
            case Z -> pos.z;
        };
    }
    
    // =====================================================
    // ⚙️ 状态 Getter
    // =====================================================
    
    public boolean isDragging() {
        return isDragging;
    }
    
    public HandleType getActiveHandleType() {
        return activeHandleType;
    }
    
    public Set<Axis> getActiveAxes() {
        return new HashSet<>(activeAxes);
    }
}

