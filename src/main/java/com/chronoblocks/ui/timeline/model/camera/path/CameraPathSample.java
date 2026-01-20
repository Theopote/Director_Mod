package com.chronoblocks.ui.timeline.model.camera.path;

import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * CameraPathSample - 摄像机路径采样结果
 * 
 * <p>Path Clip 计算后输出的结果，包含摄像机的位置和朝向信息。
 * 
 * <p>设计说明：
 * <ul>
 *   <li>使用 LookAt 点而非旋转角度，更直观且便于计算</li>
 *   <li>播放系统可以根据 position 和 lookAt 计算旋转角度</li>
 *   <li>支持混合多个 Path Clip（未来扩展）</li>
 *   <li>lookAt 可以为 null，表示"沿用上一帧/关键帧的朝向"（用于 Dolly/Crane 等保持构图的镜头）</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 2.0
 */
public class CameraPathSample {
    
    /** 摄像机位置（世界坐标） */
    private final Vec3d position;
    
    /** 摄像机注视点（LookAt，世界坐标）。null 表示"沿用上一帧/关键帧的朝向" */
    @Nullable
    private final Vec3d lookAt;
    
    public CameraPathSample(Vec3d position, @Nullable Vec3d lookAt) {
        this.position = position != null ? position : Vec3d.ZERO;
        this.lookAt = lookAt; // ⭐ 允许 null，不要替换为 ZERO
    }
    
    public Vec3d getPosition() {
        return position;
    }
    
    /**
     * 获取 LookAt 点。可能为 null，表示"沿用上一帧/关键帧的朝向"
     */
    @Nullable
    public Vec3d getLookAt() {
        return lookAt;
    }
    
    /**
     * 计算从 position 指向 lookAt 的方向向量（归一化）
     * 
     * <p>⚠️ 注意：如果 lookAt 为 null，此方法会抛出异常。
     * 调用前应先检查 getLookAt() != null。
     * 
     * @throws IllegalStateException 如果 lookAt 为 null
     */
    public Vec3d getDirection() {
        if (lookAt == null) {
            throw new IllegalStateException("Cannot compute direction when lookAt is null. Use getLookAt() to check first.");
        }
        Vec3d dir = lookAt.subtract(position);
        double len = dir.length();
        if (len < 1e-6) {
            return new Vec3d(0, 0, -1); // 默认朝向 -Z
        }
        return dir.multiply(1.0 / len);
    }
    
    /**
     * 检查是否有有效的 LookAt（非 null）
     */
    public boolean hasLookAt() {
        return lookAt != null;
    }
    
    @Override
    public String toString() {
        if (lookAt != null) {
            return String.format("CameraPathSample{pos=(%.2f, %.2f, %.2f), lookAt=(%.2f, %.2f, %.2f)}",
                    position.x, position.y, position.z,
                    lookAt.x, lookAt.y, lookAt.z);
        } else {
            return String.format("CameraPathSample{pos=(%.2f, %.2f, %.2f), lookAt=null}",
                    position.x, position.y, position.z);
        }
    }
}
