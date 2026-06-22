package com.director.client.renderer;

import com.director.core.DirectorServices;
import com.director.core.container.Container;
import com.director.core.container.ContainerManager;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import com.director.client.settings.RenderSettings;
import org.lwjgl.opengl.GL11;

/**
 * 容器边界呼吸边框渲染器
 * 
 * <p>当容器处于激活状态且可见时，在世界中渲染呼吸边框高亮效果
 * 
 * <p>特性：
 * <ul>
 *   <li>呼吸动画 - alpha = 0.6 + 0.4*sin(time*ω)</li>
 *   <li>彩虹渐变 - HSV(time*0.1, 0.8, 1.0)</li>
 *   <li>平滑线条 - 线宽可配置</li>
 *   <li>相机相对坐标渲染</li>
 * </ul>
 * 
 * @author Director Team
 * @version 5.0
 */
public final class ContainerBoundsRenderer {
    
    private static final MinecraftClient MC = MinecraftClient.getInstance();
    // 全局设置中心（新系统优先读取）
    private static final RenderSettings settings = RenderSettings.get();
    
    // ========== 渲染参数 ==========
    private static final float BASE_ALPHA = 0.6f;
    private static final float ALPHA_AMPLITUDE = 0.4f;
    private static final float BREATHING_SPEED = 0.05f; // 呼吸速度 (radians per tick)
    // 已弃用：彩虹/固定线宽逻辑改由 RenderSettings 控制
    
    // ========== 时间追踪 ==========
    private static long lastUpdateTime = 0;
    private static float animationTime = 0.0f;
    
    private ContainerBoundsRenderer() {}
    
    /**
     * 渲染所有激活容器的边界框
     * 
     * @param matrices 矩阵栈
     * @param vertexConsumers 顶点消费者提供者
     */
    public static void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        if (MC.world == null || MC.player == null) {
            return;
        }
        if (!settings.container_showOutline) {
            return;
        }
        
        ContainerManager manager = DirectorServices.get().containers();
        if (manager == null) {
            return;
        }
        
        Container activeContainer = manager.getActive();
        if (activeContainer == null) {
            return;
        }
        
        // 只有在容器可见状态时才渲染边框
        if (!activeContainer.isStateVisible()) {
            return;
        }
        
        // 更新动画时间
        updateAnimationTime();
        
        // 渲染容器边界框
        renderContainerBounds(matrices, vertexConsumers, activeContainer);
    }
    
    /**
     * 渲染单个容器的边界框
     */
    private static void renderContainerBounds(MatrixStack matrices, 
                                             VertexConsumerProvider vertexConsumers,
                                             Container container) {
        if (container.getBlockCount() == 0) {
            return;
        }
        
        // 获取容器边界框
        Container.BoundingBox bbox = container.getBoundingBox();
        BlockPos min = bbox.getMin();
        BlockPos max = bbox.getMax();
        
        // 转换为渲染坐标（扩展1个单位以包含整个方块）
        double minX = min.getX();
        double minY = min.getY();
        double minZ = min.getZ();
        double maxX = max.getX() + 1.0;
        double maxY = max.getY() + 1.0;
        double maxZ = max.getZ() + 1.0;
        
        // 计算相机偏移
        Vec3d cameraPos = MC.gameRenderer.getCamera().getPos();
        
        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        
        // 创建Box并渲染
        Box box = new Box(minX, minY, minZ, maxX, maxY, maxZ);
        renderAnimatedBox(matrices, vertexConsumers, box);
        
        matrices.pop();
    }
    
    /**
     * 渲染带动画效果的边界框
     */
    private static void renderAnimatedBox(MatrixStack matrices, 
                                         VertexConsumerProvider vertexConsumers,
                                         Box box) {
        // 计算呼吸动画的alpha叠加值
        float breathingPhase = (float) Math.sin(animationTime * BREATHING_SPEED);
        float alphaMod = BASE_ALPHA + ALPHA_AMPLITUDE * breathingPhase;

        // ✅ 直接从全局设置读取颜色和线宽（实时生效）
        float[] color = settings.container_outlineColor;
        float r = color[0];
        float g = color[1];
        float b = color[2];
        float a = color[3];
        float alpha = Math.max(0.0f, Math.min(1.0f, a * alphaMod));
        
        // 设置渲染状态
        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager._disableCull();
        GlStateManager._depthMask(false);
        float lw = Math.max(1.0f, Math.min(settings.container_outlineWidth, 6.0f));
        RenderSystem.lineWidth(lw);
        
        // 获取线条渲染层
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getLines());
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        
        // 渲染12条边
        drawBoxEdges(consumer, matrix, box, r, g, b, alpha);
        
        // 恢复渲染状态
        RenderSystem.lineWidth(1.0f);
        GlStateManager._depthMask(true);
        GlStateManager._enableCull();
        GlStateManager._disableBlend();
    }
    
    /**
     * 绘制边界框的12条边
     */
    private static void drawBoxEdges(VertexConsumer consumer, Matrix4f matrix, Box box,
                                     float r, float g, float b, float alpha) {
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;
        
        // 底部4条边
        drawLine(consumer, matrix, minX, minY, minZ, maxX, minY, minZ, r, g, b, alpha);
        drawLine(consumer, matrix, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, alpha);
        drawLine(consumer, matrix, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, alpha);
        drawLine(consumer, matrix, minX, minY, maxZ, minX, minY, minZ, r, g, b, alpha);
        
        // 顶部4条边
        drawLine(consumer, matrix, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, alpha);
        drawLine(consumer, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, alpha);
        drawLine(consumer, matrix, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, alpha);
        drawLine(consumer, matrix, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, alpha);
        
        // 垂直4条边
        drawLine(consumer, matrix, minX, minY, minZ, minX, maxY, minZ, r, g, b, alpha);
        drawLine(consumer, matrix, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, alpha);
        drawLine(consumer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, alpha);
        drawLine(consumer, matrix, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, alpha);
    }
    
    /**
     * 绘制单条线段
     */
    private static void drawLine(VertexConsumer consumer, Matrix4f matrix,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float r, float g, float b, float alpha) {
        // 计算法线（用于正确的光照，虽然线条通常不需要）
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        
        if (length > 0) {
            dx /= length;
            dy /= length;
            dz /= length;
        }
        
        consumer.vertex(matrix, x1, y1, z1)
                .color(r, g, b, alpha)
                .normal(dx, dy, dz);
        
        consumer.vertex(matrix, x2, y2, z2)
                .color(r, g, b, alpha)
                .normal(dx, dy, dz);
    }
    
    /**
     * 更新动画时间
     */
    private static void updateAnimationTime() {
        long currentTime = System.currentTimeMillis();
        
        if (lastUpdateTime == 0) {
            lastUpdateTime = currentTime;
            return;
        }
        
        long deltaMs = currentTime - lastUpdateTime;
        lastUpdateTime = currentTime;
        
        // 将毫秒转换为tick (假设60 FPS = 16.67ms per tick)
        float deltaTicks = deltaMs / 16.67f;
        animationTime += deltaTicks;
        
        // 防止溢出（每1000秒循环一次）
        if (animationTime > 60000) {
            animationTime = 0;
        }
    }
    
    /**
     * 重置动画状态
     */
    public static void reset() {
        animationTime = 0.0f;
        lastUpdateTime = 0;
    }
}

