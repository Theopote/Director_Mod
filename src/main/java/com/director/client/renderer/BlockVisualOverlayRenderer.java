package com.director.client.renderer;

import com.director.core.container.visibility.BlockVisualStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Set;

/**
 * 方块视觉覆盖层渲染器
 * 
 * <p>负责渲染需要半透明效果的方块覆盖层
 * 当方块被设置为半透明时，在真实方块上方渲染一个半透明的覆盖层
 */
public class BlockVisualOverlayRenderer {
    private static BlockVisualOverlayRenderer instance;
    
    private BlockVisualOverlayRenderer() {
    }
    
    public static BlockVisualOverlayRenderer getInstance() {
        if (instance == null) {
            instance = new BlockVisualOverlayRenderer();
        }
        return instance;
    }
    
    /**
     * 渲染半透明方块覆盖层
     * 
     * <p>在每帧的世界渲染后调用，为需要半透明的方块渲染覆盖层
     * 
     * @param matrices 变换矩阵
     * @param vertexConsumers 顶点消费者提供者
     * @param camera 摄像机位置
     */
    public void renderOverlays(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Vec3d camera) {
        BlockVisualStateManager manager = BlockVisualStateManager.getInstance();
        if (!manager.hasActiveOverrides()) {
            return;
        }
        Set<BlockPos> overlayBlocks = manager.getOverlayBlocks();
        
        if (overlayBlocks.isEmpty()) {
            return;
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.getCameraEntity() == null) {
            return;
        }
        
        // 获取半透明渲染层
        RenderLayer overlayLayer = RenderLayers.getSelectionFillLayer(); // 使用现有的半透明层
        
        VertexConsumer buffer = vertexConsumers.getBuffer(overlayLayer);
        
        // 渲染每个需要半透明的方块
        for (BlockPos pos : overlayBlocks) {
            float alpha = manager.getBlockAlpha(pos);
            if (alpha >= 1.0f || alpha <= 0f) {
                continue; // 跳过完全不透明或完全透明的方块
            }
            
            // 计算方块相对于摄像机的位置
            double dx = pos.getX() - camera.x;
            double dy = pos.getY() - camera.y;
            double dz = pos.getZ() - camera.z;
            
            // 如果方块太远，跳过渲染（性能优化）
            double distanceSq = dx * dx + dy * dy + dz * dz;
            if (distanceSq > 256 * 256) { // 16个区块的距离
                continue;
            }
            
            // 渲染半透明覆盖层
            renderBlockOverlay(matrices, buffer, pos, alpha);
        }
    }
    
    /**
     * 渲染单个方块的半透明覆盖层
     * 
     * @param matrices 变换矩阵
     * @param buffer 顶点缓冲区
     * @param pos 方块位置
     * @param alpha 透明度
     */
    private void renderBlockOverlay(MatrixStack matrices, VertexConsumer buffer, BlockPos pos, float alpha) {
        if (buffer == null || pos == null) {
            return;
        }
        
        matrices.push();
        
        // 计算相对于摄像机的偏移
        Vec3d camera = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        float x = (float) (pos.getX() - camera.x);
        float y = (float) (pos.getY() - camera.y);
        float z = (float) (pos.getZ() - camera.z);
        
        // 计算颜色（白色，带透明度）
        float r = 1.0f;
        float g = 1.0f;
        float b = 1.0f;

        // 渲染方块的6个面（稍微缩小以避免Z-fighting）
        float offset = 0.001f;
        float size = 1.0f - offset * 2;
        
        net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(
            offset, offset, offset,
            offset + size, offset + size, offset + size
        );
        
        // 使用现有的渲染方法
        renderQuad(buffer, matrices.peek().getPositionMatrix(), box, new float[]{r, g, b, alpha}, 0, 1, 2, 3, x, y, z); // 底面
        renderQuad(buffer, matrices.peek().getPositionMatrix(), box, new float[]{r, g, b, alpha}, 4, 5, 6, 7, x, y, z); // 顶面
        renderQuad(buffer, matrices.peek().getPositionMatrix(), box, new float[]{r, g, b, alpha}, 0, 1, 5, 4, x, y, z); // 前面
        renderQuad(buffer, matrices.peek().getPositionMatrix(), box, new float[]{r, g, b, alpha}, 2, 3, 7, 6, x, y, z); // 后面
        renderQuad(buffer, matrices.peek().getPositionMatrix(), box, new float[]{r, g, b, alpha}, 0, 4, 7, 3, x, y, z); // 左面
        renderQuad(buffer, matrices.peek().getPositionMatrix(), box, new float[]{r, g, b, alpha}, 1, 2, 6, 5, x, y, z); // 右面
        
        matrices.pop();
    }
    
    /**
     * 渲染四边形面
     */
    private void renderQuad(VertexConsumer consumer, org.joml.Matrix4f matrix, net.minecraft.util.math.Box box, 
                           float[] color, int v1, int v2, int v3, int v4, float offsetX, float offsetY, float offsetZ) {
        double[][] vertices = {
            {box.minX, box.minY, box.minZ}, // 0
            {box.maxX, box.minY, box.minZ}, // 1
            {box.maxX, box.maxY, box.minZ}, // 2
            {box.minX, box.maxY, box.minZ}, // 3
            {box.minX, box.minY, box.maxZ}, // 4
            {box.maxX, box.minY, box.maxZ}, // 5
            {box.maxX, box.maxY, box.maxZ}, // 6
            {box.minX, box.maxY, box.maxZ}  // 7
        };
        
        // 计算法向量（简单使用向上方向）
        float nx = 0f;
        float ny = 1f;
        float nz = 0f;
        
        consumer.vertex(matrix, (float)vertices[v1][0] + offsetX, (float)vertices[v1][1] + offsetY, (float)vertices[v1][2] + offsetZ)
                .color(color[0], color[1], color[2], color[3])
                .texture(0.5f, 0.5f)
                .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                .light(net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(nx, ny, nz);
        
        consumer.vertex(matrix, (float)vertices[v2][0] + offsetX, (float)vertices[v2][1] + offsetY, (float)vertices[v2][2] + offsetZ)
                .color(color[0], color[1], color[2], color[3])
                .texture(0.5f, 0.5f)
                .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                .light(net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(nx, ny, nz);
        
        consumer.vertex(matrix, (float)vertices[v3][0] + offsetX, (float)vertices[v3][1] + offsetY, (float)vertices[v3][2] + offsetZ)
                .color(color[0], color[1], color[2], color[3])
                .texture(0.5f, 0.5f)
                .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                .light(net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(nx, ny, nz);
        
        consumer.vertex(matrix, (float)vertices[v4][0] + offsetX, (float)vertices[v4][1] + offsetY, (float)vertices[v4][2] + offsetZ)
                .color(color[0], color[1], color[2], color[3])
                .texture(0.5f, 0.5f)
                .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                .light(net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(nx, ny, nz);
    }
}

