package com.chronoblocks.client.renderer;

import com.chronoblocks.core.selection.SelectionManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import com.chronoblocks.client.settings.RenderSettings;

/**
 * 🟩 SelectionOverlayRenderer
 * <p>
 * ⭐ 重构后职责：只负责渲染附加的 UI 元素，不渲染选区几何体本身
 * <p>
 * 负责绘制选区的覆盖层元素，包括：
 *  - 选区中心点 / 锚点（enableAnchor）
 *  - 角点手柄（Ctrl 按下时显示，用于 Gizmo 编辑）
 * <p>
 * ⭐ 已移除的功能（避免与 SelectionRenderer 冲突）：
 *  - 方块线框（由 SelectionRenderer.renderOutlineOnly 负责）
 *  - 选区包围盒线框（由 SelectionRenderer.renderOutlineOnly 负责）
 * <p>
 * ✅ 特性：
 *  - 与 Fabric 1.21.x FrameGraph 完全兼容
 *  - 使用 VertexConsumerProvider（由 FrameGraph 自动管理）
 *  - 自动适配摄像机位置
 *  - 与 SelectionRenderer 职责分离，避免重复渲染和闪烁问题
 */
@Environment(EnvType.CLIENT)
public final class SelectionOverlayRenderer {

    private static SelectionOverlayRenderer INSTANCE;

    // 全局设置中心（新系统优先读取）
    private final RenderSettings settings = RenderSettings.get();

    private SelectionOverlayRenderer() {}

    public static SelectionOverlayRenderer get() { 
        if (INSTANCE == null)
            INSTANCE = new SelectionOverlayRenderer();
        return INSTANCE; 
    }

    /**
     * 主渲染入口。
     *
     * @param matrices MatrixStack
     * @param tickDelta 当前 tick 差值
     * @param camera 摄像机
     * @param consumers VertexConsumerProvider（由 WorldRenderContext.consumers() 提供，FrameGraph 自动管理）
     */
    public void render(MatrixStack matrices, float tickDelta, Camera camera, VertexConsumerProvider consumers) {
        // ✅ 选区交互元素（Gizmo、面中心小方块）不受 enabled 控制
        // enabled 只用于控制其他覆盖层元素，交互元素始终显示
        // if (!enabled) return; // 注释掉，确保交互元素始终显示

        MinecraftClient client = MinecraftClient.getInstance();
        World world = client.world;
        if (world == null) return;

        var manager = SelectionManager.get();
        if (manager == null || !manager.hasCurrentTool()) return;

        var selection = manager.getCurrentSelection();
        if (selection == null || selection.isEmpty()) return;

        Vec3d camPos = camera.getPos();
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        Box baseBox = manager.getCurrentBounds();
        if (baseBox == null) {
            return;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // ✅ 直接从全局设置读取线宽（实时生效）
        float lw = settings.tool_outlineWidth;
        RenderSystem.lineWidth(Math.max(1.0f, lw));

        // === 绘制中心锚点 ===
        // ✅ 直接从全局设置读取颜色（实时生效）
        float[] anchorColor = settings.tool_outlineColor;
        selection.getBoundingBox().ifPresent(bbox -> {
            Vec3d anchor = new Vec3d(
                (bbox.getMinX() + bbox.getMaxX() + 1) / 2.0,
                (bbox.getMinY() + bbox.getMaxY() + 1) / 2.0,
                (bbox.getMinZ() + bbox.getMaxZ() + 1) / 2.0
            );
            drawAnchor(matrix, consumers.getBuffer(RenderLayer.getLines()), anchor.subtract(camPos), anchorColor);
        });

        RenderSystem.disableBlend();
    }

    // -------------------------------------------------------------
    // 🔹 辅助方法：绘制线条
    // -------------------------------------------------------------

    private void line(VertexConsumer c, Matrix4f matrix,
                      double x1, double y1, double z1,
                      double x2, double y2, double z2,
                      float[] col) {
        c.vertex(matrix, (float)x1, (float)y1, (float)z1).color(col[0], col[1], col[2], col[3]).normal(0, 1, 0);
        c.vertex(matrix, (float)x2, (float)y2, (float)z2).color(col[0], col[1], col[2], col[3]).normal(0, 1, 0);
    }

    // -------------------------------------------------------------
    // 🔹 绘制中心锚点
    // -------------------------------------------------------------

    private void drawAnchor(Matrix4f matrix, VertexConsumer c, Vec3d pos, float[] color) {
        double size = 0.05;
        double x = pos.x, y = pos.y, z = pos.z;

        line(c, matrix, x - size, y, z, x + size, y, z, color);
        line(c, matrix, x, y - size, z, x, y + size, z, color);
        line(c, matrix, x, y, z - size, x, y, z + size, color);
    }

}