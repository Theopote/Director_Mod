package com.director.timeline.model.camera.path;

import com.director.timeline.model.TimelineSelectable;

import java.util.UUID;

/**
 * CameraPathClip - 摄像机路径片段（抽象基类）
 * 
 * <p>表示一段"摄像机路径生成规则"，在 [startTime, endTime] 区间内决定摄像机如何运动。
 * 
 * <p>设计原则：
 * <ul>
 *   <li>Path Clip ≠ Keyframe：Keyframe 定义状态，Path Clip 定义运动方式</li>
 *   <li>Path Clip 是"有时长的对象"：必须有 startTime/endTime，Timeline 上是一个"块"</li>
 *   <li>Path Clip 是"可替换策略"：支持 FREE、ORBIT、DOLLY、CRANE 等不同模式</li>
 *   <li>播放时"只读，不修改关键帧"：Path Clip 在运行时生成位置，但不写回 Keyframe</li>
 * </ul>
 * 
 * <p>Path Clip 负责：
 * <ul>
 *   <li>Position（位置）</li>
 *   <li>Look Direction（朝向，通过 LookAt 计算）</li>
 * </ul>
 * 
 * <p>Path Clip 不负责：
 * <ul>
 *   <li>FOV（由关键帧控制）</li>
 *   <li>Roll（由关键帧控制）</li>
 *   <li>Post Effect（由关键帧控制）</li>
 * </ul>
 * 
 * @author Director Team
 * @version 1.0
 */
public abstract class CameraPathClip implements TimelineSelectable {
    
    private final UUID id;
    private String name;
    private float startTime;
    private float endTime;
    private boolean enabled = true;
    
    public CameraPathClip(String name, float startTime, float endTime) {
        this.id = UUID.randomUUID();
        this.name = name != null ? name : "Path Clip";
        this.startTime = startTime;
        this.endTime = Math.max(startTime, endTime);
    }
    
    // ========== TimelineSelectable 接口实现 ==========
    
    @Override
    public UUID getId() {
        return id;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    /**
     * 设置名称（TimelineSelectable 接口中没有此方法，但为了编辑需要提供）
     */
    public void setName(String name) {
        this.name = name != null ? name : "Path Clip";
    }
    
    @Override
    public float getStartTime() {
        return startTime;
    }
    
    @Override
    public float getEndTime() {
        return endTime;
    }
    
    @Override
    public String getType() {
        return "CameraPathClip";
    }
    
    // ========== 时间管理 ==========
    
    public void setStartTime(float startTime) {
        this.startTime = startTime;
        if (endTime < startTime) {
            endTime = startTime;
        }
    }
    
    public void setEndTime(float endTime) {
        this.endTime = Math.max(startTime, endTime);
    }

    public void offsetTime(float deltaSeconds) {
        if (Math.abs(deltaSeconds) < 1e-6f) {
            return;
        }
        this.startTime += deltaSeconds;
        this.endTime += deltaSeconds;
    }
    
    public float getDuration() {
        return endTime - startTime;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    /**
     * 判断当前时间是否落在该 Path Clip 内
     */
    public boolean isActiveAt(float timeSeconds) {
        if (!enabled) {
            return false;
        }
        return timeSeconds >= startTime && timeSeconds <= endTime;
    }
    
    /**
     * 返回当前时间在该 Clip 内的归一化进度 [0,1]
     */
    public float getNormalizedTime(float timeSeconds) {
        if (timeSeconds <= startTime) {
            return 0f;
        }
        if (timeSeconds >= endTime) {
            return 1f;
        }
        float duration = endTime - startTime;
        if (duration <= 0) {
            return 0f;
        }
        return (timeSeconds - startTime) / duration;
    }
    
    // ========== 核心：路径计算 ==========
    
    /**
     * 计算该 Path Clip 在指定时间点的摄像机位置和朝向
     * 
     * @param timeSeconds 时间（秒）
     * @return 摄像机路径采样结果，包含位置和 LookAt 点
     */
    public abstract CameraPathSample sample(float timeSeconds);
    
    /**
     * Path 类型（用于 UI / 序列化）
     */
    public abstract CameraPathType getPathType();
    
    /**
     * 复制该 Path Clip（用于撤销/重做）
     */
    public abstract CameraPathClip copy();
    
    // ========== 生命周期钩子 ==========
    
    /**
     * 当时间轴"刚进入"该 PathClip 时调用（只调用一次）。
     * 
     * <p>用于 PathClip 对齐到当前摄像机位置，实现无缝接续。
     * 例如：OrbitPathClip 可以根据当前摄像机位置自动计算起始角度。
     * 
     * <p>调用时机：
     * <ul>
     *   <li>只在第一次进入 PathClip 时调用（activePathClip != lastActivePathClip）</li>
     *   <li>不会在播放过程中反复触发</li>
     *   <li>支持 Timeline scrub、跳帧、倒放等场景</li>
     * </ul>
     * 
     * @param ctx 进入上下文，包含进入前一帧的摄像机状态（位置、朝向等）
     */
    public void onEnter(CameraPathEnterContext ctx) {
        // 默认什么都不做，子类可以 override
    }
    
    /**
     * 当时间轴"离开"该 PathClip 时调用（可选，用于清理）。
     * 
     * <p>目前暂未使用，保留用于未来扩展。
     */
    public void onExit() {
        // 默认什么都不做，子类可以 override
    }
}
