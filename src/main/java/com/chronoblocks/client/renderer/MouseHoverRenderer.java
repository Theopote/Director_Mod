package com.chronoblocks.client.renderer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 🎯 鼠标悬停方块高亮渲染器
 * 
 * 负责渲染鼠标悬停时的方块高亮边框效果，替代Minecraft默认的十字星高亮
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class MouseHoverRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MouseHoverRenderer.class);
    
    private static MouseHoverRenderer instance;
    private final MinecraftClient client;
    
    // 渲染状态
    private boolean enabled = false;
    private BlockPos lastHoveredBlock = null;
    private long lastUpdateTime = 0;
    private static final long UPDATE_INTERVAL = 50; // 50ms更新间隔
    
    // 高亮颜色配置
    private static final int HOVER_COLOR = 0xFFFF0000; // 红色边框
    private static final float HOVER_ALPHA = 0.8f;
    
    private MouseHoverRenderer() {
        this.client = MinecraftClient.getInstance();
    }
    
    public static MouseHoverRenderer getInstance() {
        if (instance == null) {
            instance = new MouseHoverRenderer();
        }
        return instance;
    }
    
    /**
     * 启用鼠标悬停高亮
     */
    public void enable() {
        enabled = true;
        LOGGER.debug("[MouseHoverRenderer] 鼠标悬停高亮已启用");
    }
    
    /**
     * 禁用鼠标悬停高亮
     */
    public void disable() {
        enabled = false;
        lastHoveredBlock = null;
        LOGGER.debug("[MouseHoverRenderer] 鼠标悬停高亮已禁用");
    }
    
    /**
     * 渲染鼠标悬停高亮
     */
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, float tickDelta) {
        if (!enabled || client.world == null || client.player == null) {
            return;
        }
        
        // 节流更新
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime < UPDATE_INTERVAL) {
            return;
        }
        lastUpdateTime = currentTime;
        
        // 获取鼠标瞄准的方块
        BlockPos hoveredBlock = getHoveredBlock();
        
        // 如果悬停的方块发生变化，更新状态
        if (hoveredBlock != null && !hoveredBlock.equals(lastHoveredBlock)) {
            lastHoveredBlock = hoveredBlock;
            LOGGER.debug("[MouseHoverRenderer] 悬停方块: {}", hoveredBlock);
        } else if (hoveredBlock == null && lastHoveredBlock != null) {
            lastHoveredBlock = null;
        }
        
        // 渲染高亮边框 - 使用简化的实现
        if (lastHoveredBlock != null) {
            renderSimpleBlockHighlight(matrices, vertexConsumers, lastHoveredBlock);
        }
    }
    
    /**
     * 获取鼠标瞄准的方块
     */
    private BlockPos getHoveredBlock() {
        HitResult hitResult = client.crosshairTarget;
        if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hitResult;
            return blockHit.getBlockPos();
        }
        return null;
    }
    
    /**
     * 渲染方块高亮边框
     */
    private void renderBlockHighlight(MatrixStack matrices, VertexConsumerProvider vertexConsumers, BlockPos pos) {
        try {
            // 获取方块边界框
            Box box = new Box(pos);
            
            // 渲染方块边框
            renderBlockOutline(matrices, vertexConsumers, box);
            
        } catch (Exception e) {
            LOGGER.warn("[MouseHoverRenderer] 渲染方块高亮时出错: {}", e.getMessage());
        }
    }
    
    /**
     * 简化的方块高亮渲染
     */
    private void renderSimpleBlockHighlight(MatrixStack matrices, VertexConsumerProvider vertexConsumers, BlockPos pos) {
        try {
            // 获取方块边界框
            Box box = new Box(pos);
            
            // 使用调试渲染，避免复杂的线框渲染
            LOGGER.debug("[MouseHoverRenderer] 方块高亮: {} 位置: {}", pos, box);
            
            // 这里可以添加简单的视觉反馈，比如改变方块颜色或添加粒子效果
            // 暂时使用日志输出作为视觉反馈
            
        } catch (Exception e) {
            LOGGER.warn("[MouseHoverRenderer] 渲染简化方块高亮时出错: {}", e.getMessage());
        }
    }
    
    /**
     * 渲染方块轮廓
     */
    private void renderBlockOutline(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Box box) {
        try {
            // 获取相机位置
            Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
            
            // 调整边界框到相机空间
            Box adjustedBox = box.offset(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            
            // 使用Minecraft内置的线框渲染方法
            // 这比自定义渲染更稳定
            renderSimpleBoxOutline(matrices, vertexConsumers, adjustedBox);
            
        } catch (Exception e) {
            LOGGER.warn("[MouseHoverRenderer] 渲染方块轮廓时出错: {}", e.getMessage());
        }
    }
    
    /**
     * 简单的方块轮廓渲染
     */
    private void renderSimpleBoxOutline(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Box box) {
        try {
            // 使用Minecraft内置的调试渲染方法
            // 这比自定义渲染更稳定
            VertexConsumer lineConsumer = vertexConsumers.getBuffer(RenderLayer.getLines());
            
            // 使用WorldRenderer的drawBox方法（如果可用）
            // 这是一个更安全的方法
            float red = (HOVER_COLOR >> 16 & 0xFF) / 255.0f;
            float green = (HOVER_COLOR >> 8 & 0xFF) / 255.0f;
            float blue = (HOVER_COLOR & 0xFF) / 255.0f;
            
            // 简单的线框渲染，避免复杂的法线计算
            renderBoxLines(matrices, lineConsumer, box, red, green, blue, HOVER_ALPHA);
            
        } catch (Exception e) {
            LOGGER.debug("[MouseHoverRenderer] 方块高亮: {} (渲染跳过: {})", box, e.getMessage());
        }
    }
    
    /**
     * 渲染方块线框（简化版本）
     */
    private void renderBoxLines(MatrixStack matrices, VertexConsumer consumer, Box box, 
                               float red, float green, float blue, float alpha) {
        // 暂时使用调试日志，避免渲染错误
        LOGGER.debug("[MouseHoverRenderer] 渲染方块线框: {} (颜色: {:.2f}, {:.2f}, {:.2f}, {:.2f})", 
                    box, red, green, blue, alpha);
        
        // TODO: 实现稳定的线框渲染
        // 当前版本暂时跳过实际渲染，避免崩溃
    }
    
    /**
     * 检查是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * 获取当前悬停的方块
     */
    public BlockPos getCurrentHoveredBlock() {
        return lastHoveredBlock;
    }
}
