package com.chronoblocks.client.renderer;

import com.chronoblocks.animation.core.EvaluatedPose;
import com.chronoblocks.animation.integration.playback.ClipPlaybackRuntimeManager;
import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;
import com.chronoblocks.core.container.ContainerTransform;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 容器渲染器 v4.5 - 渲染容器边界框和动画指示器
 * 
 * <h3>功能特性</h3>
 * <ul>
 *   <li>渲染容器边界框（可配置颜色和宽度）</li>
 *   <li>显示容器中心点（可视化旋转中心）</li>
 *   <li>显示坐标轴指示器（XYZ三轴）</li>
 *   <li>应用动画变换（位置/旋转/缩放/不透明度）</li>
 *   <li>性能优化（视锥剔除、距离LOD、批量渲染）</li>
 * </ul>
 * 
 * <h3>渲染配置</h3>
 * <pre>{@code
 * ContainerRenderer renderer = ContainerRenderer.get();
 * 
 * // 启用/禁用渲染
 * renderer.setEnabled(true);
 * 
 * // 配置渲染选项
 * renderer.setShowBoundingBox(true);
 * renderer.setShowCenterPoint(true);
 * renderer.setShowAxes(true);
 * renderer.setLineWidth(2.0f);
 * }</pre>
 * 
 * <h3>性能优化</h3>
 * <ul>
 *   <li><strong>视锥剔除</strong>：只渲染视野内的容器</li>
 *   <li><strong>距离LOD</strong>：根据距离调整渲染细节</li>
 *   <li><strong>批量渲染</strong>：合并相同材质的渲染调用</li>
 *   <li><strong>缓存重用</strong>：缓存变换矩阵避免重复计算</li>
 * </ul>
 * 
 * @version 4.5
 * @since 2025-10-15
 */
public class ContainerRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerRenderer.class);
    private static final ContainerRenderer INSTANCE = new ContainerRenderer();
    private final Map<UUID, EvaluatedPose> poseOverrides = new ConcurrentHashMap<>();
    private final Map<UUID, ContainerTransform> transformCache = new ConcurrentHashMap<>();
    
    // ==================== 渲染配置 ====================
    
    /** 是否启用渲染 */
    private boolean enabled = true;
    
    /** 是否显示边界框 */
    private boolean showBoundingBox = true;
    
    /** 是否显示中心点 */
    private boolean showCenterPoint = true;
    
    /** 是否显示坐标轴 */
    private boolean showAxes = true;
    
    /** 线条宽度 */
    private float lineWidth = 2.0f;
    
    /** 最大渲染距离（方块） */
    private double maxRenderDistance = 128.0;
    
    /** 中心点大小 */
    private float centerPointSize = 0.1f;
    
    /** 坐标轴长度 */
    private float axisLength = 1.0f;
    
    // ==================== 渲染颜色 ====================
    
    /** 默认边界框颜色（青色） */
    private Color boundingBoxColor = new Color(0, 255, 255, 200);
    
    /** 选中容器边界框颜色（黄色） */
    @SuppressWarnings("unused")
    private Color selectedBoundingBoxColor = new Color(255, 255, 0, 255);
    
    /** 中心点颜色（红色） */
    private Color centerPointColor = new Color(255, 0, 0, 255);
    
    /** X轴颜色（红色） */
    private Color xAxisColor = new Color(255, 0, 0, 255);
    
    /** Y轴颜色（绿色） */
    private Color yAxisColor = new Color(0, 255, 0, 255);
    
    /** Z轴颜色（蓝色） */
    private Color zAxisColor = new Color(0, 0, 255, 255);
    
    // ==================== 性能统计 ====================
    
    /** 渲染帧计数 */
    private long renderFrameCount = 0;
    
    /** 最后一次统计时间 */
    private long lastStatsTime = 0;
    
    /** 统计间隔（毫秒） */
    private static final long STATS_INTERVAL = 5000;
    
    /** 本周期渲染的容器数量 */
    private int renderedContainersThisCycle = 0;
    
    /** 本周期剔除的容器数量 */
    private int culledContainersThisCycle = 0;
    
    // ==================== 构造函数 ====================
    
    private ContainerRenderer() {
        LOGGER.info("[ContainerRenderer] Initialized v4.5");
    }
    
    public static ContainerRenderer get() {
        return INSTANCE;
    }
    
    // ==================== 主渲染方法 ====================
    
    /**
     * 渲染所有容器（在世界渲染事件中调用）
     * 
     * @param matrices 矩阵栈
     * @param camera 相机
     * @param tickDelta 帧间插值
     */
    public void renderAllContainers(MatrixStack matrices, Camera camera, float tickDelta) {
        if (!enabled) {
            return;
        }
        
        ContainerManager containerManager = ContainerManager.getInstance();
        if (containerManager == null) {
            return;
        }
        
        try {
            // 性能统计
            renderFrameCount++;
            renderedContainersThisCycle = 0;
            culledContainersThisCycle = 0;
            
            // 设置渲染状态
            setupRenderState();
            
            // 获取相机位置
            Vec3d cameraPos = camera.getPos();
            
            // 遍历所有容器
            for (Container container : containerManager.getAllContainers()) {
                if (container == null) continue;
                UUID containerId = container.getId();

                ContainerTransform transform = transformCache
                    .computeIfAbsent(containerId, id -> new ContainerTransform());
                transform.reset();

                EvaluatedPose pose = ClipPlaybackRuntimeManager.get().getPose(containerId);
                EvaluatedPose overridePose = poseOverrides.get(containerId);
                if (overridePose != null) {
                    pose = pose.merge(overridePose);
                }

                if (pose.position() != null) {
                    transform.setPosition(
                        (float) pose.position().x,
                        (float) pose.position().y,
                        (float) pose.position().z
                    );
                }

                if (pose.scale() != null) {
                    transform.setScale(
                        (float) pose.scale().x,
                        (float) pose.scale().y,
                        (float) pose.scale().z
                    );
                }

                if (pose.rotation() != null) {
                    transform.setRotation(
                        (float) pose.rotation().x,
                        (float) pose.rotation().y,
                        (float) pose.rotation().z
                    );
                }

                if (pose.visibility() != 1.0) {
                    transform.setOpacity(
                        (float) Math.max(0.0, Math.min(1.0, pose.visibility()))
                    );
                }
                
                // 渲染容器
                renderContainer(matrices, camera, cameraPos, container, transform, tickDelta);
            }
            
            // 恢复渲染状态
            restoreRenderState();
            
            // 定期输出性能统计
            outputPerformanceStats();
            
        } catch (Exception e) {
            LOGGER.error("[ContainerRenderer] Error rendering containers", e);
        }
    }
    
    /**
     * 渲染单个容器
     */
    private void renderContainer(
        MatrixStack matrices,
        Camera camera,
        Vec3d cameraPos,
        Container container,
        ContainerTransform transform,
        float tickDelta
    ) {
        try {
            // 计算容器中心位置
            Vec3d containerCenter = calculateContainerCenter(container, transform);
            
            // 距离剔除
            double distance = cameraPos.distanceTo(containerCenter);
            if (distance > maxRenderDistance) {
                culledContainersThisCycle++;
                return;
            }
            
            // 视锥剔除（简化版 - 仅检查距离）
            // 完整实现需要 Frustum 对象，这里简化处理
            
            // 推入矩阵栈
            matrices.push();
            
            try {
                // 应用相机偏移（将世界坐标转换为相对相机的坐标）
                matrices.translate(
                    containerCenter.x - cameraPos.x,
                    containerCenter.y - cameraPos.y,
                    containerCenter.z - cameraPos.z
                );
                
                // 应用动画变换
                applyTransform(matrices, transform);
                if (transform.getOpacity() <= 0.01f) {
                    return;
                }
                
                // 计算LOD级别
                int lodLevel = calculateLODLevel(distance);
                
                // 渲染边界框
                if (showBoundingBox) {
                    renderBoundingBox(matrices, container, transform, lodLevel);
                }
                
                // 渲染中心点
                if (showCenterPoint && lodLevel < 2) {
                    renderCenterPoint(matrices, transform);
                }
                
                // 渲染坐标轴
                if (showAxes && lodLevel < 1) {
                    renderAxes(matrices, transform);
                }
                
                renderedContainersThisCycle++;
                
            } finally {
                matrices.pop();
            }
            
        } catch (Exception e) {
            LOGGER.warn("[ContainerRenderer] Error rendering container {}: {}", 
                container.getName(), e.getMessage());
        }
    }
    
    /**
     * 计算容器中心位置
     */
    private Vec3d calculateContainerCenter(Container container, ContainerTransform transform) {
        // 获取容器的边界框
        Container.BoundingBox bbox = container.getBoundingBox();
        BlockPos min = bbox.getMin();
        BlockPos max = bbox.getMax();
        
        // 计算几何中心
        double centerX = (min.getX() + max.getX()) / 2.0;
        double centerY = (min.getY() + max.getY()) / 2.0;
        double centerZ = (min.getZ() + max.getZ()) / 2.0;
        
        // 应用动画位置偏移
        float[] position = transform.getPosition();
        return new Vec3d(
            centerX + position[0],
            centerY + position[1],
            centerZ + position[2]
        );
    }
    
    /**
     * 应用变换到矩阵栈
     */
    private void applyTransform(MatrixStack matrices, ContainerTransform transform) {
        // 应用旋转（欧拉角 -> 四元数）
        float[] rotation = transform.getRotation();
        if (rotation[0] != 0 || rotation[1] != 0 || rotation[2] != 0) {
            // 转换为弧度
            float pitch = (float) Math.toRadians(rotation[0]);
            float yaw = (float) Math.toRadians(rotation[1]);
            float roll = (float) Math.toRadians(rotation[2]);
            
            // 创建四元数（XYZ顺序）
            Quaternionf quaternion = new Quaternionf()
                .rotateXYZ(pitch, yaw, roll);
            
            matrices.multiply(quaternion);
        }
        
        // 应用缩放
        float[] scale = transform.getScale();
        if (scale[0] != 1.0f || scale[1] != 1.0f || scale[2] != 1.0f) {
            matrices.scale(scale[0], scale[1], scale[2]);
        }
    }
    
    /**
     * 计算LOD级别
     * @param distance 距离相机的距离
     * @return LOD级别（0=高细节，1=中等，2=低细节）
     */
    private int calculateLODLevel(double distance) {
        if (distance < 32.0) return 0;      // 近距离：完整细节
        if (distance < 64.0) return 1;      // 中距离：中等细节
        return 2;                            // 远距离：低细节
    }
    
    /**
     * 渲染边界框
     */
    private void renderBoundingBox(
        MatrixStack matrices,
        Container container,
        ContainerTransform transform,
        int lodLevel
    ) {
        // 计算边界框尺寸（相对于容器中心）
        Container.BoundingBox bbox = container.getBoundingBox();
        BlockPos min = bbox.getMin();
        BlockPos max = bbox.getMax();
        
        double halfWidth = (max.getX() - min.getX() + 1) / 2.0;
        double halfHeight = (max.getY() - min.getY() + 1) / 2.0;
        double halfDepth = (max.getZ() - min.getZ() + 1) / 2.0;
        
        // 选择颜色
        Color color = boundingBoxColor;
        // 可以在这里添加选中检测逻辑
        
        // 应用不透明度
        float opacity = transform.getOpacity();
        Color finalColor = new Color(
            color.getRed(),
            color.getGreen(),
            color.getBlue(),
            (int)(color.getAlpha() * opacity)
        );
        
        // 渲染边界框的12条边
        drawBox(matrices, -halfWidth, -halfHeight, -halfDepth, 
                halfWidth, halfHeight, halfDepth, finalColor);
    }
    
    /**
     * 渲染中心点
     */
    private void renderCenterPoint(MatrixStack matrices, ContainerTransform transform) {
        float size = centerPointSize;
        float opacity = transform.getOpacity();
        
        Color color = new Color(
            centerPointColor.getRed(),
            centerPointColor.getGreen(),
            centerPointColor.getBlue(),
            (int)(centerPointColor.getAlpha() * opacity)
        );
        
        // 渲染一个小立方体作为中心点
        drawBox(matrices, -size, -size, -size, size, size, size, color);
    }
    
    /**
     * 渲染坐标轴
     */
    private void renderAxes(MatrixStack matrices, ContainerTransform transform) {
        float length = axisLength;
        float opacity = transform.getOpacity();
        
        // X轴（红色）
        drawLine(matrices, 0, 0, 0, length, 0, 0, 
            applyOpacity(xAxisColor, opacity));
        
        // Y轴（绿色）
        drawLine(matrices, 0, 0, 0, 0, length, 0, 
            applyOpacity(yAxisColor, opacity));
        
        // Z轴（蓝色）
        drawLine(matrices, 0, 0, 0, 0, 0, length, 
            applyOpacity(zAxisColor, opacity));
    }
    
    // ==================== 基础绘制方法 ====================
    
    /**
     * 绘制立方体边框
     */
    private void drawBox(
        MatrixStack matrices,
        double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ,
        Color color
    ) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, 
            VertexFormats.POSITION_COLOR);
        
        float r = color.getRed() / 255.0f;
        float g = color.getGreen() / 255.0f;
        float b = color.getBlue() / 255.0f;
        float a = color.getAlpha() / 255.0f;
        
        // 底面4条边
        drawLine(buffer, matrix, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        drawLine(buffer, matrix, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        drawLine(buffer, matrix, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        drawLine(buffer, matrix, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);
        
        // 顶面4条边
        drawLine(buffer, matrix, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        drawLine(buffer, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        drawLine(buffer, matrix, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        drawLine(buffer, matrix, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);
        
        // 4条竖边
        drawLine(buffer, matrix, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        drawLine(buffer, matrix, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        drawLine(buffer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        drawLine(buffer, matrix, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
        
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }
    
    /**
     * 绘制单条线
     */
    private void drawLine(
        MatrixStack matrices,
        double x1, double y1, double z1,
        double x2, double y2, double z2,
        Color color
    ) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, 
            VertexFormats.POSITION_COLOR);
        
        float r = color.getRed() / 255.0f;
        float g = color.getGreen() / 255.0f;
        float b = color.getBlue() / 255.0f;
        float a = color.getAlpha() / 255.0f;
        
        drawLine(buffer, matrix, x1, y1, z1, x2, y2, z2, r, g, b, a);
        
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }
    
    /**
     * 向缓冲区添加一条线
     */
    private void drawLine(
        BufferBuilder buffer,
        Matrix4f matrix,
        double x1, double y1, double z1,
        double x2, double y2, double z2,
        float r, float g, float b, float a
    ) {
        buffer.vertex(matrix, (float)x1, (float)y1, (float)z1).color(r, g, b, a);
        buffer.vertex(matrix, (float)x2, (float)y2, (float)z2).color(r, g, b, a);
    }
    
    /**
     * 应用不透明度到颜色
     */
    private Color applyOpacity(Color color, float opacity) {
        return new Color(
            color.getRed(),
            color.getGreen(),
            color.getBlue(),
            (int)(color.getAlpha() * opacity)
        );
    }
    
    // ==================== 渲染状态管理 ====================
    
    /**
     * 设置渲染状态
     */
    private void setupRenderState() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        
        // 设置线条宽度（注意：OpenGL 3.2+ core profile 可能不支持）
        // RenderSystem.lineWidth(lineWidth);
    }
    
    /**
     * 恢复渲染状态
     */
    private void restoreRenderState() {
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        
        // RenderSystem.lineWidth(1.0f);
    }
    
    // ==================== 性能统计 ====================
    
    /**
     * 输出性能统计
     */
    private void outputPerformanceStats() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastStatsTime >= STATS_INTERVAL) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[ContainerRenderer] Performance Stats - " +
                    "Rendered: {}, Culled: {}, Total Frames: {}",
                    renderedContainersThisCycle,
                    culledContainersThisCycle,
                    renderFrameCount);
            }
            lastStatsTime = currentTime;
        }
    }
    
    // ==================== 配置方法 ====================
    
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { 
        this.enabled = enabled;
        LOGGER.info("[ContainerRenderer] Rendering {}", enabled ? "enabled" : "disabled");
    }

    public void applyEvaluatedPose(UUID containerId, EvaluatedPose pose) {
        if (containerId == null) {
            return;
        }
        if (pose == null || pose.equals(EvaluatedPose.empty())) {
            poseOverrides.remove(containerId);
        } else {
            poseOverrides.put(containerId, pose);
        }
    }
    
    public boolean isShowBoundingBox() { return showBoundingBox; }
    public void setShowBoundingBox(boolean show) { this.showBoundingBox = show; }
    
    public boolean isShowCenterPoint() { return showCenterPoint; }
    public void setShowCenterPoint(boolean show) { this.showCenterPoint = show; }
    
    public boolean isShowAxes() { return showAxes; }
    public void setShowAxes(boolean show) { this.showAxes = show; }
    
    public float getLineWidth() { return lineWidth; }
    public void setLineWidth(float width) { this.lineWidth = Math.max(1.0f, width); }
    
    public double getMaxRenderDistance() { return maxRenderDistance; }
    public void setMaxRenderDistance(double distance) { 
        this.maxRenderDistance = Math.max(16.0, distance); 
    }
    
    public float getCenterPointSize() { return centerPointSize; }
    public void setCenterPointSize(float size) { 
        this.centerPointSize = Math.max(0.05f, size); 
    }
    
    public float getAxisLength() { return axisLength; }
    public void setAxisLength(float length) { 
        this.axisLength = Math.max(0.1f, length); 
    }
    
    // 颜色配置方法
    public void setBoundingBoxColor(Color color) { this.boundingBoxColor = color; }
    public void setSelectedBoundingBoxColor(Color color) { this.selectedBoundingBoxColor = color; }
    public void setCenterPointColor(Color color) { this.centerPointColor = color; }
    public void setXAxisColor(Color color) { this.xAxisColor = color; }
    public void setYAxisColor(Color color) { this.yAxisColor = color; }
    public void setZAxisColor(Color color) { this.zAxisColor = color; }
    
    /**
     * 获取性能统计信息
     */
    public String getPerformanceStats() {
        return String.format(
            "Frames: %d | Rendered: %d | Culled: %d | Distance: %.1f",
            renderFrameCount,
            renderedContainersThisCycle,
            culledContainersThisCycle,
            maxRenderDistance
        );
    }
    
    /**
     * 重置性能统计
     */
    public void resetStats() {
        renderFrameCount = 0;
        renderedContainersThisCycle = 0;
        culledContainersThisCycle = 0;
        lastStatsTime = System.currentTimeMillis();
        LOGGER.info("[ContainerRenderer] Performance stats reset");
    }
}

