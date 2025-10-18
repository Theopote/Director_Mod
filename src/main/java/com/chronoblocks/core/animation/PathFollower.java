package com.chronoblocks.core.animation;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerTransform;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 路径跟随器 v4.5 - 让容器沿着 Path3D 移动
 * 
 * <h3>功能特性</h3>
 * <ul>
 *   <li>自动沿路径移动容器</li>
 *   <li>可选的自动旋转朝向</li>
 *   <li>速度控制</li>
 *   <li>循环模式</li>
 *   <li>暂停/恢复</li>
 * </ul>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 创建路径
 * Path3D path = new Path3D();
 * path.addControlPoint(new Vec3d(0, 10, 0));
 * path.addControlPoint(new Vec3d(20, 15, 10));
 * 
 * // 创建跟随器
 * PathFollower follower = new PathFollower(path, container);
 * follower.setAutoOrient(true);     // 自动朝向路径方向
 * follower.setLoop(true);            // 循环播放
 * follower.setSpeed(1.0f);           // 正常速度
 * 
 * // 每帧更新
 * follower.update(deltaTime);
 * }</pre>
 * 
 * @version 4.5
 * @since 2025-10-15
 */
public class PathFollower {
    private static final Logger LOGGER = LoggerFactory.getLogger(PathFollower.class);
    
    // ==================== 核心组件 ====================
    
    /** 路径 */
    private Path3D path;
    
    /** 容器 */
    private Container container;
    
    // ==================== 状态 ====================
    
    /** 当前进度 [0.0, 1.0] */
    private float progress = 0.0f;
    
    /** 是否正在播放 */
    private boolean playing = false;
    
    /** 是否循环 */
    private boolean loop = false;
    
    /** 是否自动朝向路径方向 */
    private boolean autoOrient = true;
    
    /** 播放速度（1.0 = 正常速度） */
    private float speed = 1.0f;
    
    /** 持续时间（秒） */
    private float duration = 5.0f;
    
    // ==================== 构造函数 ====================
    
    public PathFollower(Path3D path, Container container) {
        this.path = path;
        this.container = container;
    }
    
    // ==================== 更新方法 ====================
    
    /**
     * 每帧更新
     * 
     * @param deltaTime 帧时间（秒）
     */
    public void update(float deltaTime) {
        if (!playing || path == null || container == null) {
            return;
        }
        
        // 更新进度
        float progressDelta = (deltaTime * speed) / duration;
        progress += progressDelta;
        
        // 处理循环和结束
        if (progress >= 1.0f) {
            if (loop) {
                progress = progress % 1.0f;  // 循环
            } else {
                progress = 1.0f;
                playing = false;  // 停止
                LOGGER.info("[PathFollower] 路径跟随完成");
            }
        }
        
        // 应用变换
        applyTransform();
    }
    
    /**
     * 应用变换到容器
     */
    private void applyTransform() {
        // 采样路径位置
        Vec3d position = path.sample(progress);
        
        // 获取容器变换
        ContainerTransform transform = AnimationApplicator.get()
            .getContainerTransform(container.getId());
        
        if (transform == null) {
            LOGGER.warn("[PathFollower] 容器变换不存在: {}", container.getId());
            return;
        }
        
        // 设置位置
        transform.setPosition(
            (float) position.x,
            (float) position.y,
            (float) position.z
        );
        
        // 自动旋转
        if (autoOrient) {
            Vec3d tangent = path.getTangent(progress);
            float[] rotation = calculateRotationFromDirection(tangent);
            transform.setRotation(rotation[0], rotation[1], rotation[2]);
        }
    }
    
    /**
     * 从方向向量计算旋转角度
     * 
     * @param direction 方向向量（已归一化）
     * @return [yaw, pitch, roll] 欧拉角（度）
     */
    private float[] calculateRotationFromDirection(Vec3d direction) {
        // 计算 yaw（水平旋转）
        float yaw = (float) Math.toDegrees(Math.atan2(direction.x, direction.z));
        
        // 计算 pitch（俯仰）
        float horizontalLength = (float) Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        float pitch = (float) Math.toDegrees(Math.atan2(-direction.y, horizontalLength));
        
        // roll 保持为 0（通常不需要翻滚）
        float roll = 0.0f;
        
        return new float[]{pitch, yaw, roll};
    }
    
    // ==================== 控制方法 ====================
    
    /**
     * 开始播放
     */
    public void play() {
        playing = true;
        LOGGER.debug("[PathFollower] 开始播放");
    }
    
    /**
     * 暂停
     */
    public void pause() {
        playing = false;
        LOGGER.debug("[PathFollower] 已暂停");
    }
    
    /**
     * 停止并重置
     */
    public void stop() {
        playing = false;
        progress = 0.0f;
        LOGGER.debug("[PathFollower] 已停止并重置");
    }
    
    /**
     * 跳转到指定进度
     * 
     * @param progress 进度 [0.0, 1.0]
     */
    public void seek(float progress) {
        this.progress = clamp01(progress);
        applyTransform();
        LOGGER.debug("[PathFollower] 跳转到进度: {}", this.progress);
    }
    
    /**
     * 反向播放
     */
    public void reverse() {
        speed = -speed;
        LOGGER.debug("[PathFollower] 反向播放，速度: {}", speed);
    }
    
    // ==================== Getter/Setter ====================
    
    public Path3D getPath() {
        return path;
    }
    
    public void setPath(Path3D path) {
        this.path = path;
        LOGGER.debug("[PathFollower] 路径已更新");
    }
    
    public Container getContainer() {
        return container;
    }
    
    public void setContainer(Container container) {
        this.container = container;
        LOGGER.debug("[PathFollower] 容器已更新: {}", container.getId());
    }
    
    public float getProgress() {
        return progress;
    }
    
    public boolean isPlaying() {
        return playing;
    }
    
    public boolean isLoop() {
        return loop;
    }
    
    public void setLoop(boolean loop) {
        this.loop = loop;
    }
    
    public boolean isAutoOrient() {
        return autoOrient;
    }
    
    public void setAutoOrient(boolean autoOrient) {
        this.autoOrient = autoOrient;
    }
    
    public float getSpeed() {
        return speed;
    }
    
    public void setSpeed(float speed) {
        this.speed = speed;
    }
    
    public float getDuration() {
        return duration;
    }
    
    public void setDuration(float duration) {
        this.duration = Math.max(0.1f, duration);
    }
    
    // ==================== 辅助方法 ====================
    
    private float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
    
    /**
     * 获取当前在路径上的位置
     */
    public Vec3d getCurrentPosition() {
        return path != null ? path.sample(progress) : Vec3d.ZERO;
    }
    
    /**
     * 获取当前的切线方向
     */
    public Vec3d getCurrentDirection() {
        return path != null ? path.getTangent(progress) : new Vec3d(1, 0, 0);
    }
    
    /**
     * 获取剩余时间
     */
    public float getRemainingTime() {
        return (1.0f - progress) * duration / Math.abs(speed);
    }
}

