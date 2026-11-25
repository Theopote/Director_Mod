package com.director.client.renderer;

import com.director.client.gizmo.util.GizmoVisualScale;
import com.director.core.selection.SelectionManager;
import com.director.core.selection.tools.FaceHandleManager;
import com.director.client.settings.RenderSettings;
import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * Renders transient interaction hints such as drag arrows or hover indicators.
 */
public final class InteractionHintRenderer {

    private InteractionHintRenderer() {}

    // 全局设置中心（新系统优先读取）
    private static final RenderSettings settings = RenderSettings.get();

    /** 悬停双向箭头基础长度（世界单位，再乘距离系数）。 */
    private static final double ARROW_LENGTH_BASE = 0.85;
    /** 悬停双向箭头箭头头尺寸。 */
    private static final double ARROW_HEAD_BASE = 0.24;
    /** 拖拽方向箭头头尺寸。 */
    private static final double DRAG_ARROW_HEAD = 0.28;

    public static void render(MatrixStack matrices,
                              VertexConsumerProvider consumers,
                              Vec3d camPos,
                              SelectionManager manager,
                              float tickDelta) {
        if (manager == null || consumers == null || matrices == null) {
            return;
        }
        // 该渲染器绘制的是“包围盒交互手柄/箭头”，应受“显示包围盒”开关控制
        if (!settings.selection_showBoundingBox) {
            // 如果用户关闭包围盒显示，确保面缩放预览状态也被取消，避免“关闭不了”的错觉
            if (manager.isFaceResizeActive()) {
                manager.cancelFaceResize();
            }
            return;
        }

        // ✅ 直接从全局设置读取（实时生效）
        if (!settings.tool_showOutline) {
            return;
        }

        Box selectionBox = manager.getCurrentBounds();
        if (selectionBox == null) {
            return;
        }

        FaceHandleManager faceHandleManager = manager.getFaceHandleManager();
        Integer hoveredFace = faceHandleManager.getHoveredFaceIndex();
        Integer draggingFace = faceHandleManager.isDragging() ? faceHandleManager.getDraggingFaceIndex() : null;
        Box previewBox = manager.isFaceResizeActive() ? manager.getResizePreviewBounds() : null;
        Box baseBounds = manager.isFaceResizeActive() && manager.getResizeBaseBounds() != null
            ? manager.getResizeBaseBounds()
            : selectionBox;

        Box handleBox = (draggingFace != null && previewBox != null)
            ? previewBox
            : selectionBox;

        // 与 Actor Gizmo 一致：禁用深度，确保背面/被方块遮挡时仍能找到手柄
        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(
            GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
            GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA
        );
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);
        try {
            drawAxisColoredHandles(
                matrices,
                consumers,
                camPos,
                handleBox,
                hoveredFace,
                draggingFace
            );

            VertexConsumer lineConsumer = consumers.getBuffer(RenderLayers.getLineOverlay());
            Matrix4f matrix = matrices.peek().getPositionMatrix();

            if (draggingFace != null && previewBox != null) {
                drawDragArrow(lineConsumer, matrix, camPos,
                    baseBounds, previewBox, draggingFace);
            } else if (hoveredFace != null) {
                drawBidirectionalArrow(lineConsumer, matrix, camPos,
                    baseBounds, hoveredFace);
            }
        } finally {
            GlStateManager._enableDepthTest();
            GlStateManager._depthMask(true);
            GlStateManager._disableBlend();
        }
    }

    private static void drawAxisColoredHandles(MatrixStack matrices,
                                               VertexConsumerProvider consumers,
                                               Vec3d camPos,
                                               Box handleBox,
                                               Integer hoveredFace,
                                               Integer draggingFace) {
        // 半透明高饱和填充 + 同色亮线框（无黑边）
        VertexConsumer fillBuffer = consumers.getBuffer(RenderLayers.getPreviewLayer());
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        for (int faceIndex = 0; faceIndex < 6; faceIndex++) {
            Vec3d center = OverlayRenderHelper.faceCenter(handleBox, faceIndex);
            Vec3d rel = center.subtract(camPos);
            double scale = GizmoVisualScale.distanceMultiplier(center, camPos);

            float[] base = axisBaseColor(faceIndex);
            boolean hovered = hoveredFace != null && hoveredFace == faceIndex;
            boolean dragging = draggingFace != null && draggingFace == faceIndex;
            boolean active = hovered || dragging;

            float[] fillColor;
            if (dragging) {
                fillColor = new float[]{1.0f, 0.95f, 0.35f, 0.82f};
            } else if (hovered) {
                float[] hot = brighten(base);
                fillColor = new float[]{hot[0], hot[1], hot[2], 0.78f};
            } else {
                fillColor = new float[]{base[0], base[1], base[2], 0.62f};
            }

            OverlayRenderHelper.drawSolidHandle(fillBuffer, matrix, rel, fillColor, active, scale);
        }

        VertexConsumer lineBuffer = consumers.getBuffer(RenderLayers.getLineOverlay());
        for (int faceIndex = 0; faceIndex < 6; faceIndex++) {
            Vec3d center = OverlayRenderHelper.faceCenter(handleBox, faceIndex);
            Vec3d rel = center.subtract(camPos);
            double scale = GizmoVisualScale.distanceMultiplier(center, camPos);

            float[] base = axisBaseColor(faceIndex);
            boolean hovered = hoveredFace != null && hoveredFace == faceIndex;
            boolean dragging = draggingFace != null && draggingFace == faceIndex;
            boolean active = hovered || dragging;

            float[] outlineColor;
            if (dragging) {
                outlineColor = new float[]{1.0f, 1.0f, 0.65f, 1.0f};
            } else if (hovered) {
                outlineColor = brighten(base);
            } else {
                outlineColor = new float[]{base[0], base[1], base[2], 1.0f};
            }

            OverlayRenderHelper.drawHandle(lineBuffer, matrix, rel, outlineColor, active, scale);
        }
    }

    private static void drawDragArrow(VertexConsumer consumer,
                                      Matrix4f matrix,
                                      Vec3d camPos,
                                      Box baseBox,
                                      Box previewBox,
                                      int faceIndex) {
        Vec3d start = OverlayRenderHelper.faceCenter(baseBox, faceIndex);
        Vec3d end = OverlayRenderHelper.faceCenter(previewBox, faceIndex);
        Vec3d delta = end.subtract(start);
        if (delta.lengthSquared() < 1e-6) {
            return;
        }

        double scale = GizmoVisualScale.distanceMultiplier(end, camPos);
        float[] color = axisArrowColor(faceIndex);
        Vec3d startRel = start.subtract(camPos);
        Vec3d endRel = end.subtract(camPos);
        // 深色描边线，提高对比度
        line(consumer, matrix, startRel, endRel, new float[]{0.02f, 0.02f, 0.05f, 0.85f});
        line(consumer, matrix, startRel, endRel, color);

        Vec3d dir = delta.normalize();
        drawArrowHead(consumer, matrix, endRel, dir, DRAG_ARROW_HEAD * scale, color);
    }

    private static void drawBidirectionalArrow(VertexConsumer consumer,
                                               Matrix4f matrix,
                                               Vec3d camPos,
                                               Box box,
                                               int faceIndex) {
        Vec3d faceCenter = OverlayRenderHelper.faceCenter(box, faceIndex);
        Vec3d normal = OverlayRenderHelper.faceNormal(faceIndex).normalize();

        double distanceMultiplier = GizmoVisualScale.distanceMultiplier(faceCenter, camPos);
        double arrowLength = ARROW_LENGTH_BASE * distanceMultiplier;
        double arrowHeadSize = ARROW_HEAD_BASE * distanceMultiplier;

        float[] color = axisArrowColor(faceIndex);
        float[] halo = {0.02f, 0.02f, 0.05f, 0.9f};

        Vec3d arrow1Start = faceCenter.subtract(normal.multiply(arrowLength));
        Vec3d arrow2End = faceCenter.add(normal.multiply(arrowLength));

        Vec3d arrow1StartRel = arrow1Start.subtract(camPos);
        Vec3d arrow1EndRel = faceCenter.subtract(camPos);
        Vec3d arrow2EndRel = arrow2End.subtract(camPos);

        // 先画深色粗感描边，再画轴色主体
        line(consumer, matrix, arrow1StartRel, arrow1EndRel, halo);
        line(consumer, matrix, arrow1EndRel, arrow2EndRel, halo);
        line(consumer, matrix, arrow1StartRel, arrow1EndRel, color);
        line(consumer, matrix, arrow1EndRel, arrow2EndRel, color);

        drawArrowHead(consumer, matrix, arrow1StartRel, normal.multiply(-1), arrowHeadSize, color);
        drawArrowHead(consumer, matrix, arrow2EndRel, normal, arrowHeadSize, color);
    }

    /** X=红 / Y=绿 / Z=蓝，饱和度偏高，便于远距离辨认。 */
    private static float[] axisBaseColor(int faceIndex) {
        return switch (faceIndex) {
            case 0, 1 -> new float[]{1.0f, 0.22f, 0.22f};
            case 2, 3 -> new float[]{0.22f, 1.0f, 0.32f};
            case 4, 5 -> new float[]{0.30f, 0.55f, 1.0f};
            default -> new float[]{0.95f, 0.95f, 0.95f};
        };
    }

    private static float[] axisArrowColor(int faceIndex) {
        float[] base = brighten(axisBaseColor(faceIndex));
        return new float[]{base[0], base[1], base[2], 1.0f};
    }

    private static float[] brighten(float[] base) {
        return new float[]{
            clamp(base[0] + 0.28f),
            clamp(base[1] + 0.28f),
            clamp(base[2] + 0.28f),
            1.0f
        };
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static void line(VertexConsumer consumer, Matrix4f matrix, Vec3d start, Vec3d end, float[] color) {
        consumer.vertex(matrix, (float) start.x, (float) start.y, (float) start.z)
            .color(color[0], color[1], color[2], color[3])
            .normal(0, 1, 0);
        consumer.vertex(matrix, (float) end.x, (float) end.y, (float) end.z)
            .color(color[0], color[1], color[2], color[3])
            .normal(0, 1, 0);
    }

    private static void drawArrowHead(VertexConsumer consumer,
                                      Matrix4f matrix,
                                      Vec3d tip,
                                      Vec3d dir,
                                      double size,
                                      float[] color) {
        Vec3d base = tip.subtract(dir.multiply(size));

        Vec3d perp1;
        Vec3d perp2;
        if (Math.abs(dir.y) < 0.9) {
            Vec3d up = new Vec3d(0, 1, 0);
            perp1 = dir.crossProduct(up).normalize().multiply(size * 0.55);
            perp2 = dir.crossProduct(perp1).normalize().multiply(size * 0.55);
        } else {
            Vec3d right = new Vec3d(1, 0, 0);
            perp1 = dir.crossProduct(right).normalize().multiply(size * 0.55);
            perp2 = dir.crossProduct(perp1).normalize().multiply(size * 0.55);
        }

        Vec3d p1 = base.add(perp1);
        Vec3d p2 = base.add(perp2);
        Vec3d p3 = base.subtract(perp1).subtract(perp2);

        float[] halo = {0.02f, 0.02f, 0.05f, 0.85f};
        line(consumer, matrix, tip, p1, halo);
        line(consumer, matrix, tip, p2, halo);
        line(consumer, matrix, tip, p3, halo);

        line(consumer, matrix, tip, p1, color);
        line(consumer, matrix, tip, p2, color);
        line(consumer, matrix, tip, p3, color);
        line(consumer, matrix, p1, p2, color);
        line(consumer, matrix, p2, p3, color);
        line(consumer, matrix, p3, p1, color);
    }
}
