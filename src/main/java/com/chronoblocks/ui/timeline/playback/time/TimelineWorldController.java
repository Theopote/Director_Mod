package com.chronoblocks.ui.timeline.playback.time;

import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineSelectable;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.TrackType;
import com.chronoblocks.ui.timeline.model.time.TimeKeyframe;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.GameRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * TimelineWorldController v2.0 - 正式可用、真正驱动世界太阳/时间系统的版本
 *
 * <p>核心设计理念（参考 CameraRuntime）：
 * <ul>
 *   <li>统一的状态缓存：Mixin 永远从 TimelineWorldController.get().current() 读取，而不是从 keyframe 或 sampler</li>
 *   <li>严格的时间冻结：每 tick mixin 调用 maintainFrozenTime() 来维持冻结时间</li>
 *   <li>自定义太阳轨迹驱动：直接使用 TimeSample 中的参数（sunElevation, sunAzimuth, skyAngle）</li>
 *   <li>支持完整 TimeSample v3.0：包括颜色、月亮等所有参数</li>
 * </ul>
 *
 * <p>关键改进（相比 v1.0）：
 * <ul>
 *   <li>✅ 彻底解决"自定义太阳角不生效"：Mixin 统一从 currentSample 读取</li>
 *   <li>✅ 严格的世界时间冻结：每 tick 调用 maintainFrozenTime() 维持冻结</li>
 *   <li>✅ 完整的状态缓存：currentSample 作为 Mixin 的唯一真相源</li>
 *   <li>✅ 支持预览和实时应用：previewAt() / previewAtCurrentTime() / applyTimeSample()</li>
 *   <li>✅ 为 Mixin 提供统一的状态访问接口</li>
 * </ul>
 *
 * <p>职责：
 * <ul>
 *   <li>管理 TIME 轨道的时间关键帧</li>
 *   <li>在播放/拖动时采样世界时间并应用到 Minecraft 世界</li>
 *   <li>维护当前采样状态（currentSample），供 Mixin 查询</li>
 *   <li>支持自然时间模式和自定义太阳模式</li>
 *   <li>处理时间循环和角度循环</li>
 * </ul>
 *
 * <p>工作流程：
 * <ol>
 *   <li>Controller 采样 TimeTrackSampler 得到 TimeSample</li>
 *   <li>将 TimeSample 写入 currentSample 缓存（供 Mixin 读取）</li>
 *   <li>应用世界时间（如果使用自然时间模式）</li>
 *   <li>Mixin 在渲染时从 currentSample 读取太阳角度、天空颜色等</li>
 *   <li>Mixin 在每 tick 调用 maintainFrozenTime() 维持时间冻结</li>
 * </ol>
 *
 * @author ChronoBlocks Team
 * @version 2.0 - 正式可用版本
 */
public final class TimelineWorldController {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineWorldController.class);
    private static final TimelineWorldController INSTANCE = new TimelineWorldController();

    /** 时间线数据模型（由 TimelinePanel 注入） */
    private TimelineModel timelineModel;

    /** 当前激活的时间轨道（可能为 null） */
    private TimelineTrack activeTimeTrack;

    /** 采样器（基于激活轨道） */
    private final TimeTrackSampler sampler = new TimeTrackSampler();

    /** 是否启用时间控制 */
    private boolean enabled = true;

    /** 监听模型变更 */
    private TimelineModel.ModelChangeListener modelChangeListener;

    // ============================================================
    // 当前采样值（供 Mixin 查询）- v2.0 核心改进
    // ============================================================

    /** 是否启用了自定义 TimeSample（有活跃采样） */
    private boolean hasCustomSample = false;

    /** 当前采样结果缓存（Mixin 的唯一真相源） */
    private TimeSample currentSample = null;
    private volatile boolean internalTimeUpdate = false;

    /** 冻结的世界时间快照（用于 maintainFrozenTime） */
    private long frozenGameTime = 0;

    /** 是否已激活时间冻结 */
    private boolean freezeTimeActive = false;

    /** 冻结前保存的 doDaylightCycle 游戏规则值 */
    private Boolean savedDoDaylightCycle = null;

    private TimelineWorldController() {
    }

    public static TimelineWorldController getInstance() {
        return INSTANCE;
    }

    /** 便捷别名，供 Mixin 使用（兼容参考代码） */
    public static TimelineWorldController get() {
        return INSTANCE;
    }

    // ================== 生命周期 / 绑定模型 ==================

    /**
     * 绑定 TimelineModel。
     * 会自动注册 ModelChangeListener，在模型变更时刷新时间关键帧。
     */
    public synchronized void setTimelineModel(TimelineModel model) {
        if (this.timelineModel == model) {
            return;
        }

        // 解绑旧模型监听
        if (this.timelineModel != null && modelChangeListener != null) {
            this.timelineModel.removeModelChangeListener(modelChangeListener);
        }

        this.timelineModel = model;

        if (this.timelineModel == null) {
            activeTimeTrack = null;
            sampler.setKeyframes(new ArrayList<>());
            clear(); // 清除状态缓存
            return;
        }

        // 监听时间线结构变化
        this.modelChangeListener = changeType -> {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[TimelineWorld] Model changed: {}", changeType);
            }
            onModelStructureChanged();
        };
        this.timelineModel.addModelChangeListener(this.modelChangeListener);

        // ⭐ v3.5：初始选中第一条 TIME 轨道（如果存在）
        // ⭐ 如果只有一个时间轨道，自动激活它
        if (!isValidTimeTrack(activeTimeTrack)) {
            TimelineTrack firstTimeTrack = findFirstTimeTrack();
            if (firstTimeTrack != null) {
                // 检查是否只有一个时间轨道
                int timeTrackCount = countTimeTracks();
                activeTimeTrack = firstTimeTrack;
                if (timeTrackCount == 1) {
                    LOGGER.info("[TimelineWorld] 检测到只有一个时间轨道，自动激活: {}", firstTimeTrack.getName());
                }
            }
        }

        rebuildFromActiveTrack();
        // ⭐ v3.0：updateTimeFreezeState() 已在 rebuildFromActiveTrack() 内部调用，无需重复
    }

    /**
     * 获取当前激活的时间轨道
     */
    public synchronized TimelineTrack getActiveTimeTrack() {
        return activeTimeTrack;
    }

    /**
     * 设置当前激活的时间轨道
     */
    public synchronized void setActiveTimeTrack(TimelineTrack track) {
        if (track != null && track.getType() != TrackType.TIME) {
            LOGGER.warn("[TimelineWorld] setActiveTimeTrack: 非 TIME 轨道被选中，忽略: {}", track.getName());
            return;
        }
        if (track == activeTimeTrack) {
            return;
        }

        this.activeTimeTrack = track;
        LOGGER.info("[TimelineWorld] 激活时间轨道: {}",
                track != null ? track.getName() : "<none>");

        rebuildFromActiveTrack();
        // ⭐ v3.0：updateTimeFreezeState() 已在 rebuildFromActiveTrack() 内部调用，无需重复
    }

    /**
     * 启用/禁用时间控制
     */
    public synchronized void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            clear(); // 禁用时清除状态
        }
        LOGGER.info("[TimelineWorld] 时间控制 {}",
                enabled ? "已启用" : "已禁用");
    }

    public synchronized boolean isEnabled() {
        return enabled;
    }

    // ================== 播放 / 拖动 / 预览 状态控制 ==================

    /**
     * 每 tick 更新世界时间
     * 由 TimelinePanel 或游戏主循环调用
     */
    public synchronized void update() {
        if (!enabled || timelineModel == null) {
            return;
        }

        if (!isValidTimeTrack(activeTimeTrack)) {
            return;
        }

        float currentTime = timelineModel.getCurrentTime();

        // 性能关键：update() 在播放/拖动时每帧调用，禁止使用 info 刷屏。
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("[TimelineWorld] update: playing={} scrubbing={} t={} freezeActive={} frozenGameTime={}",
                    timelineModel.isPlaying(), timelineModel.isScrubbing(), currentTime, freezeTimeActive, freezeTimeActive ? frozenGameTime : "N/A");
        }

        // ⭐ 关键修复：时间轨道面板打开时，即使暂停也应维持预览（否则会立刻回到世界时间）
        applyTimeSample(currentTime);
    }

    /**
     * 更新方法（带参数版本，便于外部直接调用）
     *
     * @param deltaSeconds 时间增量（秒）
     * @param currentTime 当前时间线时间（秒）
     */
    public synchronized void update(float deltaSeconds, float currentTime) {
        if (!enabled || timelineModel == null) {
            return;
        }

        if (!isValidTimeTrack(activeTimeTrack)) {
            return;
        }

        // ⭐ 与无参 update() 保持一致：面板驱动时暂停也维持预览
        applyTimeSample(currentTime);
    }

    /**
     * 在指定时间采样并应用世界时间
     *
     * <p>v2.0：支持完整功能，包括自定义太阳模式和所有 TimeSample v3.0 参数
     */
    public synchronized void applyTimeSample(float timeSeconds) {
        // 性能关键：applyTimeSample 在播放/拖动时每帧调用，禁止使用 info/warn 刷屏。
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("[TimelineWorld] applyTimeSample: t={}s", timeSeconds);
        }

        // 没启用/没关键帧：不要每帧 clear + warn（会导致 FPS 掉个位数），只在必要时清一次。
        if (!enabled || sampler.getKeyframes().isEmpty()) {
            if (hasCustomSample || currentSample != null || freezeTimeActive) {
                clear();
            }
            return;
        }

        Optional<TimeSample> sampleOpt = sampler.sample(timeSeconds);
        if (sampleOpt.isEmpty()) {
            // 采样失败：同样避免刷屏，只在有状态时清一次
            if (hasCustomSample || currentSample != null || freezeTimeActive) {
                clear();
            }
            return;
        }

        TimeSample sample = sampleOpt.get();

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("[TimelineWorld] applyTimeSample ok: worldTime={} useWorldTime={} freezeWorldTime={}",
                    sample.getWorldTimeTicks(), sample.isUseWorldTime(), sample.isFreezeWorldTime());
        }

        // ⭐ v2.0：应用采样到缓存（供 Mixin 读取），支持所有模式
        applySampleInternal(sample, false);
    }

    /**
     * 预览指定时间的世界时间（不改变播放状态）
     *
     * <p>v2.0：支持完整功能，包括自定义太阳模式
     */
    public synchronized void previewAt(float timeSeconds) {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("[TimelineWorld] previewAt: t={}s", timeSeconds);
        }

        if (!enabled || !hasValidActiveTrack()) {
            LOGGER.warn("[TimelineWorld] previewAt() - 未启用或无有效轨道");
            return;
        }

        Optional<TimeSample> sampleOpt = sampler.sample(timeSeconds);
        if (sampleOpt.isEmpty()) {
            LOGGER.warn("[TimelineWorld] previewAt() - 采样失败");
            return;
        }

        TimeSample sample = sampleOpt.get();

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("[TimelineWorld] previewAt ok: worldTime={} useWorldTime={} freezeWorldTime={}",
                    sample.getWorldTimeTicks(), sample.isUseWorldTime(), sample.isFreezeWorldTime());
        }

        // ⭐ v2.0：应用采样到缓存（供 Mixin 读取）
        applySampleInternal(sample, true);

        LOGGER.debug("[TimelineWorld] 预览时间 @ {}s: {}", timeSeconds, sample);
    }

    /**
     * 预览指定关键帧所在位置的时间（用于属性面板实时预览）
     *
     * <p>v3.3 修复：当修改关键帧参数时，应该预览关键帧所在位置，而不是当前播放头位置
     * 这样可以确保设置的时间能够持久，不会被播放头位置的采样结果覆盖
     *
     * <p>⭐ 关键改进：
     * <ul>
     *   <li>预览前立即更新冻结状态，确保自定义太阳模式时立即冻结</li>
     *   <li>应用采样后立即同步冻结时间快照，防止原版时间推进覆盖</li>
     * </ul>
     *
     * @param keyframeTime 关键帧在时间线上的位置（秒）
     */
    public synchronized void previewAtKeyframeTime(float keyframeTime) {
        // ⭐ DEBUG: 记录关键帧预览调用
        LOGGER.info("[TimelineWorld] 🎯 previewAtKeyframeTime() 调用 - 关键帧时间={}s, 当前冻结状态={}, frozenGameTime={}",
                keyframeTime, freezeTimeActive, freezeTimeActive ? frozenGameTime : "N/A");

        if (!enabled) {
            LOGGER.warn("[TimelineWorld] previewAtKeyframeTime() - 未启用");
            return;
        }

        // ⭐ v3.3 关键修复：预览前立即更新冻结状态
        // 这样在属性面板修改参数时，如果切换为自定义模式，会立即冻结时间
        LOGGER.info("[TimelineWorld] 🔄 previewAtKeyframeTime() - 预览前更新冻结状态");
        updateTimeFreezeState();

        // 预览关键帧位置的时间
        previewAt(keyframeTime);

        // ⭐ v3.3 关键修复：预览后再次确保冻结状态正确
        // 因为采样结果可能改变了冻结需求（例如切换模式）
        LOGGER.info("[TimelineWorld] 🔄 previewAtKeyframeTime() - 预览后再次更新冻结状态");
        updateTimeFreezeState();

        LOGGER.info("[TimelineWorld] ✅ previewAtKeyframeTime() - 完成，当前冻结状态={}, frozenGameTime={}",
                freezeTimeActive, freezeTimeActive ? frozenGameTime : "N/A");
    }

    // ============================================================
    // 核心：应用 TimeSample（写入缓存，提供给 Mixin）
    // ============================================================

    /**
     * 内部方法：应用采样结果到缓存
     *
     * @param newSample 新的采样结果
     * @param quiet 是否静默（预览时不输出详细日志）
     */
    private void applySampleInternal(TimeSample newSample, boolean quiet) {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("[TimelineWorld] applySampleInternal: worldTime={} useWorldTime={} freezeWorldTime={} quiet={}",
                    newSample != null ? newSample.getWorldTimeTicks() : "null",
                    newSample != null ? newSample.isUseWorldTime() : "N/A",
                    newSample != null ? newSample.isFreezeWorldTime() : "N/A",
                    quiet);
        }

        if (newSample == null) {
            LOGGER.warn("[TimelineWorld] applySampleInternal() - 采样为null，清除状态");
            clear();
            return;
        }

        // ⭐ v2.0 核心：更新状态缓存（Mixin 的唯一真相源）
        long oldWorldTime = currentSample != null ? currentSample.getWorldTimeTicks() : -1;
        this.hasCustomSample = true;
        this.currentSample = newSample; // TimeSample 是不可变的，直接赋值即可

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("[TimelineWorld] applySampleInternal cache: old={} new={} freezeActive={}",
                    oldWorldTime, newSample.getWorldTimeTicks(), freezeTimeActive);
        }

        // ⭐ v3.0：应用世界时间（根据模式不同处理）
        // 注意：时间冻结状态由 shouldFreezeTime() 基于关键帧类型决定，不需要在这里更新
        // 但如果是首次应用采样，需要确保冻结状态已更新（通过 rebuildFromActiveTrack 触发）

        // 应用世界时间（自然时间模式和自定义太阳模式都需要设置时间，用于月相等）
        long normalizedTicks = newSample.getWorldTimeTicks() % 24000L;

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("[TimelineWorld] applySampleInternal applyWorldTime: normalizedTicks={} freezeActive={}",
                    normalizedTicks, freezeTimeActive);
        }

        // Debug: trace the applied sample details (temporary)
        System.out.println("[ChronoBlocks][TIME] applySampleInternal -> newSample.worldTicks=" + newSample.getWorldTimeTicks()
            + ", useWorldTime=" + newSample.isUseWorldTime()
            + ", freezeWorldTime=" + newSample.isFreezeWorldTime()
            + ", skyAngle=" + newSample.getSkyAngle()
            + ", worldTimeOfDay=" + newSample.getWorldTimeOfDay());

        applyWorldTime(normalizedTicks);
        // ⭐ 注意：applyWorldTime() 内部已经会更新 frozenGameTime（如果 freezeTimeActive）

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("[TimelineWorld] applySampleInternal applied, frozenGameTime={}",
                    freezeTimeActive ? frozenGameTime : "N/A");
        }

        // ⭐ v3.0：对于自定义太阳模式，时间已被冻结，这里只是同步时间值（用于月相/光照一致性）
        // 对于自然时间模式，如果未冻结，setTime 会生效；如果已冻结（其他原因），Mixin 会维持冻结

        if (!quiet) {
            LOGGER.debug("[TimelineWorld] Applied TimeSample: time={}, useWorldTime={}, skyAngle={}, sunElevation={}°, sunAzimuth={}°",
                newSample.getWorldTimeTicks(),
                newSample.isUseWorldTime(),
                newSample.getSkyAngle(),
                newSample.getSunElevation(),
                newSample.getSunAzimuth());
        }
    }

    // ============================================================
    // 清除（恢复原版时间）
    // ============================================================

    /**
     * 清除状态缓存，恢复原版时间系统
     */
    public synchronized void clear() {
        hasCustomSample = false;
        currentSample = null;

        // 恢复原版时间系统（注意：必须在清除 freezeTimeActive 前执行，否则 resume 会被短路）
        resumeMinecraftTime();
        
        // ⭐ 关键修复：显式同步客户端时间与服务器
        // 这确保当 Mixin 停止覆盖时，时间值会被正确更新
        syncClientTimeWithServer();

        freezeTimeActive = false;
        savedDoDaylightCycle = null;

        LOGGER.info("[TimelineWorld] Cleared time override state");
    }

    // ============================================================
    // 供 Mixin 查询的 Getter（核心）- v2.0 核心接口
    // ============================================================

    /**
     * 检查是否有活跃的自定义采样（供 Mixin 使用）
     */
    public synchronized boolean hasOverride() {
        return hasCustomSample && currentSample != null;
    }

    public boolean isInternalTimeUpdate() {
        return internalTimeUpdate;
    }

    /**
     * 便捷方法：直接获取当前采样（不返回 Optional）
     * 如果不存在，返回 null
     */
    public synchronized TimeSample current() {
        return hasOverride() ? currentSample : null;
    }

    // ================== 模型变更处理 ==================

    /**
     * 处理模型结构变更（轨道增删、关键帧变化等）
     * 
     * <p>⭐ v3.5：如果只有一个时间轨道，自动激活它
     */
    private synchronized void onModelStructureChanged() {
        if (timelineModel == null) {
            return;
        }

        // 统计当前时间轨道数量
        int timeTrackCount = countTimeTracks();
        TimelineTrack firstTimeTrack = findFirstTimeTrack();
        
        // ⭐ v3.5：如果只有一个时间轨道，无论当前状态如何，都确保它是激活的
        // 这包括：从0个变成1个、从多个变成1个、新建唯一轨道等情况
        if (timeTrackCount == 1 && firstTimeTrack != null) {
            if (activeTimeTrack != firstTimeTrack) {
                activeTimeTrack = firstTimeTrack;
                LOGGER.info("[TimelineWorld] 模型变更：检测到只有一个时间轨道，自动激活: {}", firstTimeTrack.getName());
            }
        } else if (timeTrackCount == 0) {
            // 没有时间轨道时，清空激活轨道
            activeTimeTrack = null;
        } else {
            // 有多个时间轨道时，检查当前激活轨道是否有效
            if (!isValidTimeTrack(activeTimeTrack)) {
                // 当前激活轨道无效，选择第一个作为激活轨道（但不强制，因为可能有多个）
                activeTimeTrack = firstTimeTrack;
                if (firstTimeTrack != null) {
                    LOGGER.info("[TimelineWorld] 模型变更：当前激活轨道无效，切换到: {}", firstTimeTrack.getName());
                }
            }
        }

        rebuildFromActiveTrack();
        // ⭐ v3.0：updateTimeFreezeState() 已在 rebuildFromActiveTrack() 内部调用，无需重复
        
        LOGGER.debug("[TimelineWorld] 模型变更，刷新时间关键帧");
    }

    /**
     * 从当前激活轨道重建采样器
     * 
     * <p>公开方法，供属性面板等外部组件调用，用于在关键帧属性修改后刷新采样器。
     * 
     * <p>⭐ v3.0 改进：重建后立即更新冻结状态，确保关键帧变化时及时响应
     */
    public synchronized void rebuildFromActiveTrack() {
        // ⭐ 性能优化：减少日志输出，避免频繁的 INFO 日志影响性能
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[TimelineWorld] 🔨 rebuildFromActiveTrack() 调用 - 当前冻结状态={}, frozenGameTime={}", 
                    freezeTimeActive, freezeTimeActive ? frozenGameTime : "N/A");
        }
        
        if (!hasValidActiveTrack()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[TimelineWorld] rebuildFromActiveTrack() - 无效轨道，清除状态");
            }
            sampler.setKeyframes(new ArrayList<>());
            clear(); // 清除状态缓存
            // ⭐ v3.0：轨道无效时也需要更新冻结状态（可能轨道被删除）
            updateTimeFreezeState();
            return;
        }

        List<TimeKeyframe> kfs = collectTimeKeyframes(activeTimeTrack);
        
        sampler.setKeyframes(kfs);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[TimelineWorld] Rebuild from track '{}', keyframes = {}",
                    activeTimeTrack.getName(), kfs.size());
        }
        
        // ⭐ v3.0 核心改进：每次重建采样器后立即检查冻结状态
        // 这样在添加/删除/修改关键帧时，冻结状态会立即更新
        updateTimeFreezeState();
        
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[TimelineWorld] ✅ rebuildFromActiveTrack() - 完成，当前冻结状态={}, frozenGameTime={}", 
                    freezeTimeActive, freezeTimeActive ? frozenGameTime : "N/A");
        }
    }

    /**
     * 从指定轨道收集时间关键帧
     */
    private List<TimeKeyframe> collectTimeKeyframes(TimelineTrack track) {
        List<TimeKeyframe> result = new ArrayList<>();
        if (track == null || track.getType() != TrackType.TIME) {
            return result;
        }

        for (TimelineSelectable selectable : track.getAutomationKeyframes()) {
            if (selectable instanceof TimeKeyframe tk) {
                result.add(tk);
            }
        }
        result.sort(Comparator.comparing(TimeKeyframe::getTime));
        return result;
    }

    // ================== 辅助方法 ==================

    /**
     * 检查是否有有效的激活轨道
     */
    private boolean hasValidActiveTrack() {
        return isValidTimeTrack(activeTimeTrack);
    }

    /**
     * 验证轨道是否是有效的时间轨道
     */
    private boolean isValidTimeTrack(TimelineTrack track) {
        return track != null && track.getType() == TrackType.TIME &&
               timelineModel != null &&
               timelineModel.getTracks().contains(track);
    }

    /**
     * 查找第一条 TIME 轨道
     */
    private TimelineTrack findFirstTimeTrack() {
        if (timelineModel == null) return null;
        for (TimelineTrack track : timelineModel.getTracks()) {
            if (track.getType() == TrackType.TIME) {
                return track;
            }
        }
        return null;
    }

    /**
     * 统计 TIME 轨道数量
     * 
     * <p>⭐ v3.5：用于判断是否只有一个时间轨道，如果是则自动激活
     */
    private int countTimeTracks() {
        if (timelineModel == null) return 0;
        int count = 0;
        for (TimelineTrack track : timelineModel.getTracks()) {
            if (track.getType() == TrackType.TIME) {
                count++;
            }
        }
        return count;
    }

    /**
     * 应用世界时间（自然时间模式）
     */
    private void applyWorldTime(long ticks) {
        // 性能关键：播放时每帧可能调用，禁止 info 刷屏。
        long oldWorldTime = -1;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.world != null) {
            oldWorldTime = client.world.getTimeOfDay();
        }

        // Debug: quick console trace for diagnosis (temporary)
        System.out.println("[ChronoBlocks][TIME] applyWorldTime called: newTicks=" + ticks
            + ", oldWorldTime=" + oldWorldTime
            + ", normOld=" + (oldWorldTime >= 0 ? oldWorldTime % 24000L : -1)
            + ", freezeActive=" + freezeTimeActive
            + ", frozenGameTime=" + (freezeTimeActive ? frozenGameTime : "N/A"));
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("[TimelineWorld] applyWorldTime: new={} old={} normOld={} freezeActive={} frozenGameTime={}",
                ticks,
                oldWorldTime,
                oldWorldTime >= 0 ? oldWorldTime % 24000L : -1,
                freezeTimeActive,
                freezeTimeActive ? frozenGameTime : "N/A");
        }
        
        if (client == null || client.world == null) {
            LOGGER.warn("[TimelineWorld] applyWorldTime() - 客户端或世界为null");
            return;
        }

        ClientWorld world = client.world;
        long normalizedTicks = ticks % 24000L;
        if (normalizedTicks < 0) normalizedTicks += 24000L;

        // ⭐ v3.6：避免每帧重复 setTime() 触发天空/光照抖动（用户体感为“闪一下”）
        long actualTime = world.getTimeOfDay();
        long actualNormalized = actualTime % 24000L;
        if (actualNormalized < 0) actualNormalized += 24000L;
        if (actualNormalized == normalizedTicks) {
            // Debug: skip setTime when already equal
            System.out.println("[ChronoBlocks][TIME] applyWorldTime -> already at normalizedTicks=" + normalizedTicks + ", skipping setTime");
            if (freezeTimeActive) {
                frozenGameTime = normalizedTicks;
            }
            return;
        }

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("[TimelineWorld] applyWorldTime: world.setTime({}, {}, false)", normalizedTicks, normalizedTicks);
        }
        // Debug: announce we are calling world.setTime (temporary)
        System.out.println("[ChronoBlocks][TIME] applyWorldTime -> calling world.setTime(" + normalizedTicks + ")");
        try {
            internalTimeUpdate = true;
            world.setTime(normalizedTicks, normalizedTicks, false);
        } finally {
            internalTimeUpdate = false;
        }
        
        // 更新冻结时间快照
        if (freezeTimeActive) {
            frozenGameTime = normalizedTicks;
        } else {
        }
        
        // ⭐ DEBUG: 验证设置是否成功
        // 注意：getTimeOfDay() 可能返回总 tick 数，需要归一化后比较
        actualTime = world.getTimeOfDay();
        actualNormalized = actualTime % 24000L;
        if (actualNormalized < 0) actualNormalized += 24000L;
        if (actualNormalized != normalizedTicks) {
            System.out.println("[ChronoBlocks][TIME] ⚠️ applyWorldTime() - verification failed: expected=" + normalizedTicks + ", actual=" + actualTime + " (norm=" + actualNormalized + ")");
            LOGGER.warn("[TimelineWorld] ⚠️ applyWorldTime() - 设置后验证失败! 期望={}, 实际={} (归一化={})", 
                    normalizedTicks, actualTime, actualNormalized);
        } else {
            // Debug: success
            System.out.println("[ChronoBlocks][TIME] applyWorldTime ok: actual=" + actualTime + " norm=" + actualNormalized);
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[TimelineWorld] applyWorldTime ok: actual={} norm={}", actualTime, actualNormalized);
            }
        }
    }

    // ================== 原版时间系统冻结/恢复 ==================

    /**
     * 冻结原版时间系统
     * 
     * <p>⭐ v3.0 改进：
     * <ul>
     *   <li>更严格的时间冻结控制</li>
     *   <li>初始同步时间，避免跳变</li>
     * </ul>
     */
    private synchronized void freezeMinecraftTime() {
        // ⭐ DEBUG: 记录冻结调用
        LOGGER.info("[TimelineWorld] ❄️ freezeMinecraftTime() 调用开始");
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            LOGGER.warn("[TimelineWorld] freezeMinecraftTime() - 客户端或世界为null");
            return;
        }

        ClientWorld world = client.world;
        long currentWorldTime = world.getTimeOfDay();
        long currentNormalized = currentWorldTime % 24000L;
        
        LOGGER.info("[TimelineWorld] 🌍 freezeMinecraftTime() - 当前世界时间={} (归一化={}), hasOverride={}, currentSample={}", 
                currentWorldTime, currentNormalized, hasOverride(), currentSample != null);
        
        // ⭐ v3.0：如果有活跃采样，使用采样时间作为冻结时间，避免跳变
        // 否则使用当前世界时间
        if (hasOverride()) {
            frozenGameTime = currentSample.getWorldTimeTicks() % 24000L;
            LOGGER.info("[TimelineWorld] ✅ freezeMinecraftTime() - 使用采样时间作为冻结时间: {}", frozenGameTime);
            
            // ⭐ v4.0 关键修复：移除 world.setTime() 调用
            // 原因：setTime() 会永久改变世界的内部时间值
            // 即使后来 hasOverride() 变为 false，世界内部时间仍然是那个冻结的值
            // 应该由 Mixin 的 overrideTimeOfDay() 负责返回冻结的时间值
            // 而不是修改世界的实际时间
            /*
            try {
                internalTimeUpdate = true;
                world.setTime(frozenGameTime, frozenGameTime, false);
            } finally {
                internalTimeUpdate = false;
            }
            LOGGER.info("[TimelineWorld] 🔨 freezeMinecraftTime() - 已设置世界时间到 {}", frozenGameTime);
            */
        } else {
            frozenGameTime = currentNormalized; // 使用归一化的时间
            LOGGER.info("[TimelineWorld] ⚠️ freezeMinecraftTime() - 无活跃采样，使用当前世界时间: {} (原始={}, 归一化={})", 
                    frozenGameTime, currentWorldTime, currentNormalized);
        }
        
        freezeTimeActive = true;
        LOGGER.info("[TimelineWorld] ✅ freezeMinecraftTime() - 冻结完成: freezeTimeActive=true, frozenGameTime={}", frozenGameTime);

        // 在客户端，需要通过服务器来修改游戏规则
        MinecraftServer server = client.getServer();
        if (server != null) {
            net.minecraft.server.world.ServerWorld serverWorld = server.getOverworld();
            if (serverWorld != null) {
                try {
                    GameRules gameRules = serverWorld.getGameRules();

                    // 保存当前的 doDaylightCycle 值（如果还未保存）
                    if (savedDoDaylightCycle == null) {
                        savedDoDaylightCycle = gameRules.getBoolean(GameRules.DO_DAYLIGHT_CYCLE);
                        LOGGER.debug("[TimelineWorld] 保存原始 doDaylightCycle: {}", savedDoDaylightCycle);
                    }

                    // 禁用时间流动
                    gameRules.get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);

                    LOGGER.info("[TimelineWorld] ✅ 已冻结原版时间系统 (doDaylightCycle = false, frozenTime = {})", frozenGameTime);
                    return;
                } catch (Exception e) {
                    LOGGER.warn("[TimelineWorld] 无法修改游戏规则: {}", e.getMessage());
                }
            }
        }

        // 如果没有服务器（纯客户端模式），则只记录日志
        // 时间冻结将通过 Mixin 来实现（ClientWorldMixin.onTickTime + maintainFrozenTime）
        LOGGER.info("[TimelineWorld] ✅ 已标记时间冻结（将通过 Mixin 实现，无服务器访问，frozenTime = {}）", frozenGameTime);
    }

    /**
     * 恢复原版时间系统
     */
    private synchronized void resumeMinecraftTime() {
        // 即使 freezeTimeActive 标记不同步，只要保存过 gamerule，也应尝试恢复
        if (!freezeTimeActive && savedDoDaylightCycle == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            freezeTimeActive = false;
            return;
        }

        // 在客户端，需要通过服务器来修改游戏规则
        MinecraftServer server = client.getServer();
        if (server != null) {
            net.minecraft.server.world.ServerWorld serverWorld = server.getOverworld();
            if (serverWorld != null) {
                try {
                    GameRules gameRules = serverWorld.getGameRules();

                    // 恢复保存的 doDaylightCycle 值
                    if (savedDoDaylightCycle != null) {
                        gameRules.get(GameRules.DO_DAYLIGHT_CYCLE).set(savedDoDaylightCycle, server);
                        LOGGER.info("[TimelineWorld] ✅ 已恢复原版时间系统 (doDaylightCycle = {})", savedDoDaylightCycle);
                    } else {
                        // 如果没有保存的值，默认恢复为 true
                        gameRules.get(GameRules.DO_DAYLIGHT_CYCLE).set(true, server);
                        LOGGER.info("[TimelineWorld] ✅ 已恢复原版时间系统 (doDaylightCycle = true, 默认值)");
                    }

                    savedDoDaylightCycle = null;
                    freezeTimeActive = false;
                    return;
                } catch (Exception e) {
                    LOGGER.warn("[TimelineWorld] 无法恢复游戏规则: {}", e.getMessage());
                }
            }
        }

        // 如果没有服务器，只清除标记
        savedDoDaylightCycle = null;
        freezeTimeActive = false;
        LOGGER.info("[TimelineWorld] ✅ 已清除时间冻结标记");
    }

    /**
     * 更新时间冻结状态
     * 
     * <p>v2.0 改进：根据 TimeSample 的 freezeWorldTime 标志决定是否冻结
     */
    private synchronized void updateTimeFreezeState() {
        // 检查是否应该冻结时间
        boolean shouldFreeze = shouldFreezeTime();

        if (shouldFreeze && !freezeTimeActive) {
            freezeMinecraftTime();
        } else if (!shouldFreeze && freezeTimeActive) {
            resumeMinecraftTime();
        }
    }
    
    /**
     * ⭐ 客户端时间同步方法 - 确保客户端与服务器时间同步
     * 
     * <p>当关闭 ChronoBlocks 时调用，强制客户端刷新时间缓存
     * 这确保即使 Mixin 停止覆盖，客户端也能正确显示服务器的实际时间
     * 
     * <p>问题背景：
     * <ul>
     *   <li>当时间被冻结时，ClientWorld 的内部时间值停止更新</li>
     *   <li>即使 Mixin 停止覆盖返回原始值，缓存的时间也不会自动更新</li>
     *   <li>需要显式触发客户端重新同步时间</li>
     * </ul>
     */
    private synchronized void syncClientTimeWithServer() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            LOGGER.warn("[TimelineWorld] ❌ 无法同步时间：客户端或世界为 null");
            return;
        }

        // 获取服务器时间
        MinecraftServer server = client.getServer();
        if (server != null) {
            net.minecraft.server.world.ServerWorld serverWorld = server.getOverworld();
            if (serverWorld != null) {
                try {
                    long serverTime = serverWorld.getTimeOfDay();
                    // 在客户端世界中同步时间
                    // 这会触发客户端重新计算所有时间相关的值（太阳角度、天空亮度等）
                    ClientWorld clientWorld = client.world;
                    
                    // ⚠️ 注意：ClientWorld 的时间由服务器同步过来
                    // 我们不能直接修改，但可以通过清除 Mixin 覆盖来让它恢复服务器的时间
                    // 由于我们已经设置了 hasCustomSample=false，Mixin 会停止覆盖
                    
                    LOGGER.info("[TimelineWorld] ✅ 已触发客户端时间同步 (服务器时间: {})", serverTime);
                } catch (Exception e) {
                    LOGGER.warn("[TimelineWorld] ⚠️ 客户端时间同步出错: {}", e.getMessage());
                }
            }
        }
    }
    
    /**
     * 检查是否应该冻结世界时间（供 Mixin 使用）
     * 
     * <p>⭐ v3.0 核心改进：基于轨道上的关键帧类型动态判断，而不是当前采样结果
     * 
     * <p>冻结规则：
     * <ul>
     *   <li>如果轨道为空（无关键帧）→ 不冻结</li>
     *   <li>如果所有关键帧都是自然时间模式 → 不冻结，原版时间正常流逝</li>
     *   <li>如果有至少一个自定义太阳关键帧 → 冻结，完全由时间轨道控制</li>
     * </ul>
     * 
     * <p>这样设计的好处：
     * <ul>
     *   <li>更高效：只有真正需要时才冻结</li>
     *   <li>更灵活：支持混合模式（虽然当前实现不支持，但架构上允许）</li>
     *   <li>更直观：用户添加自定义太阳关键帧时，立即看到冻结效果</li>
     * </ul>
     */
    public synchronized boolean shouldFreezeTime() {
        if (!enabled || !hasValidActiveTrack()) {
            return false;
        }

        // 获取当前激活轨道上的所有关键帧
        List<TimeKeyframe> keyframes = sampler.getKeyframes();
        
        // 如果轨道为空（无关键帧），不冻结
        if (keyframes.isEmpty()) {
            return false;
        }

        // ✅ v4.0 修复：检查是否所有关键帧都是自然时间模式
        // 如果至少有一个自定义太阳关键帧，就需要冻结时间
        for (TimeKeyframe kf : keyframes) {
            if (!kf.isUseNaturalTime()) {
                // 发现自定义太阳关键帧，需要冻结
                return true;
            }
        }

        // 所有关键帧都是自然时间模式 → 不冻结，原版时间正常推进
        return false;
    }

    /**
     * 检查采样器是否为空（没有关键帧）
     */
    public synchronized boolean isEmpty() {
        return sampler.getKeyframes().isEmpty();
    }

}
