package com.director.timeline.model.camera.path;

import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DollyPathClip - 推拉路径片段
 * 
 * <p>实现"沿直线推拉"的摄像机路径。
 * 摄像机沿着一个明确的方向前后移动，用于推近/拉远镜头。
 * 
 * <p>参数说明：
 * <ul>
 *   <li>distance: 推拉距离（blocks），正 = 向前推，负 = 向后拉</li>
 *   <li>directionMode: 方向模式（CAMERA_FORWARD / TOWARDS_TARGET / FIXED_DIRECTION）</li>
 *   <li>fixedDirection: 固定方向（当 mode = FIXED_DIRECTION 时使用）</li>
 *   <li>alignStartToCurrentPosition: 是否在进入时对齐到当前摄像机位置</li>
 * </ul>
 * 
 * <p>数学实现：
 * <pre>
 * position(t) = startPosition + dollyDirection * (distance * t)
 * </pre>
 * 
 * <p>其中 t ∈ [0,1] 是 clip 内的归一化时间，distance 是总推拉距离。
 * LookAt 返回 null，表示"沿用上一帧 / 关键帧 LookAt"，保持构图稳定。
 * 
 * @author Director Team
 * @version 1.0
 */
public class DollyPathClip extends CameraPathClip {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DollyPathClip.class);
    
    /**
     * Dolly 方向模式枚举
     */
    public enum DirectionMode {
        /** 沿当前摄像机的 forward 方向推 / 拉（最常用） */
        CAMERA_FORWARD,
        
        /** 沿着 LookAt → Camera 的方向（向主体推近/拉远） */
        TOWARDS_TARGET,
        
        /** 使用固定世界方向 */
        FIXED_DIRECTION
    }
    
    /** 推拉距离（blocks），正 = 向前推，负 = 向后拉 */
    private float distance = 5.0f;
    
    /** Dolly 方向模式 */
    private DirectionMode directionMode = DirectionMode.CAMERA_FORWARD;
    
    /** 固定方向（当 mode = FIXED_DIRECTION 时使用） */
    private Vec3d fixedDirection = new Vec3d(0, 0, 1);
    
    /** 是否在进入时对齐到当前摄像机位置 */
    private boolean alignStartToCurrentPosition = true;
    
    // —— 运行期缓存（非常重要）——
    /** 起始点（进入时捕获） */
    private Vec3d startPosition;
    
    /** 实际使用的方向（单位向量） */
    private Vec3d dollyDirection;
    
    /**
     * 构造函数
     * 
     * @param name 路径片段名称
     * @param startTime 开始时间（秒）
     * @param endTime 结束时间（秒）
     */
    public DollyPathClip(String name, float startTime, float endTime) {
        super(name, startTime, endTime);
    }
    
    @Override
    public CameraPathType getPathType() {
        return CameraPathType.DOLLY;
    }
    
    /**
     * ⭐ 当时间轴"刚进入"该 DollyPathClip 时调用
     * 
     * <p>如果启用了对齐功能，会根据当前摄像机位置和方向自动计算起始位置和推拉方向，
     * 实现与前一段路径的无缝接续。
     * 
     * <p>对齐逻辑：
     * <ul>
     *   <li>捕获当前摄像机位置作为起始点</li>
     *   <li>根据 directionMode 决定推拉方向</li>
     *   <li>固定这些值（整个 Clip 生命周期不再变化）</li>
     * </ul>
     */
    @Override
    public void onEnter(CameraPathEnterContext ctx) {
        if (ctx == null) {
            return;
        }

        if (!alignStartToCurrentPosition && startPosition != null && dollyDirection != null) {
            return;
        }

        alignFromContext(ctx);
    }

    /**
     * 从指定摄像机状态重新捕获推拉路径起点和方向。
     * 属性面板的手动对齐需要无视 alignStartToCurrentPosition 开关。
     */
    public void alignFromContext(CameraPathEnterContext ctx) {
        if (ctx == null || ctx.position == null) {
            return;
        }

        this.startPosition = ctx.position;

        switch (directionMode) {
            case CAMERA_FORWARD -> {
                // ⭐ 使用当前摄像机的 forward 方向（镜头朝向）
                // 从 yaw/pitch 计算 forward 向量，而不是从 lookAt
                Vec3d forward = ctx.getForward();
                this.dollyDirection = normalizeSafe(forward);
            }
            
            case TOWARDS_TARGET -> {
                // 沿 Camera → LookAt 方向：正距离 = 向焦点推近，负距离 = 拉远
                Vec3d dir = ctx.lookAt.subtract(ctx.position);
                this.dollyDirection = normalizeSafe(dir);
            }
            
            case FIXED_DIRECTION -> {
                // 使用固定方向
                this.dollyDirection = normalizeSafe(fixedDirection);
            }
        }
        
        // ③ 兜底（防止 NaN）
        if (this.dollyDirection == null) {
            this.dollyDirection = new Vec3d(0, 0, 1);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[DollyPathClip] onEnter() 方向向量无效，使用默认方向 (0, 0, 1)");
            }
        }
        
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[DollyPathClip] onEnter() 起始位置: {}, 方向: {}, 距离: {} blocks",
                    startPosition, dollyDirection, distance);
        }
    }
    
    @Override
    public CameraPathSample sample(float timeSeconds) {
        if (startPosition == null || dollyDirection == null) {
            initializeFromCurrentCamera();
        }

        if (startPosition == null || dollyDirection == null) {
            // 尚未初始化（理论上不会，但防御性编程）
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("[DollyPathClip] sample() 尚未初始化，返回 null");
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
        float move = distance * t;
        Vec3d pos = startPosition.add(dollyDirection.multiply(move));
        
        // LookAt 返回 null → 表示"沿用上一帧 / 关键帧 LookAt"
        // 这正是 Dolly 的导演语义：推近但构图不变
        return new CameraPathSample(pos, null);
    }

    /** 路径终点（t=1） */
    public Vec3d getEndPosition() {
        if (startPosition == null || dollyDirection == null) {
            return null;
        }
        return startPosition.add(dollyDirection.multiply(distance));
    }

    public java.util.List<Vec3d> samplePathPositions() {
        return samplePathPositions(16);
    }

    public java.util.List<Vec3d> samplePathPositions(int minSampleCount) {
        java.util.List<Vec3d> points = new java.util.ArrayList<>();
        float duration = getEndTime() - getStartTime();
        if (duration <= 0) {
            return points;
        }
        int sampleCount = Math.max(minSampleCount, (int) (duration * 10));
        for (int i = 0; i <= sampleCount; i++) {
            float timeSeconds = getStartTime() + (duration * i) / sampleCount;
            CameraPathSample sample = sample(timeSeconds);
            if (sample != null) {
                points.add(sample.getPosition());
            }
        }
        return points;
    }

    /** 拖动起点：保持终点不动，更新方向与距离 */
    public void applyStartEndpointDelta(Vec3d delta) {
        if (delta == null || delta.lengthSquared() < 1e-12) {
            return;
        }
        ensurePathInitialized();
        Vec3d end = getEndPosition();
        if (startPosition == null || end == null) {
            return;
        }
        setEndpointsFromPositions(startPosition.add(delta), end);
    }

    /** 拖动终点：保持起点不动，更新方向与距离 */
    public void applyEndEndpointDelta(Vec3d delta) {
        if (delta == null || delta.lengthSquared() < 1e-12) {
            return;
        }
        ensurePathInitialized();
        Vec3d end = getEndPosition();
        if (startPosition == null || end == null) {
            return;
        }
        setEndpointsFromPositions(startPosition, end.add(delta));
    }

    private void setEndpointsFromPositions(Vec3d start, Vec3d end) {
        Vec3d offset = end.subtract(start);
        double len = offset.length();
        if (len < 1e-6) {
            return;
        }
        this.startPosition = start;
        this.dollyDirection = offset.multiply(1.0 / len);
        this.distance = (float) len;
        this.alignStartToCurrentPosition = false;
        if (directionMode == DirectionMode.FIXED_DIRECTION) {
            this.fixedDirection = this.dollyDirection;
        }
    }

    private void ensurePathInitialized() {
        if (startPosition == null || dollyDirection == null) {
            sample(getStartTime());
        }
    }
    
    // ========== Getter / Setter ==========
    
    public float getDistance() {
        return distance;
    }
    
    public void setDistance(float distance) {
        this.distance = distance;
    }
    
    public DirectionMode getDirectionMode() {
        return directionMode;
    }
    
    public void setDirectionMode(DirectionMode directionMode) {
        this.directionMode = directionMode != null ? directionMode : DirectionMode.CAMERA_FORWARD;
        this.dollyDirection = null;
    }
    
    public Vec3d getFixedDirection() {
        return fixedDirection;
    }
    
    public void setFixedDirection(Vec3d fixedDirection) {
        this.fixedDirection = fixedDirection != null ? fixedDirection : new Vec3d(0, 0, 1);
        if (directionMode == DirectionMode.FIXED_DIRECTION) {
            this.dollyDirection = null;
        }
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
    
    /**
     * 获取当前缓存的推拉方向（用于调试）
     */
    public Vec3d getDollyDirection() {
        return dollyDirection;
    }

    /**
     * 用于复制/持久化恢复：直接设置已捕获的起始位置。
     * 若设置成功，建议同时将 alignStartToCurrentPosition 设为 false，避免进入时被覆盖。
     */
    public void setStartPosition(Vec3d startPosition) {
        this.startPosition = startPosition;
    }

    /**
     * 用于复制/持久化恢复：直接设置已捕获的推拉方向（建议传入单位向量）。
     */
    public void setDollyDirection(Vec3d dollyDirection) {
        this.dollyDirection = dollyDirection;
    }
    
    @Override
    public CameraPathClip copy() {
        DollyPathClip copy = new DollyPathClip(
                getName() + " (Copy)",
                getStartTime(),
                getEndTime()
        );
        copy.setDistance(distance);
        copy.setDirectionMode(directionMode);
        copy.setFixedDirection(fixedDirection);
        copy.setAlignStartToCurrentPosition(alignStartToCurrentPosition);
        // ⭐ 复制必须复刻行为：若原片段已固化起点/方向，则复制缓存并禁用再次对齐
        if (startPosition != null && dollyDirection != null) {
            copy.setStartPosition(startPosition);
            copy.setDollyDirection(dollyDirection);
            copy.setAlignStartToCurrentPosition(false);
        }
        copy.setEnabled(isEnabled());
        return copy;
    }
    
    // ========== 工具方法 ==========

    private void initializeFromCurrentCamera() {
        try {
            com.director.timeline.playback.camera.CameraSample currentSample =
                    com.director.timeline.playback.camera.CameraRuntime.getInstance().getCurrentSample();
            if (currentSample == null) {
                return;
            }
            alignFromContext(new CameraPathEnterContext(
                    currentSample.position(),
                    currentSample.yaw(),
                    currentSample.pitch()));
        } catch (Exception e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[DollyPathClip] 无法从当前摄像机状态初始化", e);
            }
        }
    }
    
    /**
     * 安全归一化向量（防止除零和 NaN）
     */
    private Vec3d normalizeSafe(Vec3d v) {
        if (v == null) {
            return null;
        }
        double len = v.length();
        if (len < 1e-6) {
            return null;
        }
        return v.multiply(1.0 / len);
    }
    
    @Override
    public String toString() {
        return String.format("DollyPathClip{name='%s', time=[%.2f, %.2f], distance=%.2f, directionMode=%s}",
                getName(), getStartTime(), getEndTime(),
                distance, directionMode);
    }
}
