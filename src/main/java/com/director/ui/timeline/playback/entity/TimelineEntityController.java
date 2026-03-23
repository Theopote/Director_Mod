package com.director.ui.timeline.playback.entity;

import com.director.ui.timeline.model.TimelineTrack;
import com.director.ui.timeline.model.TrackType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TimelineEntityController v1.0
 * -------------------------------------------------------
 * 负责在"导演模式 + 实体轨道启用"时，用 EntityTrackSampler
 * 驱动 Minecraft 客户端中实体的表现：
 * <p>
 *  - 位置、旋转、缩放
 *  - 动作类型、动作速度、动作循环
 *  - 可见性、激活状态
 *  - 名称标签显示
 * <p>
 * 设计要点：
 *  - 单例（与 TimelineWeatherController / TimelineSeasonController 风格一致）
 *  - 不主动"创造实体 Mixin"，只是提供 hasOverride() + current()
 *    供其他渲染/实体系统查询。
 *  - 通过 applyToWorld(...) 应用实体状态到 Minecraft 世界。
 * <p>
 * 使用方式（典型调用链）：
 *  - 时间线播放： updateForPlayback(currentTimeSeconds)
 *  - 拖动时间标尺： previewAt(currentTimeSeconds)
 *  - UI 切换激活轨道： bindActiveTrack(track) / clearActiveTrack()
 *  - 关闭导演模式 / 删除实体轨道： disableAndReset()
 *
 * @author Director
 * @version 1.0
 */
public final class TimelineEntityController {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineEntityController.class);

    // ========= 单例 =========

    private static final TimelineEntityController INSTANCE = new TimelineEntityController();

    public static TimelineEntityController getInstance() {
        return INSTANCE;
    }

    /** 便捷别名，供 Mixin 使用 */
    public static TimelineEntityController get() {
        return INSTANCE;
    }

    // ========= 状态字段 =========

    /** 当前是否启用"实体轨道接管" */
    private boolean enabled = false;

    /** 当前绑定的实体轨道（可能为 null） */
    private TimelineTrack activeTrack;

    /** 采样器：负责从 EntityKeyframe 列表插值出 EntitySample */
    private EntityTrackSampler sampler;

    /** 当前采样结果，供渲染 / 实体系统查询 */
    private EntitySample currentSample;

    /** 当前是否有有效 override（enabled + sampler + sample） */
    private boolean hasOverride = false;

    private TimelineEntityController() {
    }

    // ==========================================================
    // 基本控制 API（给 UI / TimelinePanel 调用）
    // ==========================================================

    /**
     * 是否启用实体轨道接管。
     * 通常由 UI 的"启用实体轨道"按钮控制。
     */
    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;

        if (!enabled) {
            // 关闭时：清理 override，尽量恢复原版实体行为
            clearOverrideAndResetWorld();
        } else {
            LOGGER.info("[EntityController] 实体轨道接管已启用");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 绑定/切换当前激活的实体轨道。
     * 一般在 TimelinePanel 用户选中某条 ENTITY 轨道时调用。
     */
    public void bindActiveTrack(TimelineTrack track) {
        if (track != null && track.getType() != TrackType.ENTITY) {
            LOGGER.warn("[EntityController] 试图绑定非实体轨道: {}", track.getType());
        }
        this.activeTrack = track;
        rebuildFromActiveTrack();
    }

    /**
     * 设置激活的实体轨道（向后兼容方法）。
     * 
     * @deprecated 使用 {@link #bindActiveTrack(TimelineTrack)} 代替
     */
    @Deprecated
    public void setActiveEntityTrack(TimelineTrack track) {
        bindActiveTrack(track);
    }

    /**
     * 获取当前激活的实体轨道。
     */
    public TimelineTrack getActiveEntityTrack() {
        return activeTrack;
    }

    /**
     * 是否存在实体轨道（无论是否已激活）。
     */
    public boolean hasEntityTrack() {
        if (com.director.client.DirectorClientController.getInstance().isDirectorModeActive()) {
            return true;
        }
        if (activeTrack != null) {
            return true;
        }
        com.director.ui.timeline.container.ContainerTimelineService service =
            com.director.ui.timeline.container.ContainerTimelineService.getInstance();
        if (service == null) {
            return false;
        }
        com.director.ui.timeline.model.TimelineModel model = service.getTimelineModel();
        if (model == null) {
            return false;
        }
        for (TimelineTrack track : model.getTracks()) {
            if (track.getType() == TrackType.ENTITY) {
                return true;
            }
        }
        return false;
    }

    /**
     * 当轨道上的关键帧发生变化（添加/删除/移动）时调用。
     * 例如：
     *  - EntityKeyframe 命令执行完成后
     *  - 撤销/重做
     */
    public void rebuildFromActiveTrack() {
        if (activeTrack == null) {
            sampler = null;
            currentSample = null;
            hasOverride = false;
            return;
        }

        sampler = new EntityTrackSampler(activeTrack);
        sampler.rebuildKeyframeList();
        
        // 统计关键帧数量
        int keyframeCount = 0;
        for (com.director.ui.timeline.model.TimelineSelectable selectable : activeTrack.getAutomationKeyframes()) {
            if (selectable instanceof com.director.ui.timeline.model.entity.EntityKeyframe) {
                keyframeCount++;
            }
        }
        LOGGER.debug("[EntityController] 重建 EntityTrackSampler。关键帧数 = {}", keyframeCount);
    }

    /**
     * ⭐ v1.0：检查并自动激活唯一的实体轨道。
     * 当模型结构变更时调用此方法，如果只有一个实体轨道，自动激活它。
     * 
     * <p>此方法通过 ContainerTimelineService 获取 TimelineModel，不直接绑定模型。
     */
    public void checkAndAutoActivate() {
        com.director.ui.timeline.container.ContainerTimelineService service = 
            com.director.ui.timeline.container.ContainerTimelineService.getInstance();
        if (service == null) {
            return;
        }
        
        com.director.ui.timeline.model.TimelineModel model = service.getTimelineModel();
        if (model == null) {
            return;
        }
        
        // 统计当前实体轨道数量
        int entityTrackCount = 0;
        TimelineTrack firstEntityTrack = null;
        for (TimelineTrack track : model.getTracks()) {
            if (track.getType() == TrackType.ENTITY) {
                entityTrackCount++;
                if (firstEntityTrack == null) {
                    firstEntityTrack = track;
                }
            }
        }
        
        // ⭐ v1.0：如果只有一个实体轨道，无论当前状态如何，都确保它是激活的
        if (entityTrackCount == 1) {
            if (activeTrack != firstEntityTrack) {
                activeTrack = firstEntityTrack;
                LOGGER.info("[EntityController] 检测到只有一个实体轨道，自动激活: {}", firstEntityTrack.getName());
                rebuildFromActiveTrack();
            }
            // ⭐ 自动启用实体控制器（如果有激活轨道）
            if (!enabled) {
                setEnabled(true);
            }
        } else if (entityTrackCount == 0) {
            // 没有实体轨道时，清空激活轨道并禁用控制器
            if (activeTrack != null) {
                activeTrack = null;
                sampler = null;
                currentSample = null;
                hasOverride = false;
                LOGGER.info("[EntityController] 没有实体轨道，清空激活轨道");
            }
            if (enabled) {
                setEnabled(false);
            }
        } else {
            // 有多个实体轨道时，检查当前激活轨道是否仍然在模型中
            if (activeTrack != null && !model.getTracks().contains(activeTrack)) {
                // 当前激活轨道已被删除，切换到第一条轨道（但不强制，因为可能有多个）
                activeTrack = firstEntityTrack;
                LOGGER.info("[EntityController] 当前激活轨道已被删除，切换到: {}", firstEntityTrack.getName());
                rebuildFromActiveTrack();
            }
            // ⭐ 如果有激活轨道，确保控制器启用
            if (activeTrack != null && !enabled) {
                setEnabled(true);
            }
        }
    }

    // ==========================================================
    // 调用链入口（时间线驱动）
    // ==========================================================

    /**
     * ⭐ v1.0：时间线播放 / 拖动时调用，传入当前时间，刷新实体状态。
     * <p>
     * 一般由 TimelinePanel / 播放控制中心 调用：
     *  - 播放时每 tick 调用
     *  - 拖动时间尺 / Scrubbing 时调用
     */
    public void updateForPlayback(float timelineTimeSeconds) {
        if (!enabled || activeTrack == null || sampler == null) {
            // 当未启用或无轨道时，不 override
            clearOverride();  // NOTE: 不强制恢复原版，只是停止覆盖。
            return;
        }

        // 采样当前时间的实体状态
        EntitySample sample = sampler.sample(timelineTimeSeconds);
        this.currentSample = sample;
        this.hasOverride = true;

        // 分发到各个子系统
        applyToWorld(sample);
    }

    /**
     * 仅用于"预览"（例如拖动时间标尺时预览，但不一定处于播放态）。
     * 和 updateForPlayback 基本一致，只是语义上给 UI 看得更清楚。
     */
    public void previewAt(float timelineTimeSeconds) {
        updateForPlayback(timelineTimeSeconds);
    }

    /**
     * 时间线播放时调用（向后兼容方法）。
     * 
     * @deprecated 使用 {@link #updateForPlayback(float)} 代替
     */
    @Deprecated
    public void onTimelineTick(float timelineTime) {
        updateForPlayback(timelineTime);
    }

    /**
     * 外部系统（例如实体渲染 Mixin）需要知道：
     * 当前是否有实体 override。
     */
    public boolean hasOverride() {
        return hasOverride && currentSample != null && enabled;
    }

    /**
     * 当前实体采样结果（给 Mixin / 渲染层调用）。
     */
    public EntitySample getCurrentSample() {
        return currentSample;
    }

    // ==========================================================
    // 将 EntitySample 应用到 Minecraft 世界
    // ==========================================================

    /**
     * ⭐ v1.0：将 EntitySample 分发到各个实体子系统。
     * 
     * <p>架构设计：
     * <ul>
     *   <li>1️⃣ 实体位置/旋转/缩放控制（未来实现）</li>
     *   <li>2️⃣ 实体动作/动画控制（未来实现）</li>
     *   <li>3️⃣ 实体可见性控制（未来实现）</li>
     *   <li>4️⃣ 实体名称标签控制（未来实现）</li>
     * </ul>
     * 
     * <p>注意：目前是框架实现，具体的实体控制逻辑需要后续扩展。
     */
    private void applyToWorld(EntitySample sample) {
        if (sample == null || !sample.enabled) {
            return;
        }

        // TODO: 实现具体的实体控制逻辑
        // 例如：
        // - 更新实体位置、旋转、缩放
        // - 控制实体动作、动画
        // - 控制实体可见性
        // - 控制名称标签显示
        
        LOGGER.trace("[EntityController] 应用实体采样: position={}, rotation={}, action={}", 
                sample.position, sample.rotation, sample.actionType);
    }

    // ==========================================================
    // 清理 / 重置逻辑
    // ==========================================================

    /**
     * 清除 override 状态（但不强制恢复 Vanilla 实体行为）。
     * 一般用于：
     *  - 停止播放但仍在导演模式中
     *  - 暂停实体轨道接管
     */
    public void clearOverride() {
        this.hasOverride = false;
        this.currentSample = null;
    }

    /**
     * ⭐ v1.0：清空 override，并尝试恢复世界实体到"正常状态"。
     * 
     * <p>清理所有子系统状态：
     *  - 实体位置/旋转/缩放 重置为默认值
     *  - 其他实体状态由 Vanilla 自己驱动
     */
    private void clearOverrideAndResetWorld() {
        this.hasOverride = false;
        this.currentSample = null;

        // TODO: 清理实体子系统
        // 例如：
        // - 重置实体位置、旋转、缩放
        // - 恢复实体动作、动画
        // - 恢复实体可见性
        
        LOGGER.info("[EntityController] 清除实体 override，并尝试恢复 Vanilla 实体状态");
    }

    /**
     * ⭐ v1.0：清除实体覆盖状态，恢复游戏默认值。
     * 
     * <p>通常在关闭 Director 时调用，确保实体修改被清除，游戏恢复默认渲染。
     */
    public void clear() {
        // 禁用控制器
        this.enabled = false;
        
        // 清空所有状态
        this.hasOverride = false;
        this.currentSample = null;
        this.activeTrack = null;
        this.sampler = null;

        // TODO: 清理实体子系统
        
        LOGGER.info("[EntityController] Cleared entity override state - 恢复游戏默认实体状态");
    }

}

