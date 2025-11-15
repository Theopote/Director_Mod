package com.chronoblocks.client.renderer;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

/**
 * 容器高亮渲染器
 * 当容器被选中时，高亮显示容器中的方块边框
 * 
 * @author ChronoBlocks Team
 * @version 5.0
 */
public final class ContainerHighlightRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerHighlightRenderer.class);
    
    private static volatile ContainerHighlightRenderer INSTANCE;
    
    // 高亮颜色（使用容器的标记颜色，如果没有则使用默认颜色）
    private final float[] defaultOutlineColor = {0.4f, 1.0f, 0.5f, 0.9f}; // 绿色边框
    private float outlineWidth = 2.0f;
    private boolean enabled = true;
    
    // 性能控制
    private int maxRenderDistanceSq = 128 * 128; // 最大渲染距离（方块）
    
    private ContainerHighlightRenderer() {
    }
    
    public static ContainerHighlightRenderer getInstance() {
        if (INSTANCE == null) {
            synchronized (ContainerHighlightRenderer.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ContainerHighlightRenderer();
                }
            }
        }
        return INSTANCE;
    }
    
    /**
     * 渲染选中容器的高亮边框
     */
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, float tickDelta) {
        if (!enabled) {
            return;
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            return;
        }
        
        ContainerManager manager = ContainerManager.getInstance();
        if (manager == null) {
            return;
        }
        
        Container selectedContainer = manager.getSelectedContainer();
        if (selectedContainer == null) {
            return;
        }
        
        // 获取容器的方块
        Collection<BlockPos> blocks = selectedContainer.getBlocks();
        if (blocks == null || blocks.isEmpty()) {
            return;
        }
        
        // 获取相机位置
        Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
        BlockPos cameraBlock = new BlockPos(
            (int) Math.floor(cameraPos.x),
            (int) Math.floor(cameraPos.y),
            (int) Math.floor(cameraPos.z)
        );
        
        // 获取容器的颜色（如果有标记颜色则使用，否则使用默认颜色）
        int containerColor = selectedContainer.getColor();
        float[] outlineColor = intColorToFloatArray(containerColor);
        if (outlineColor == null) {
            outlineColor = defaultOutlineColor.clone();
        }
        
        // 设置渲染状态
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.lineWidth(Math.max(1.0f, Math.min(outlineWidth, 4.0f)));
        
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumer lineConsumer = vertexConsumers.getBuffer(RenderLayer.getLines());
        
        try {
            // 渲染每个方块的边框
            int renderedCount = 0;
            for (BlockPos pos : blocks) {
                // 距离剔除
                if (cameraBlock.getSquaredDistance(pos) > maxRenderDistanceSq) {
                    continue;
                }
                
                renderBlockOutline(lineConsumer, matrix, pos, cameraPos, outlineColor);
                renderedCount++;
            }
            
            if (renderedCount > 0) {
                LOGGER.debug("[ContainerHighlightRenderer] 渲染了 {} 个方块的高亮边框", renderedCount);
            }
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.lineWidth(1.0f);
        }
    }
    
    /**
     * 渲染单个方块的边框
     */
    private void renderBlockOutline(VertexConsumer consumer, Matrix4f matrix, BlockPos pos, Vec3d cameraPos, float[] color) {
        // 计算相对于相机的偏移
        double offsetX = pos.getX() - cameraPos.x;
        double offsetY = pos.getY() - cameraPos.y;
        double offsetZ = pos.getZ() - cameraPos.z;
        
        // 创建方块的边界框（稍微扩展以确保可见）
        Box box = new Box(
            offsetX - 0.001, offsetY - 0.001, offsetZ - 0.001,
            offsetX + 1.001, offsetY + 1.001, offsetZ + 1.001
        );
        
        float r = color[0];
        float g = color[1];
        float b = color[2];
        float a = color[3];
        
        // 绘制边框的12条边
        // 底部4条边
        drawLine(consumer, matrix, 
            (float) box.minX, (float) box.minY, (float) box.minZ,
            (float) box.maxX, (float) box.minY, (float) box.minZ, r, g, b, a);
        drawLine(consumer, matrix,
            (float) box.maxX, (float) box.minY, (float) box.minZ,
            (float) box.maxX, (float) box.minY, (float) box.maxZ, r, g, b, a);
        drawLine(consumer, matrix,
            (float) box.maxX, (float) box.minY, (float) box.maxZ,
            (float) box.minX, (float) box.minY, (float) box.maxZ, r, g, b, a);
        drawLine(consumer, matrix,
            (float) box.minX, (float) box.minY, (float) box.maxZ,
            (float) box.minX, (float) box.minY, (float) box.minZ, r, g, b, a);
        
        // 顶部4条边
        drawLine(consumer, matrix,
            (float) box.minX, (float) box.maxY, (float) box.minZ,
            (float) box.maxX, (float) box.maxY, (float) box.minZ, r, g, b, a);
        drawLine(consumer, matrix,
            (float) box.maxX, (float) box.maxY, (float) box.minZ,
            (float) box.maxX, (float) box.maxY, (float) box.maxZ, r, g, b, a);
        drawLine(consumer, matrix,
            (float) box.maxX, (float) box.maxY, (float) box.maxZ,
            (float) box.minX, (float) box.maxY, (float) box.maxZ, r, g, b, a);
        drawLine(consumer, matrix,
            (float) box.minX, (float) box.maxY, (float) box.maxZ,
            (float) box.minX, (float) box.maxY, (float) box.minZ, r, g, b, a);
        
        // 4条垂直边
        drawLine(consumer, matrix,
            (float) box.minX, (float) box.minY, (float) box.minZ,
            (float) box.minX, (float) box.maxY, (float) box.minZ, r, g, b, a);
        drawLine(consumer, matrix,
            (float) box.maxX, (float) box.minY, (float) box.minZ,
            (float) box.maxX, (float) box.maxY, (float) box.minZ, r, g, b, a);
        drawLine(consumer, matrix,
            (float) box.maxX, (float) box.minY, (float) box.maxZ,
            (float) box.maxX, (float) box.maxY, (float) box.maxZ, r, g, b, a);
        drawLine(consumer, matrix,
            (float) box.minX, (float) box.minY, (float) box.maxZ,
            (float) box.minX, (float) box.maxY, (float) box.maxZ, r, g, b, a);
    }
    
    /**
     * 绘制一条线
     */
    private void drawLine(VertexConsumer consumer, Matrix4f matrix,
                         float x1, float y1, float z1,
                         float x2, float y2, float z2,
                         float r, float g, float b, float a) {
        consumer.vertex(matrix, x1, y1, z1)
                .color(r, g, b, a)
                .normal(0, 1, 0);
        consumer.vertex(matrix, x2, y2, z2)
                .color(r, g, b, a)
                .normal(0, 1, 0);
    }
    
    /**
     * 将整数颜色（ARGB）转换为浮点数组（RGBA）
     */
    private float[] intColorToFloatArray(int color) {
        if (color == 0xFFFFFFFF) { // 默认白色，使用默认颜色
            return null;
        }
        
        float a = ((color >> 24) & 0xFF) / 255.0f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        
        // 如果 alpha 为 0，使用默认 alpha
        if (a < 0.01f) {
            a = defaultOutlineColor[3];
        }
        
        return new float[]{r, g, b, a};
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setOutlineWidth(float width) {
        this.outlineWidth = Math.max(1.0f, Math.min(width, 4.0f));
    }
    
    public float getOutlineWidth() {
        return outlineWidth;
    }
    
    public void setMaxRenderDistance(int distance) {
        this.maxRenderDistanceSq = distance * distance;
    }
    
    public int getMaxRenderDistance() {
        return (int) Math.sqrt(maxRenderDistanceSq);
    }
}

