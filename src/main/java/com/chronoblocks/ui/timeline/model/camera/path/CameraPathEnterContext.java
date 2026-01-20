package com.chronoblocks.ui.timeline.model.camera.path;

import net.minecraft.util.math.Vec3d;

/**
 * CameraPathEnterContext - 摄像机路径进入上下文
 * 
 * <p>当时间轴"刚进入"一个 PathClip 时，传递给 onEnter() 的上下文信息。
 * 包含进入前一帧的摄像机状态，用于 PathClip 对齐到当前摄像机位置。
 * 
 * <p>设计原则：
 * <ul>
 *   <li>只读上下文：避免 PathClip 修改外部状态</li>
 *   <li>最小信息集：只包含对齐所需的位置和朝向信息</li>
 *   <li>支持多种朝向表示：lookAt（目标点）和 yaw/pitch（欧拉角）</li>
 *   <li>未来可扩展：可以添加速度、加速度等信息（如果需要）</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 2.0
 */
public final class CameraPathEnterContext {
    
    /** 进入前一帧的摄像机位置（世界坐标） */
    public final Vec3d position;
    
    /** 进入前一帧的摄像机注视点（LookAt，世界坐标） */
    public final Vec3d lookAt;
    
    /** 进入前一帧的摄像机偏航角（度，-180 到 180） */
    public final float yaw;
    
    /** 进入前一帧的摄像机俯仰角（度，-90 到 90） */
    public final float pitch;
    
    /**
     * 构造函数（使用位置和 LookAt）
     * 
     * @param position 摄像机位置
     * @param lookAt 摄像机注视点
     */
    public CameraPathEnterContext(Vec3d position, Vec3d lookAt) {
        this.position = position != null ? position : Vec3d.ZERO;
        this.lookAt = lookAt != null ? lookAt : Vec3d.ZERO;
        
        // 从 lookAt 计算 yaw/pitch
        Vec3d dir = this.lookAt.subtract(this.position);
        double len = dir.length();
        if (len > 1e-6) {
            Vec3d normalized = dir.multiply(1.0 / len);
            this.yaw = (float) Math.toDegrees(Math.atan2(-normalized.x, normalized.z));
            this.pitch = (float) Math.toDegrees(Math.asin(-normalized.y));
        } else {
            this.yaw = 0f;
            this.pitch = 0f;
        }
    }
    
    /**
     * 构造函数（使用位置、yaw 和 pitch）
     * 
     * @param position 摄像机位置
     * @param yaw 摄像机偏航角（度）
     * @param pitch 摄像机俯仰角（度）
     */
    public CameraPathEnterContext(Vec3d position, float yaw, float pitch) {
        this.position = position != null ? position : Vec3d.ZERO;
        this.yaw = yaw;
        this.pitch = pitch;
        
        // 从 yaw/pitch 计算 lookAt
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double cosPitch = Math.cos(pitchRad);
        this.lookAt = this.position.add(
            -Math.sin(yawRad) * cosPitch * 10.0,
            -Math.sin(pitchRad) * 10.0,
            Math.cos(yawRad) * cosPitch * 10.0
        );
    }
    
    /**
     * 从 yaw/pitch 计算 forward 方向向量（归一化）
     */
    public Vec3d getForward() {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double cosPitch = Math.cos(pitchRad);
        return new Vec3d(
            -Math.sin(yawRad) * cosPitch,
            -Math.sin(pitchRad),
            Math.cos(yawRad) * cosPitch
        );
    }
    
    @Override
    public String toString() {
        return String.format("CameraPathEnterContext{pos=(%.2f, %.2f, %.2f), lookAt=(%.2f, %.2f, %.2f), yaw=%.1f°, pitch=%.1f°}",
                position.x, position.y, position.z,
                lookAt.x, lookAt.y, lookAt.z,
                yaw, pitch);
    }
}
