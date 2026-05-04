package com.director.client.renderer;

import com.director.core.selection.SelectionManager;
import com.director.core.selection.tools.FaceHandleManager;
import com.director.client.settings.RenderSettings;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Renders transient interaction hints such as drag arrows or hover indicators.
 */
public final class InteractionHintRenderer {

    private InteractionHintRenderer() {}

    // 全局设置中心（新系统优先读取）
    private static final RenderSettings settings = RenderSettings.get();

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
    }

    private static void drawAxisColoredHandles(MatrixStack matrices,
                                               VertexConsumerProvider consumers,
                                               Vec3d camPos,
                                               Box handleBox,
                                               Integer hoveredFace,
                                               Integer draggingFace) {
        VertexConsumer buffer = consumers.getBuffer(RenderLayers.getLineOverlay());
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        for (int faceIndex = 0; faceIndex < 6; faceIndex++) {
            Vec3d center = OverlayRenderHelper.faceCenter(handleBox, faceIndex);
            Vec3d rel = center.subtract(camPos);

            float[] base = axisBaseColor(faceIndex);
            float[] color;
            if (draggingFace != null && draggingFace == faceIndex) {
                color = new float[]{1.0f, 1.0f, 0.25f, 1.0f};
            } else if (hoveredFace != null && hoveredFace == faceIndex) {
                color = brighten(base);
            } else {
                color = new float[]{base[0], base[1], base[2], 0.65f};
            }

            boolean active = draggingFace != null && draggingFace == faceIndex;
            OverlayRenderHelper.drawHandle(buffer, matrix, rel, color, active);
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

        // ✅ 直接从全局设置读取颜色（实时生效）
        float[] color = settings.tool_outlineColor;
        Vec3d startRel = start.subtract(camPos);
        Vec3d endRel = end.subtract(camPos);
        line(consumer, matrix, startRel, endRel, color);

        Vec3d dir = delta.normalize();
        drawArrowHead(consumer, matrix, endRel, dir, 0.2, color);
    }

    private static void drawBidirectionalArrow(VertexConsumer consumer,
                                               Matrix4f matrix,
                                               Vec3d camPos,
                                               Box box,
                                               int faceIndex) {
        Vec3d faceCenter = OverlayRenderHelper.faceCenter(box, faceIndex);
        Vec3d normal = OverlayRenderHelper.faceNormal(faceIndex).normalize();

        double distance = faceCenter.distanceTo(camPos);
        double distanceMultiplier = Math.max(distance, 8.0) / 15.0;
        double arrowLength = 0.4 * distanceMultiplier;
        double arrowHeadSize = 0.15 * distanceMultiplier;

        float[] color = {1.0f, 1.0f, 1.0f, 0.9f};

        Vec3d arrow1Start = faceCenter.subtract(normal.multiply(arrowLength));
        Vec3d arrow2End = faceCenter.add(normal.multiply(arrowLength));

        Vec3d arrow1StartRel = arrow1Start.subtract(camPos);
        Vec3d arrow1EndRel = faceCenter.subtract(camPos);
        Vec3d arrow2EndRel = arrow2End.subtract(camPos);

        line(consumer, matrix, arrow1StartRel, arrow1EndRel, color);
        line(consumer, matrix, arrow1EndRel, arrow2EndRel, color);

        drawArrowHead(consumer, matrix, arrow1EndRel, normal.multiply(-1), arrowHeadSize, color);
        drawArrowHead(consumer, matrix, arrow2EndRel, normal, arrowHeadSize, color);
    }

    private static float[] axisBaseColor(int faceIndex) {
        return switch (faceIndex) {
            case 0, 1 -> new float[]{1.0f, 0.3f, 0.3f};
            case 2, 3 -> new float[]{0.3f, 1.0f, 0.3f};
            case 4, 5 -> new float[]{0.3f, 0.45f, 1.0f};
            default -> new float[]{0.85f, 0.85f, 0.85f};
        };
    }

    private static float[] brighten(float[] base) {
        return new float[]{
            clamp(base[0] + 0.35f),
            clamp(base[1] + 0.35f),
            clamp(base[2] + 0.35f),
            0.95f
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
            perp1 = dir.crossProduct(up).normalize().multiply(size * 0.5);
            perp2 = dir.crossProduct(perp1).normalize().multiply(size * 0.5);
        } else {
            Vec3d right = new Vec3d(1, 0, 0);
            perp1 = dir.crossProduct(right).normalize().multiply(size * 0.5);
            perp2 = dir.crossProduct(perp1).normalize().multiply(size * 0.5);
        }

        Vec3d p1 = base.add(perp1);
        Vec3d p2 = base.add(perp2);
        Vec3d p3 = base.subtract(perp1).subtract(perp2);

        line(consumer, matrix, tip, p1, color);
        line(consumer, matrix, tip, p2, color);
        line(consumer, matrix, tip, p3, color);
        line(consumer, matrix, p1, p2, color);
        line(consumer, matrix, p2, p3, color);
        line(consumer, matrix, p3, p1, color);
    }
}

