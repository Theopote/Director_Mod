package com.chronoblocks.client.renderer;

import com.chronoblocks.core.selection.SelectionManager;
import com.chronoblocks.core.selection.SelectionSet;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.client.render.VertexConsumer;

import org.joml.Matrix4f;

/**
 * 🖍 SelectionOverlayRenderer
 * 
 * 在世界中渲染：
 * - 选中方块轮廓（线框）
 * - 整体包围盒（透明轮廓）
 * - 中心锚点（十字或小立方体）
 * 
 * ⚙️ 性能：
 * - 渲染节流：仅在选区变化后一定时间内全速刷新
 * - 顶点批处理：使用 DebugRenderLayer 的 Lines / Quad 渲染
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class SelectionOverlayRenderer {

    private static final SelectionOverlayRenderer INSTANCE = new SelectionOverlayRenderer();

    public static SelectionOverlayRenderer get() { 
        return INSTANCE; 
    }

    /** 渲染开关（Toolbar可切换） */
    private volatile boolean enabled = true;

    /** 线框颜色（RGBA） */
    private float lineR = 0.2f, lineG = 0.8f, lineB = 1.0f, lineA = 0.95f;

    /** BBox 颜色（RGBA） */
    private float bboxR = 0.2f, bboxG = 1.0f, bboxB = 0.4f, bboxA = 0.35f;

    /** 中心点颜色 */
    private float pivotR = 1.0f, pivotG = 0.9f, pivotB = 0.2f, pivotA = 0.95f;

    /** 渲染节流（纳秒） */
    private static final long THROTTLE_NS = 10_000_000L; // 10ms

    private SelectionOverlayRenderer() {}

    public boolean isEnabled() { 
        return enabled; 
    }
    
    public void setEnabled(boolean enabled) { 
        this.enabled = enabled; 
    }

    public void setLineColor(float r, float g, float b, float a) { 
        lineR=r; lineG=g; lineB=b; lineA=a; 
    }
    
    public void setBBoxColor(float r, float g, float b, float a) { 
        bboxR=r; bboxG=g; bboxB=b; bboxA=a; 
    }
    
    public void setPivotColor(float r, float g, float b, float a) { 
        pivotR=r; pivotG=g; pivotB=b; pivotA=a; 
    }

    /**
     * 渲染入口（桥接 WorldRenderEvents.AFTER_ENTITIES）
     */
    public void render(MatrixStack matrices, float tickDelta, Camera camera, VertexConsumerProvider vcp) {
        if (!enabled) return;

        SelectionSet set = SelectionManager.get().getSelectionSet();
        if (set == null || set.isEmpty()) return;

        // 简单节流：上次变化超过阈值则正常渲染
        long elapsed = System.nanoTime() - set.getLastChangeNanos();
        if (elapsed < THROTTLE_NS && MinecraftClient.getInstance().isPaused()) {
            return; // 暂停时进一步节流
        }

        // 相机偏移（视图空间）
        double camX = camera.getPos().x;
        double camY = camera.getPos().y;
        double camZ = camera.getPos().z;

        // 线框层（用于方块轮廓）
        VertexConsumer lineConsumer = vcp.getBuffer(RenderLayer.getLines());
        Matrix4f mat = matrices.peek().getPositionMatrix();

        // 1) 渲染每个方块的线框
        for (BlockPos p : set.asUnmodifiableSet()) {
            drawUnitBlockWireframe(mat, lineConsumer, p.getX() - camX, p.getY() - camY, p.getZ() - camZ, lineR, lineG, lineB, lineA);
        }

        // 2) 渲染整体包围盒
        Box bbox = set.getBoundingBox().offset(-camX, -camY, -camZ);
        // 使用简单的线框绘制包围盒
        drawBoxWireframe(mat, vcp.getBuffer(RenderLayer.getLines()), bbox, bboxR, bboxG, bboxB, bboxA);

        // 3) 渲染中心锚点十字
        BlockPos c = set.getCenterBlock();
        drawPivotCross(mat, lineConsumer, c.getX() + 0.5 - camX, c.getY() + 0.5 - camY, c.getZ() + 0.5 - camZ, pivotR, pivotG, pivotB, pivotA);
    }

    // ---------- 绘制工具 ----------

    private void drawUnitBlockWireframe(Matrix4f m, VertexConsumer consumer, double x, double y, double z,
                                        float r, float g, float b, float a) {
        // 用最小化版的 12 条边线（unit cube）
        line(consumer, m, x, y, z, x+1, y, z, r,g,b,a);
        line(consumer, m, x, y, z, x, y+1, z, r,g,b,a);
        line(consumer, m, x, y, z, x, y, z+1, r,g,b,a);

        line(consumer, m, x+1, y, z, x+1, y+1, z, r,g,b,a);
        line(consumer, m, x+1, y, z, x+1, y, z+1, r,g,b,a);

        line(consumer, m, x, y+1, z, x+1, y+1, z, r,g,b,a);
        line(consumer, m, x, y+1, z, x, y+1, z+1, r,g,b,a);

        line(consumer, m, x, y, z+1, x+1, y, z+1, r,g,b,a);
        line(consumer, m, x, y, z+1, x, y+1, z+1, r,g,b,a);

        line(consumer, m, x+1, y+1, z, x+1, y+1, z+1, r,g,b,a);
        line(consumer, m, x+1, y, z+1, x+1, y+1, z+1, r,g,b,a);
        line(consumer, m, x, y+1, z+1, x+1, y+1, z+1, r,g,b,a);
    }

    private void drawPivotCross(Matrix4f m, VertexConsumer c, double x, double y, double z,
                                float r, float g, float b, float a) {
        double s = 0.35;
        line(c, m, x - s, y, z, x + s, y, z, r,g,b,a);
        line(c, m, x, y - s, z, x, y + s, z, r,g,b,a);
        line(c, m, x, y, z - s, x, y, z + s, r,g,b,a);
    }

    private void drawBoxWireframe(Matrix4f m, VertexConsumer consumer, Box box, float r, float g, float b, float a) {
        double minX = box.minX, minY = box.minY, minZ = box.minZ;
        double maxX = box.maxX, maxY = box.maxY, maxZ = box.maxZ;
        
        // 绘制包围盒的12条边
        line(consumer, m, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        line(consumer, m, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        line(consumer, m, minX, minY, minZ, minX, minY, maxZ, r, g, b, a);
        
        line(consumer, m, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        line(consumer, m, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        
        line(consumer, m, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        line(consumer, m, minX, maxY, minZ, minX, maxY, maxZ, r, g, b, a);
        
        line(consumer, m, minX, minY, maxZ, maxX, minY, maxZ, r, g, b, a);
        line(consumer, m, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
        
        line(consumer, m, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        line(consumer, m, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        line(consumer, m, minX, maxY, maxZ, maxX, maxY, maxZ, r, g, b, a);
    }

    private void line(VertexConsumer c, Matrix4f m, double x1, double y1, double z1, double x2, double y2, double z2,
                      float r, float g, float b, float a) {
        c.vertex(m, (float)x1, (float)y1, (float)z1).color(r,g,b,a).normal(0, 1, 0);
        c.vertex(m, (float)x2, (float)y2, (float)z2).color(r,g,b,a).normal(0, 1, 0);
    }
}
