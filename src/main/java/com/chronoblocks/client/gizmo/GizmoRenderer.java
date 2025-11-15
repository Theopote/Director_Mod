package com.chronoblocks.client.gizmo;

import com.chronoblocks.client.renderer.OverlayRenderHelper;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.*;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import com.chronoblocks.client.gizmo.handles.*;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 🧭 GizmoRenderer - Gizmo 渲染器
 *
 * 负责渲染箭头、平面、角点等 Gizmo 元素
 *
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class GizmoRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(GizmoRenderer.class);
    private final GizmoContext context;
    private final GizmoInteractionController controller;

    // ✅ v4.9: 渲染配置
    // 增大尺寸以提高可见性和易用性
    private static final double BASE_AXIS_LENGTH = 2.0;         // 基础轴线长度（增大到2.0）
    private static final double BASE_ARROW_HEIGHT = 0.35;       // 基础箭头高度（增大到0.35）
    private static final double BASE_ARROW_RADIUS = 0.15;       // 基础箭头底部半径（增大到0.15）
    private static final double BASE_PIVOT_SIZE = 0.12;         // 基础中心点立方体大小（增大到0.12）
    private static final double BASE_PLANE_SIZE = 0.8;          // 基础平面手柄大小（增大到0.8）
    private static final double BASE_CENTER_RADIUS = 0.25;      // 基础中心手柄大小（增大到0.25）
    private static final double MIN_DISTANCE_MULTIPLIER = 8.0;  // 最小距离乘数（调整为8.0）
    private static final double DISTANCE_SCALE_DIVISOR = 15.0;  // 距离缩放除数（调整为15.0，使Gizmo更大）

    // ✅ 颜色定义（RGBA，0-1范围）
    // X轴 = 鲜红色（Red），Y轴 = 鲜绿色（Green），Z轴 = 鲜蓝色（Blue）
    private static final float[] COLOR_X = {0.95f, 0.25f, 0.25f, 1.0f}; // 鲜红色
    private static final float[] COLOR_Y = {0.45f, 0.95f, 0.25f, 1.0f}; // 鲜绿色
    private static final float[] COLOR_Z = {0.25f, 0.50f, 0.95f, 1.0f}; // 鲜蓝色
    private static final float[] COLOR_PLANE = {0.85f, 0.85f, 0.85f, 0.5f}; // 灰色半透明
    private static final float[] COLOR_CENTER = {1.0f, 1.0f, 1.0f, 1.0f}; // 白色，完全不透明
    private static final float[] COLOR_HOVER = {1.0f, 1.0f, 0.3f, 1.0f}; // 黄色高亮

    public GizmoRenderer(GizmoContext context, GizmoInteractionController controller) {
        this.context = context;
        this.controller = controller;
    }

    /**
     * 渲染 Gizmo
     * ✅ v4.7: 使用独立的 Tessellator 和 BufferBuilder 管理线条渲染
     *
     * @param matrices MatrixStack
     * @param camera 摄像机
     * @param vcp VertexConsumerProvider（用于填充渲染：箭头、平面）
     * @param time 动画时间
     */
    public void render(MatrixStack matrices, Camera camera, VertexConsumerProvider vcp, double time) {
        if (!context.isActive() || context.getTargetBox() == null) {
            LOGGER.debug("[GizmoRenderer] 跳过渲染: isActive={}, targetBox={}",
                    context.isActive(), context.getTargetBox());
            return;
        }

        Box box = context.getTargetBox();

        // ✅ 修复：完全使用 pivot，不再计算 center
        Vec3d pivot = context.getPivot();
        if (pivot == null) {
            // 只有在 pivot 为 null 时才计算中心（作为后备）
            pivot = new Vec3d(
                    (box.minX + box.maxX) / 2.0,
                    (box.minY + box.maxY) / 2.0,
                    (box.minZ + box.maxZ) / 2.0
            );
            LOGGER.warn("[GizmoRenderer] ⚠️ pivot 为 null，使用 Box 中心作为后备");
        }

        Vec3d cameraPos = camera.getPos();

        // ✅ v4.9: 计算距离缩放系数
        double distanceMultiplier = getDistanceMultiplier(pivot, cameraPos);
        double axisLength = BASE_AXIS_LENGTH * distanceMultiplier;
        double arrowHeight = BASE_ARROW_HEIGHT * distanceMultiplier;
        double arrowRadius = BASE_ARROW_RADIUS * distanceMultiplier;
        double pivotSize = BASE_PIVOT_SIZE * distanceMultiplier;
        double planeSize = BASE_PLANE_SIZE * distanceMultiplier;

        // ✅ v4.8.1: 确保渲染状态独立管理
        matrices.push();
        try {
            // ✅ v4.8.1: 设置独立的渲染状态
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            
            // ✅ 关键修复：禁用深度测试，让 Gizmo 始终显示在最前面
            // 这样即使在半透明物体后面，Gizmo 也能正常显示
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false); // 禁用深度写入
            
            // ✅ 使用 PolygonOffset 让 Gizmo 更靠近摄像机，确保不被半透明填充遮挡
            // 使用比 SelectionRenderer 更大的负值（SelectionRenderer 使用 -1.5）
            RenderSystem.enablePolygonOffset();
            RenderSystem.polygonOffset(-3.0f, -3.0f); // 更靠近摄像机
            
            RenderSystem.lineWidth(3.0F); // 增大线条宽度到 3.0

            // ✅ 重置着色器颜色为白色，确保顶点颜色正确显示
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

            matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

            Matrix4f posMat = matrices.peek().getPositionMatrix();

            LOGGER.info("[GizmoRenderer] 🧩 开始渲染: Box={}, Pivot={}, Type={}, Visible={}",
                    box, pivot, context.getType(), context.isActive());

            // === 1️⃣ 绘制 Gizmo 中心点（白色立方体） ===
            try {
                drawPivotCube(posMat, vcp, pivot, pivotSize);
                LOGGER.debug("[GizmoRenderer] ✅ 中心点立方体渲染完成");
            } catch (Exception e) {
                LOGGER.error("[GizmoRenderer] ❌ 渲染中心点立方体失败", e);
            }

            // === 2️⃣ 渲染三轴线条（纯色，使用 VertexConsumerProvider） ===
            try {
                // ✅ 使用 RenderLayer.getLines() 确保正确的着色器
                VertexConsumer lineBuffer = vcp.getBuffer(RenderLayer.getLines());

                // ✅ 从角点向外延伸（如果是角点 Gizmo）
                // ✅ v4.9: 使用动态计算的轴长度
                Vec3d axisEnd = getAxisEndPoint(pivot, Axis.X, axisLength);
                drawLineToBuffer(lineBuffer, posMat, pivot, axisEnd, COLOR_X);

                axisEnd = getAxisEndPoint(pivot, Axis.Y, axisLength);
                drawLineToBuffer(lineBuffer, posMat, pivot, axisEnd, COLOR_Y);

                axisEnd = getAxisEndPoint(pivot, Axis.Z, axisLength);
                drawLineToBuffer(lineBuffer, posMat, pivot, axisEnd, COLOR_Z);

                LOGGER.info("[GizmoRenderer] ✅ 轴手柄线条渲染完成: 3条轴 (X={}, Y={}, Z={})",
                        java.util.Arrays.toString(COLOR_X),
                        java.util.Arrays.toString(COLOR_Y),
                        java.util.Arrays.toString(COLOR_Z));
            } catch (Exception e) {
                LOGGER.error("[GizmoRenderer] ❌ 渲染轴手柄线条失败", e);
            }

            // === 3️⃣ 渲染箭头（纯色三角锥，使用 VertexConsumerProvider） ===
            try {
                renderAxisHandlesArrows(posMat, vcp, pivot, axisLength, arrowHeight, arrowRadius);
                LOGGER.debug("[GizmoRenderer] ✅ 轴手柄箭头渲染完成");
            } catch (Exception e) {
                LOGGER.error("[GizmoRenderer] ❌ 渲染轴手柄箭头失败", e);
            }

            // 渲染平面手柄
            try {
                renderPlaneHandles(posMat, vcp, pivot, time, planeSize);
                LOGGER.debug("[GizmoRenderer] ✅ 平面手柄渲染完成");
            } catch (Exception e) {
                LOGGER.error("[GizmoRenderer] ❌ 渲染平面手柄失败", e);
            }

            // ✅ v4.8: 角点手柄已移至 SelectionOverlayRenderer，不再在此处渲染
            // 避免双层角点标记（橙+黄）的问题
            // 角点标记由 SelectionOverlayRenderer.drawCornerHandles() 在 Ctrl 按下时统一管理

            // 渲染 ResizeHandle（BOUNDS 类型使用新的 ResizeHandle）
            // 注意：对于框选工具（BoxSelectTool），面手柄由 SelectionOverlayRenderer 统一渲染为白色
            // 这里不再渲染面手柄，避免重复渲染
            // 其他工具如果需要 ResizeHandle，可以在这里渲染
            if (context.getType() == GizmoType.BOUNDS) {
                // 框选工具的面手柄由 SelectionOverlayRenderer 渲染，这里跳过
                // 如果需要为其他工具渲染 ResizeHandle，可以在这里添加条件判断
                // try {
                //     renderResizeHandles(posMat, vcp, time);
                //     LOGGER.debug("[GizmoRenderer] ✅ ResizeHandle 渲染完成");
                // } catch (Exception e) {
                //     LOGGER.error("[GizmoRenderer] ❌ 渲染 ResizeHandle 失败", e);
                // }
            }

            // 渲染旋转环（TRANSFORM 类型或动画模式）
            if (context.getType() == GizmoType.TRANSFORM || context.getType() == GizmoType.PIVOT) {
                try {
                    renderRotationRings(posMat, vcp, pivot, time);
                    LOGGER.debug("[GizmoRenderer] ✅ 旋转环渲染完成");
                } catch (Exception e) {
                    LOGGER.error("[GizmoRenderer] ❌ 渲染旋转环失败", e);
                }
            }

            // 渲染中心手柄
            try {
                renderCenterHandle(posMat, vcp, pivot, time);
                LOGGER.debug("[GizmoRenderer] ✅ 中心手柄渲染完成");
            } catch (Exception e) {
                LOGGER.error("[GizmoRenderer] ❌ 渲染中心手柄失败", e);
            }

            LOGGER.info("[GizmoRenderer] ✅ Gizmo 渲染完成: Pivot={}", pivot);

        } catch (Exception e) {
            LOGGER.error("[GizmoRenderer] ❌ 渲染 Gizmo 时发生异常", e);
            e.printStackTrace();
        } finally {
            // ✅ v4.8.1: 确保状态恢复，即使发生异常也恢复
            RenderSystem.disablePolygonOffset(); // 禁用 PolygonOffset
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);   // 恢复深度写入
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f); // ✅ 重置着色器颜色
            matrices.pop();
            LOGGER.debug("[GizmoRenderer] ✅ 渲染状态已恢复");
        }
    }

    /**
     * ✅ v4.8.1: 绘制 Gizmo 中心点（白色立方体）
     * ✅ v4.9: 添加动态大小支持
     */
    private void drawPivotCube(Matrix4f matrix, VertexConsumerProvider vcp, Vec3d pivot, double pivotSize) {
        float half = (float)(pivotSize / 2.0);
        float x = (float)pivot.x;
        float y = (float)pivot.y;
        float z = (float)pivot.z;

        // ✅ 使用自定义的 RenderLayer，禁用深度写入但仍然测试深度
        // 这样 Gizmo 可以在半透明物体后面显示，但仍然有正确的前后关系
        VertexConsumer buffer = vcp.getBuffer(RenderLayer.getLines());

        float r = 1.0f, g = 1.0f, b = 1.0f, a = 1.0f;

        // 绘制立方体的边框（12条边）
        // 底面4条边
        drawLineToBuffer(buffer, matrix, new Vec3d(x-half, y-half, z-half), new Vec3d(x+half, y-half, z-half), new float[]{r,g,b,a});
        drawLineToBuffer(buffer, matrix, new Vec3d(x+half, y-half, z-half), new Vec3d(x+half, y-half, z+half), new float[]{r,g,b,a});
        drawLineToBuffer(buffer, matrix, new Vec3d(x+half, y-half, z+half), new Vec3d(x-half, y-half, z+half), new float[]{r,g,b,a});
        drawLineToBuffer(buffer, matrix, new Vec3d(x-half, y-half, z+half), new Vec3d(x-half, y-half, z-half), new float[]{r,g,b,a});
        
        // 顶面4条边
        drawLineToBuffer(buffer, matrix, new Vec3d(x-half, y+half, z-half), new Vec3d(x+half, y+half, z-half), new float[]{r,g,b,a});
        drawLineToBuffer(buffer, matrix, new Vec3d(x+half, y+half, z-half), new Vec3d(x+half, y+half, z+half), new float[]{r,g,b,a});
        drawLineToBuffer(buffer, matrix, new Vec3d(x+half, y+half, z+half), new Vec3d(x-half, y+half, z+half), new float[]{r,g,b,a});
        drawLineToBuffer(buffer, matrix, new Vec3d(x-half, y+half, z+half), new Vec3d(x-half, y+half, z-half), new float[]{r,g,b,a});
        
        // 竖直4条边
        drawLineToBuffer(buffer, matrix, new Vec3d(x-half, y-half, z-half), new Vec3d(x-half, y+half, z-half), new float[]{r,g,b,a});
        drawLineToBuffer(buffer, matrix, new Vec3d(x+half, y-half, z-half), new Vec3d(x+half, y+half, z-half), new float[]{r,g,b,a});
        drawLineToBuffer(buffer, matrix, new Vec3d(x+half, y-half, z+half), new Vec3d(x+half, y+half, z+half), new float[]{r,g,b,a});
        drawLineToBuffer(buffer, matrix, new Vec3d(x-half, y-half, z+half), new Vec3d(x-half, y+half, z+half), new float[]{r,g,b,a});
    }

    /**
     * ✅ v4.9: 获取距离缩放系数
     * 根据摄像机距离动态调整 Gizmo 大小，确保远距离时仍然可见
     *
     * @param pivot Gizmo 中心点
     * @param cameraPos 摄像机位置
     * @return 距离缩放系数（距离越远，系数越大）
     */
    private double getDistanceMultiplier(Vec3d pivot, Vec3d cameraPos) {
        double distance = pivot.distanceTo(cameraPos);
        if (distance < MIN_DISTANCE_MULTIPLIER) {
            distance = MIN_DISTANCE_MULTIPLIER;
        }
        return distance / DISTANCE_SCALE_DIVISOR;
    }

    /**
     * 获取轴端点（现在使用动态计算的轴长度）
     *
     * @param pivot Gizmo 中心点
     * @param axis 轴方向
     * @param axisLength 轴长度（已考虑距离缩放）
     */
    private Vec3d getAxisEndPoint(Vec3d pivot, Axis axis, double axisLength) {
        return switch (axis) {
            case X -> pivot.add(axisLength, 0, 0);
            case Y -> pivot.add(0, axisLength, 0);
            case Z -> pivot.add(0, 0, axisLength);
        };
    }

    /**
     * ✅ v4.8: 渲染轴手柄箭头（纯色三角锥，使用独立的 Tessellator）
     * ✅ v4.9: 添加动态大小支持
     */
    private void renderAxisHandlesArrows(Matrix4f posMat, VertexConsumerProvider vcp, Vec3d pivot, double axisLength, double arrowHeight, double arrowRadius) {
        // X 轴箭头（红色）
        drawArrowHead(posMat, vcp, getAxisEndPoint(pivot, Axis.X, axisLength), Axis.X, COLOR_X, arrowHeight, arrowRadius);

        // Y 轴箭头（绿色）
        drawArrowHead(posMat, vcp, getAxisEndPoint(pivot, Axis.Y, axisLength), Axis.Y, COLOR_Y, arrowHeight, arrowRadius);

        // Z 轴箭头（蓝色）
        drawArrowHead(posMat, vcp, getAxisEndPoint(pivot, Axis.Z, axisLength), Axis.Z, COLOR_Z, arrowHeight, arrowRadius);
    }

    /**
     * ✅ v4.8: 绘制箭头（纯色三角锥）
     * ✅ v4.9: 添加动态大小支持
     * 修复：确保箭头使用正确的颜色（X=红，Y=绿，Z=蓝）
     */
    private void drawArrowHead(Matrix4f matrix, VertexConsumerProvider vcp, Vec3d tip, Axis axis, float[] color, double arrowHeight, double arrowRadius) {
        // ✅ 使用 RenderLayer.getLines() 绘制箭头的轮廓锥形，确保始终可见
        VertexConsumer buffer = vcp.getBuffer(RenderLayer.getLines());

        // 箭头底部中心（沿 axis 方向回退 arrowHeight）
        Vec3d base = switch (axis) {
            case X -> tip.add(-arrowHeight, 0, 0);
            case Y -> tip.add(0, -arrowHeight, 0);
            case Z -> tip.add(0, 0, -arrowHeight);
        };

        // ✅ 生成圆锥底部的多个点（8个点形成圆形底面）
        int segments = 8;
        Vec3d[] basePoints = new Vec3d[segments];
        
        for (int i = 0; i < segments; i++) {
            double angle = 2.0 * Math.PI * i / segments;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            
            basePoints[i] = switch (axis) {
                case X -> base.add(0, arrowRadius * cos, arrowRadius * sin);
                case Y -> base.add(arrowRadius * cos, 0, arrowRadius * sin);
                case Z -> base.add(arrowRadius * cos, arrowRadius * sin, 0);
            };
        }

        // 绘制从顶点到底部圆圈的线条（锥形边缘）
        for (int i = 0; i < segments; i++) {
            Vec3d p = basePoints[i];
            drawLineToBuffer(buffer, matrix, tip, p, color);
        }
        
        // 绘制底部圆圈
        for (int i = 0; i < segments; i++) {
            Vec3d p1 = basePoints[i];
            Vec3d p2 = basePoints[(i + 1) % segments];
            drawLineToBuffer(buffer, matrix, p1, p2, color);
        }
    }

    /**
     * 渲染平面手柄
     * ✅ v4.9: 添加动态大小支持
     */
    private void renderPlaneHandles(Matrix4f posMat, VertexConsumerProvider vcp, Vec3d center, double time, double planeSize) {
        VertexConsumer fillConsumer = vcp.getBuffer(com.chronoblocks.client.renderer.RenderLayers.getPreviewLayer());

        // XY 平面
        renderPlane(fillConsumer, posMat, center, Axis.X, Axis.Y, planeSize);

        // XZ 平面
        renderPlane(fillConsumer, posMat, center, Axis.X, Axis.Z, planeSize);

        // YZ 平面
        renderPlane(fillConsumer, posMat, center, Axis.Y, Axis.Z, planeSize);
    }

    /**
     * 渲染单个平面
     * ✅ v4.9: 添加动态大小支持
     */
    private void renderPlane(VertexConsumer consumer, Matrix4f posMat, Vec3d center, Axis a1, Axis a2, double planeSize) {
        Vec3d dir1 = getAxisDirection(a1);
        Vec3d dir2 = getAxisDirection(a2);
        double halfSize = planeSize / 2.0;

        Vec3d p1 = center.add(dir1.multiply(-halfSize)).add(dir2.multiply(-halfSize));
        Vec3d p2 = center.add(dir1.multiply(halfSize)).add(dir2.multiply(-halfSize));
        Vec3d p3 = center.add(dir1.multiply(halfSize)).add(dir2.multiply(halfSize));
        Vec3d p4 = center.add(dir1.multiply(-halfSize)).add(dir2.multiply(halfSize));

        float[] finalColor = GizmoRenderer.COLOR_PLANE.clone();
        GizmoHandle hovered = controller.getHoveredHandle();
        if (hovered instanceof PlaneHandle plane) {
            if ((plane.getAxis1() == a1 && plane.getAxis2() == a2) ||
                    (plane.getAxis1() == a2 && plane.getAxis2() == a1)) {
                finalColor = new float[]{1.0f, 1.0f, 1.0f, 0.6f}; // 高亮
            }
        }

        // 绘制平面（四边形）
        drawQuad(consumer, posMat, p1, p2, p3, p4, finalColor);
    }

    /**
     * 渲染中心手柄
     */
    private void renderCenterHandle(Matrix4f posMat, VertexConsumerProvider vcp, Vec3d center, double time) {
        VertexConsumer consumer = vcp.getBuffer(RenderLayer.getLines());

        float[] color = COLOR_CENTER.clone();
        if (controller.getHoveredHandle() instanceof CenterHandle) {
            color = COLOR_HOVER.clone();
            color[3] *= (1.0f + 0.3f * (float) Math.sin(System.currentTimeMillis() * 0.005)); // 脉冲效果
        }

        drawCube(consumer, posMat, center, color);
    }

    // ========== 辅助渲染方法 ==========

    /**
     * ✅ 绘制线条（使用 BufferBuilder）
     * 向当前 buffer 写入一条线段的两个顶点。
     * 仅写入数据，不调用 begin()/draw()。
     */
    @SuppressWarnings("unused")
    private void drawLine(BufferBuilder buffer, Matrix4f posMat, Vec3d start, Vec3d end, float[] color) {
        try {
            buffer.vertex(posMat, (float)start.x, (float)start.y, (float)start.z)
                    .color(color[0], color[1], color[2], color[3]);
            buffer.vertex(posMat, (float)end.x, (float)end.y, (float)end.z)
                    .color(color[0], color[1], color[2], color[3]);
        } catch (IllegalStateException e) {
            // 捕获 "Not building!" 异常，自动恢复
            LOGGER.error("[GizmoRenderer] ❌ drawLine() 调用时 buffer 未 begin(): {}", e.getMessage());
        }
    }
    
    /**
     * 绘制线条到 VertexConsumer（用于 RenderLayer）
     */
    private void drawLineToBuffer(VertexConsumer buffer, Matrix4f posMat, Vec3d start, Vec3d end, float[] color) {
        buffer.vertex(posMat, (float)start.x, (float)start.y, (float)start.z)
                .color(color[0], color[1], color[2], color[3])
                .normal(0f, 1f, 0f);
        buffer.vertex(posMat, (float)end.x, (float)end.y, (float)end.z)
                .color(color[0], color[1], color[2], color[3])
                .normal(0f, 1f, 0f);
    }

    /**
     * 绘制线条（使用 VertexConsumer）
     * 用于需要 VertexConsumer 的地方（如 drawCube）
     */
    private void drawLineVertexConsumer(VertexConsumer consumer, Matrix4f posMat, Vec3d start, Vec3d end, float[] color) {
        consumer.vertex(posMat, (float)start.x, (float)start.y, (float)start.z)
                .color(color[0], color[1], color[2], color[3])
                .normal(0.0f, 1.0f, 0.0f);
        consumer.vertex(posMat, (float)end.x, (float)end.y, (float)end.z)
                .color(color[0], color[1], color[2], color[3])
                .normal(0.0f, 1.0f, 0.0f);
    }

    private void drawQuad(VertexConsumer consumer, Matrix4f posMat, Vec3d p1, Vec3d p2, Vec3d p3, Vec3d p4, float[] color) {
        // 计算法线向量（垂直于四边形平面）
        Vec3d edge1 = p2.subtract(p1);
        Vec3d edge2 = p3.subtract(p1);
        Vec3d normal = edge1.crossProduct(edge2).normalize();
        float nx = (float)normal.x;
        float ny = (float)normal.y;
        float nz = (float)normal.z;

        // ✅ 需要完整的顶点属性：UV0, UV2 (light), Normal
        // 参考 SelectionRenderer.emitQuad() 的实现
        consumer.vertex(posMat, (float)p1.x, (float)p1.y, (float)p1.z)
                .color(color[0], color[1], color[2], color[3])
                .texture(0.0f, 0.0f)  // UV0
                .overlay(OverlayTexture.DEFAULT_UV)  // Overlay UV
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)  // UV2 (光照)
                .normal(nx, ny, nz);  // Normal

        consumer.vertex(posMat, (float)p2.x, (float)p2.y, (float)p2.z)
                .color(color[0], color[1], color[2], color[3])
                .texture(1.0f, 0.0f)  // UV0
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)  // UV2
                .normal(nx, ny, nz);

        consumer.vertex(posMat, (float)p3.x, (float)p3.y, (float)p3.z)
                .color(color[0], color[1], color[2], color[3])
                .texture(1.0f, 1.0f)  // UV0
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)  // UV2
                .normal(nx, ny, nz);

        consumer.vertex(posMat, (float)p4.x, (float)p4.y, (float)p4.z)
                .color(color[0], color[1], color[2], color[3])
                .texture(0.0f, 1.0f)  // UV0
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)  // UV2
                .normal(nx, ny, nz);
    }

    private void drawCube(VertexConsumer consumer, Matrix4f posMat, Vec3d center, float[] color) {
        // ✅ v4.9: 使用固定的中心手柄大小（保持兼容性）
        double halfSize = BASE_CENTER_RADIUS / 2.0;
        Vec3d min = center.add(-halfSize, -halfSize, -halfSize);
        Vec3d max = center.add(halfSize, halfSize, halfSize);

        // 绘制立方体的12条边（简化：绘制8个角点之间的线）
        Vec3d[] corners = {
                new Vec3d(min.x, min.y, min.z), new Vec3d(max.x, min.y, min.z),
                new Vec3d(min.x, max.y, min.z), new Vec3d(max.x, max.y, min.z),
                new Vec3d(min.x, min.y, max.z), new Vec3d(max.x, min.y, max.z),
                new Vec3d(min.x, max.y, max.z), new Vec3d(max.x, max.y, max.z)
        };

        // 绘制12条边
        int[][] edges = {
                {0, 1}, {2, 3}, {4, 5}, {6, 7}, // X方向
                {0, 2}, {1, 3}, {4, 6}, {5, 7}, // Y方向
                {0, 4}, {1, 5}, {2, 6}, {3, 7}  // Z方向
        };

        for (int[] edge : edges) {
            drawLineVertexConsumer(consumer, posMat, corners[edge[0]], corners[edge[1]], color);
        }
    }


    private Vec3d getAxisDirection(Axis axis) {
        return switch (axis) {
            case X -> new Vec3d(1, 0, 0);
            case Y -> new Vec3d(0, 1, 0);
            case Z -> new Vec3d(0, 0, 1);
        };
    }

    /**
     * 渲染旋转环
     */
    private void renderRotationRings(Matrix4f posMat, VertexConsumerProvider vcp, Vec3d center, double time) {
        VertexConsumer lineConsumer = vcp.getBuffer(RenderLayer.getLines());

        List<com.chronoblocks.client.gizmo.handles.RotationHandle> rotationHandles =
                controller.getRotationHandles();

        for (com.chronoblocks.client.gizmo.handles.RotationHandle rotHandle : rotationHandles) {
            float[] color = getRotationRingColor(rotHandle.getAxis());

            // 检查是否激活
            if (controller.getActiveRotationHandle() == rotHandle) {
                color = new float[]{1.0f, 1.0f, 1.0f, 1.0f}; // 白色高亮
                color[3] *= (1.0f + 0.3f * (float) Math.sin(time * 10.0)); // 脉冲效果
            }

            // 使用 SelectionRenderer 的 drawRing 方法
            OverlayRenderHelper.drawRing(
                    lineConsumer,
                    posMat,
                    rotHandle.getCenter(),
                    rotHandle.getAxis(),
                    rotHandle.getRadius(),
                    color
            );
        }
    }

    /**
     * ✅ 获取旋转环颜色（与轴手柄颜色保持一致）
     */
    private float[] getRotationRingColor(Axis axis) {
        return switch (axis) {
            case X -> new float[]{0.95f, 0.25f, 0.25f, 0.8f}; // 鲜红色
            case Y -> new float[]{0.45f, 0.95f, 0.25f, 0.8f}; // 鲜绿色
            case Z -> new float[]{0.25f, 0.50f, 0.95f, 0.8f}; // 鲜蓝色
        };
    }

    /**
     * 渲染 ResizeHandle（面手柄和角点手柄）
     */
    @SuppressWarnings("unused")
    private void renderResizeHandles(Matrix4f posMat, VertexConsumerProvider vcp, double time) {
        VertexConsumer lineConsumer = vcp.getBuffer(RenderLayer.getLines());

        List<com.chronoblocks.client.gizmo.handles.ResizeHandle> resizeHandles =
                controller.getResizeHandles();

        for (com.chronoblocks.client.gizmo.handles.ResizeHandle handle : resizeHandles) {
            // 只渲染面手柄，不渲染角点手柄
            if (handle.getHandleType() != com.chronoblocks.client.gizmo.handles.ResizeHandle.Type.FACE) {
                continue; // 跳过角点手柄
            }
            
            float[] color;
            // 面手柄：青色
            color = new float[]{0.2f, 0.8f, 1.0f, 0.9f};

            // 检查是否激活或悬停
            GizmoHandle hovered = controller.getHoveredHandle();
            if (hovered == handle || handle.isDragging()) {
                // 高亮显示
                color = new float[]{1.0f, 1.0f, 1.0f, 1.0f};
                if (handle.isDragging()) {
                    // 激活时脉冲效果
                    color[3] *= (1.0f + 0.3f * (float) Math.sin(time * 10.0));
                }
            }

            // 绘制手柄（使用 SelectionRenderer 的统一方法）
            OverlayRenderHelper.drawHandle(lineConsumer, posMat, handle.getPosition(), color, handle.isDragging());
        }
    }

    // =============================================================
    // 📐 轴向 Gizmo (静态工具)
    // =============================================================

    /**
     * 绘制红绿蓝三轴箭头，用于简单坐标指示。
     */
    public static void drawAxisGizmo(MatrixStack matrices, VertexConsumerProvider consumers,
                                     Vec3d center, Vec3d camPos) {
        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLines());
        Matrix4f mat = matrices.peek().getPositionMatrix();

        float cx = (float) (center.x - camPos.x);
        float cy = (float) (center.y - camPos.y);
        float cz = (float) (center.z - camPos.z);

        float len = 0.8f;
        float head = 0.15f;

        // X 轴（红）
        drawAxisArrow(buffer, mat, cx, cy, cz, len, 1, 0, 0, 1.0f, 0.2f, 0.2f, head);
        // Y 轴（绿）
        drawAxisArrow(buffer, mat, cx, cy, cz, len, 0, 1, 0, 0.2f, 1.0f, 0.2f, head);
        // Z 轴（蓝）
        drawAxisArrow(buffer, mat, cx, cy, cz, len, 0, 0, 1, 0.2f, 0.4f, 1.0f, head);
    }

    private static void drawAxisArrow(VertexConsumer buffer, Matrix4f mat,
                                      float cx, float cy, float cz,
                                      float length, int nx, int ny, int nz,
                                      float r, float g, float b, float headSize) {
        float endX = cx + length * nx;
        float endY = cy + length * ny;
        float endZ = cz + length * nz;

        buffer.vertex(mat, cx, cy, cz).color(r, g, b, 1f).normal(0f, 0f, 0f);
        buffer.vertex(mat, endX, endY, endZ).color(r, g, b, 1f).normal(0f, 0f, 0f);

        drawArrowHead(buffer, mat, endX, endY, endZ, nx, ny, nz, headSize, r, g, b);
    }

    /**
     * 简化箭头头部：沿轴方向退回一段并连三条辅助线形成 "燕尾"。
     * v5.1: 优化为更简洁的固定方向，避免翻面
     */
    private static void drawArrowHead(VertexConsumer buffer, Matrix4f mat,
                                       float x, float y, float z,
                                       int nx, int ny, int nz,
                                       float size, float r, float g, float b) {
        float sx = x - nx * size;
        float sy = y - ny * size;
        float sz = z - nz * size;

        float halfSize = size / 3f;

        if (nx != 0) {
            // X 轴箭头：在 YZ 平面形成三角
            buffer.vertex(mat, x, y, z).color(r, g, b, 1f).normal(0f, 0f, 0f);
            buffer.vertex(mat, sx, y + halfSize, z).color(r, g, b, 1f).normal(0f, 0f, 0f);

            buffer.vertex(mat, x, y, z).color(r, g, b, 1f).normal(0f, 0f, 0f);
            buffer.vertex(mat, sx, y - halfSize, z).color(r, g, b, 1f).normal(0f, 0f, 0f);

            buffer.vertex(mat, x, y, z).color(r, g, b, 1f).normal(0f, 0f, 0f);
            buffer.vertex(mat, sx, y, z + halfSize).color(r, g, b, 1f).normal(0f, 0f, 0f);

            buffer.vertex(mat, x, y, z).color(r, g, b, 1f).normal(0f, 0f, 0f);
            buffer.vertex(mat, sx, y, z - halfSize).color(r, g, b, 1f).normal(0f, 0f, 0f);
        } else if (ny != 0) {
            // Y 轴箭头：在 XZ 平面形成三角
            buffer.vertex(mat, x, y, z).color(r, g, b, 1f).normal(0f, 0f, 0f);
            buffer.vertex(mat, x + halfSize, sy, z).color(r, g, b, 1f).normal(0f, 0f, 0f);

            buffer.vertex(mat, x, y, z).color(r, g, b, 1f).normal(0f, 0f, 0f);
            buffer.vertex(mat, x - halfSize, sy, z).color(r, g, b, 1f).normal(0f, 0f, 0f);

            buffer.vertex(mat, x, y, z).color(r, g, b, 1f).normal(0f, 0f, 0f);
            buffer.vertex(mat, x, sy, z + halfSize).color(r, g, b, 1f).normal(0f, 0f, 0f);

            buffer.vertex(mat, x, y, z).color(r, g, b, 1f).normal(0f, 0f, 0f);
            buffer.vertex(mat, x, sy, z - halfSize).color(r, g, b, 1f).normal(0f, 0f, 0f);
        } else if (nz != 0) {
            // Z 轴箭头：在 XY 平面形成三角
            buffer.vertex(mat, x, y, z).color(r, g, b, 1f).normal(0f, 0f, 0f);
            buffer.vertex(mat, x + halfSize, y, sz).color(r, g, b, 1f).normal(0f, 0f, 0f);

            buffer.vertex(mat, x, y, z).color(r, g, b, 1f).normal(0f, 0f, 0f);
            buffer.vertex(mat, x - halfSize, y, sz).color(r, g, b, 1f).normal(0f, 0f, 0f);

            buffer.vertex(mat, x, y, z).color(r, g, b, 1f).normal(0f, 0f, 0f);
            buffer.vertex(mat, x, y + halfSize, sz).color(r, g, b, 1f).normal(0f, 0f, 0f);

            buffer.vertex(mat, x, y, z).color(r, g, b, 1f).normal(0f, 0f, 0f);
            buffer.vertex(mat, x, y - halfSize, sz).color(r, g, b, 1f).normal(0f, 0f, 0f);
        }
    }
}