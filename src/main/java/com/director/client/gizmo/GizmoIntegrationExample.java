package com.director.client.gizmo;

import com.director.core.DirectorServices;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import com.director.core.selection.SelectionManager;
import com.director.core.selection.SelectionState;
import com.director.client.gizmo.util.GizmoVoxelizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * 🧭 GizmoIntegrationExample - Gizmo 集成示例
 * 
 * 展示如何从 BoxSelectTool 或其他工具激活 Gizmo，以及如何在主循环中使用
 * 
 * @author Director Team
 * @version 1.0
 */
public class GizmoIntegrationExample {
    private static final Logger LOGGER = LoggerFactory.getLogger(GizmoIntegrationExample.class);
    
    /**
     * 从 BoxSelectTool 激活 Gizmo 的示例
     * 
     * 在 BoxSelectTool.onDragEnd(...) 或建立选区后调用：
     */
    public static void exampleActivateFromBoxSelectTool(Box selectionBox) {
        GizmoManager gm = GizmoManager.get();
        
        // 计算轴心点（使用 Box 中心）
        Vec3d pivot = new Vec3d(
            (selectionBox.minX + selectionBox.maxX) / 2.0,
            (selectionBox.minY + selectionBox.maxY) / 2.0,
            (selectionBox.minZ + selectionBox.maxZ) / 2.0
        );
        
        // 激活 BOUNDS 类型的 Gizmo
        gm.activate(GizmoType.BOUNDS, selectionBox, pivot);
        
        // 设置自定义应用回调（可选）
        gm.setOnApply(ctx -> {
            // 应用时，将 ctx.targetBox 写回 SelectionManager 或触发关键帧
            SelectionManager sm = DirectorServices.get().selection();
            Set<net.minecraft.util.math.BlockPos> vox = GizmoVoxelizer.voxelize(ctx.getTargetBox());
            sm.clearSelection();
            sm.getSelectionSet().addAll(vox);
            
            LOGGER.info("[GizmoIntegration] 已应用 Gizmo 变换：{} 个方块", vox.size());
        });
    }
    
    /**
     * 从 SelectionState 激活 Gizmo 的示例
     */
    public static void exampleActivateFromSelectionState(SelectionState state) {
        if (state == null || state.isEmpty()) {
            return;
        }
        
        // 获取边界框
        var bboxOpt = state.getBoundingBox();
        if (bboxOpt.isEmpty()) {
            return;
        }
        
        var bbox = bboxOpt.get();
        Box box = new Box(
            bbox.getMinX(), bbox.getMinY(), bbox.getMinZ(),
            bbox.getMaxX() + 1, bbox.getMaxY() + 1, bbox.getMaxZ() + 1
        );
        
        // 激活 Gizmo
        GizmoManager gm = GizmoManager.get();
        Vec3d pivot = new Vec3d(
            (bbox.getMinX() + bbox.getMaxX() + 1) / 2.0,
            (bbox.getMinY() + bbox.getMaxY() + 1) / 2.0,
            (bbox.getMinZ() + bbox.getMaxZ() + 1) / 2.0
        );
        
        gm.activate(GizmoType.BOUNDS, box, pivot);
    }
    
    /**
     * 在主渲染/输入循环中使用 Gizmo 的示例
     * 
     * 这应该在 DirectorScreen 的渲染/输入循环中调用
     */
    public static void exampleGameLoopTickAndRender(
        Vec3d rayOrigin, Vec3d rayDir, 
        boolean mouseDown, boolean mouseReleased,
        Vec3d cameraPos, 
        double time,
        net.minecraft.client.util.math.MatrixStack matrices,
        net.minecraft.client.render.VertexConsumerProvider vcp
    ) {
        GizmoManager gm = GizmoManager.get();
        
        // 更新 Gizmo（每帧调用）
        gm.tick(rayOrigin, rayDir, mouseDown, mouseReleased);
        
        // 渲染 Gizmo（在 AFTER_ENTITIES 或 AFTER_TRANSLUCENT 阶段调用）
        gm.render(time, cameraPos, matrices, vcp);
    }
    
    /**
     * 停用 Gizmo 的示例
     */
    public static void exampleDeactivateGizmo() {
        GizmoManager gm = GizmoManager.get();
        if (gm.isActive()) {
            gm.deactivate();
        }
    }
}

