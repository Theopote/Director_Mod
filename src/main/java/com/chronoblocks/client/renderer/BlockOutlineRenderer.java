package com.chronoblocks.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * 渲染 ImGui 鼠标射线命中的方块高亮轮廓
 * 
 * 使用 RenderLayer.getLines() 确保正确的渲染状态，这样不需要按F3+B也能看到高亮边框
 */
public final class BlockOutlineRenderer {

    private static final MinecraftClient MC = MinecraftClient.getInstance();

    /**
     * 渲染方块高亮轮廓
     * 使用 RenderLayer.getLines() 替代 DEBUG_LINES 模式，确保在正常渲染时也能正确显示
     */
    public static void renderOutline(MatrixStack matrices, VertexConsumerProvider vertexConsumers, BlockHitResult hit) {
        if (MC.world == null || hit == null || hit.getType() != net.minecraft.util.hit.HitResult.Type.BLOCK)
            return;

        BlockPos pos = hit.getBlockPos();
        // 若该方块已被选择系统高亮，则跳过悬停高亮，避免叠色
        try {
            com.chronoblocks.core.selection.SelectionManager sm = com.chronoblocks.core.selection.SelectionManager.get();
            if (sm != null) {
                com.chronoblocks.core.selection.SelectionState state = sm.getCurrentSelection();
                if (state != null && state.getSelectedBlocks().contains(pos)) {
                    return;
                }
            }
        } catch (Throwable ignored) {}

        Vec3d camPos = MC.gameRenderer.getCamera().getPos();

        Box box = MC.world.getBlockState(pos)
                .getOutlineShape(MC.world, pos)
                .getBoundingBox()
                .offset(pos)
                .offset(-camPos.x, -camPos.y, -camPos.z);

        // ✅ 使用 RenderLayer.getLines() 获取正确的 VertexConsumer
        // 这样可以确保渲染状态正确设置，不需要按F3+B也能看到高亮边框
        VertexConsumer lineConsumer = vertexConsumers.getBuffer(RenderLayer.getLines());
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float r = 0.2f, g = 0.6f, b = 1.0f, a = 1.0f;
        
        // 🔵 设置线条宽度
        RenderSystem.lineWidth(2.0f);
        
        // 绘制方块的12条边
        drawBoxEdges(lineConsumer, matrix, box, r, g, b, a);
        
        // 恢复线条宽度
        RenderSystem.lineWidth(1.0f);
    }
    
    /**
     * 绘制方块的12条边
     */
    private static void drawBoxEdges(VertexConsumer consumer, Matrix4f matrix, Box box, float r, float g, float b, float a) {
        double minX = box.minX, minY = box.minY, minZ = box.minZ;
        double maxX = box.maxX, maxY = box.maxY, maxZ = box.maxZ;
        
        // 底面4条边
        drawLine(consumer, matrix, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        drawLine(consumer, matrix, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        drawLine(consumer, matrix, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        drawLine(consumer, matrix, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);
        
        // 顶面4条边
        drawLine(consumer, matrix, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        drawLine(consumer, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        drawLine(consumer, matrix, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        drawLine(consumer, matrix, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);
        
        // 4条竖边
        drawLine(consumer, matrix, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        drawLine(consumer, matrix, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        drawLine(consumer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        drawLine(consumer, matrix, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }
    
    /**
     * 向 VertexConsumer 添加一条线
     * 注意：RenderLayer.getLines() 需要提供 normal 信息
     */
    private static void drawLine(VertexConsumer consumer, Matrix4f matrix, double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float b, float a) {
        consumer.vertex(matrix, (float)x1, (float)y1, (float)z1).color(r, g, b, a).normal(0, 1, 0);
        consumer.vertex(matrix, (float)x2, (float)y2, (float)z2).color(r, g, b, a).normal(0, 1, 0);
    }
}
