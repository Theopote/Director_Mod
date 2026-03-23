package com.director.ui.timeline.model.camera.path;

import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CranePathClip - 升降路径片段
 * 
 * <p>实现"垂直升降"的摄像机路径。
 * 摄像机沿着垂直轴上下移动，用于升降镜头（Crane Shot）。
 * 
 * <p>设计语义：
 * <ul>
 *   <li>路径只影响 Y 坐标</li>
 *   <li>X / Z 永远来自进入时的摄像机位置</li>
 *   <li>LookAt 不变（构图稳定）</li>
 *   <li>起始点与前一段路径无缝衔接</li>
 * </ul>
 * 
 * <p>参数说明：
 * <ul>
 *   <li>heightDelta: 垂直移动距离（blocks），正 = 上升，负 = 下降</li>
 *   <li>alignStartToCurrentPosition: 是否在进入时对齐到当前摄像机位置</li>
 * </ul>
 * 
 * <p>数学实现：
 * <pre>
 * x(t) = startX
 * z(t) = startZ
 * y(t) = startY + heightDelta * t
 * </pre>
 * 
 * <p>其中 t ∈ [0,1] 是 clip 内的归一化时间。
 * LookAt 返回 null，表示"沿用上一帧 / 关键帧 LookAt"，保持构图稳定。
 * 
 * @author Director Team
 * @version 2.0
 */
public class CranePathClip extends CameraPathClip {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(CranePathClip.class);
    
    /** 垂直移动距离（blocks），正 = 上升，负 = 下降 */
    private float heightDelta = 5.0f;
    
    /** 是否在进入时对齐到当前摄像机位置 */
    private boolean alignStartToCurrentPosition = true;
    
    // —— 运行期缓存（非常重要）——
    /** 起始位置（进入时捕获，包含完整的 X/Y/Z 坐标） */
    private Vec3d startPosition;
    
    /**
     * 构造函数
     * 
     * @param name 路径片段名称
     * @param startTime 开始时间（秒）
     * @param endTime 结束时间（秒）
     */
    public CranePathClip(String name, float startTime, float endTime) {
        super(name, startTime, endTime);
    }
    
    @Override
    public CameraPathType getPathType() {
        return CameraPathType.CRANE;
    }
    
    /**
     * ⭐ 当时间轴"刚进入"该 CranePathClip 时调用
     * 
     * <p>如果启用了对齐功能，会捕获当前摄像机位置作为起始点，
     * 实现与前一段路径的无缝接续。
     * 
     * <p>对齐逻辑：
     * <ul>
     *   <li>捕获进入时的完整位置（X/Y/Z）</li>
     *   <li>sample 阶段只改 Y，其它轴保持不变</li>
     *   <li>固定这些值（整个 Clip 生命周期不再变化）</li>
     * </ul>
     */
    @Override
    public void onEnter(CameraPathEnterContext ctx) {
        if (!alignStartToCurrentPosition || ctx == null) {
            return;
        }
        
        // 捕获进入时的完整位置
        this.startPosition = ctx.position;
        
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[CranePathClip] onEnter() 起始位置: {}, 高度变化: {} blocks",
                    startPosition, heightDelta);
        }
    }
    
    @Override
    public CameraPathSample sample(float timeSeconds) {
        if (startPosition == null) {
            // 尚未初始化（理论上不会，但防御性编程）
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("[CranePathClip] sample() 尚未初始化，返回 null");
            }
            return null;
        }
        
        if (!isActiveAt(timeSeconds)) {
            // 不在激活时间范围内，返回边界值
            if (timeSeconds < getStartTime()) {
                return sampleAtNormalizedTime(0f);
            } else {
                return sampleAtNormalizedTime(1f);
            }
        }
        
        float t = getNormalizedTime(timeSeconds);
        return sampleAtNormalizedTime(t);
    }
    
    /**
     * 在归一化时间 [0,1] 处采样
     */
    private CameraPathSample sampleAtNormalizedTime(float t) {
        // 核心公式：只改变 Y 坐标
        double y = startPosition.y + heightDelta * t;
        
        Vec3d pos = new Vec3d(
            startPosition.x,
            y,
            startPosition.z
        );
        
        // LookAt 返回 null → 表示"沿用上一帧 / 关键帧 LookAt"
        // 这正是 Crane 的导演语义：升降但构图不变
        return new CameraPathSample(pos, null);
    }
    
    // ========== Getter / Setter ==========
    
    public float getHeightDelta() {
        return heightDelta;
    }
    
    public void setHeightDelta(float heightDelta) {
        this.heightDelta = heightDelta;
    }
    
    public boolean isAlignStartToCurrentPosition() {
        return alignStartToCurrentPosition;
    }
    
    public void setAlignStartToCurrentPosition(boolean alignStartToCurrentPosition) {
        this.alignStartToCurrentPosition = alignStartToCurrentPosition;
    }
    
    /**
     * 获取当前缓存的起始位置（用于调试）
     */
    public Vec3d getStartPosition() {
        return startPosition;
    }
    
    @Override
    public CameraPathClip copy() {
        CranePathClip copy = new CranePathClip(
                getName() + " (Copy)",
                getStartTime(),
                getEndTime()
        );
        copy.setHeightDelta(heightDelta);
        copy.setAlignStartToCurrentPosition(alignStartToCurrentPosition);
        copy.setEnabled(isEnabled());
        return copy;
    }
    
    @Override
    public String toString() {
        return String.format("CranePathClip{name='%s', time=[%.2f, %.2f], heightDelta=%.2f}",
                getName(), getStartTime(), getEndTime(), heightDelta);
    }
}
