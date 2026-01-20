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
 *   <li>未来可扩展：可以添加速度、加速度等信息（如果需要）</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public final class CameraPathEnterContext {
    
    /** 进入前一帧的摄像机位置（世界坐标） */
    public final Vec3d position;
    
    /** 进入前一帧的摄像机注视点（LookAt，世界坐标） */
    public final Vec3d lookAt;
    
    /**
     * 构造函数
     * 
     * @param position 摄像机位置
     * @param lookAt 摄像机注视点
     */
    public CameraPathEnterContext(Vec3d position, Vec3d lookAt) {
        this.position = position != null ? position : Vec3d.ZERO;
        this.lookAt = lookAt != null ? lookAt : Vec3d.ZERO;
    }
    
    @Override
    public String toString() {
        return String.format("CameraPathEnterContext{pos=(%.2f, %.2f, %.2f), lookAt=(%.2f, %.2f, %.2f)}",
                position.x, position.y, position.z,
                lookAt.x, lookAt.y, lookAt.z);
    }
}
