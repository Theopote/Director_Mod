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

        // 初始选中第一条 TIME 轨道（如果存在）
        if (!isValidTimeTrack(activeTimeTrack)) {
            activeTimeTrack = findFirstTimeTrack();
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

        // 只有在播放或 scrubbing 时才应用时间控制
        boolean playing = timelineModel.isPlaying();
        boolean scrubbing = timelineModel.isScrubbing();

        if (!playing && !scrubbing) {
            return;
        }

        float currentTime = timelineModel.getCurrentTime();
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

        // 只有在播放或 scrubbing 时才应用时间控制
        boolean playing = timelineModel.isPlaying();
        boolean scrubbing = timelineModel.isScrubbing();

        if (!playing && !scrubbing) {
            return;
        }

        applyTimeSample(currentTime);
    }

    /**
     * 在指定时间采样并应用世界时间
     * 
     * <p>v2.0：支持完整功能，包括自定义太阳模式和所有 TimeSample v3.0 参数
     */
    public synchronized void applyTimeSample(float timeSeconds) {
        if (!enabled || sampler.getKeyframes().isEmpty()) {
            clear(); // 没有关键帧时清除状态
            return;
        }

        Optional<TimeSample> sampleOpt = sampler.sample(timeSeconds);
        if (sampleOpt.isEmpty()) {
            clear(); // 采样失败时清除状态
            return;
        }

        TimeSample sample = sampleOpt.get();
        
        // ⭐ v2.0：应用采样到缓存（供 Mixin 读取），支持所有模式
        applySampleInternal(sample, false);
    }

    /**
     * 预览指定时间的世界时间（不改变播放状态）
     * 
     * <p>v2.0：支持完整功能，包括自定义太阳模式
     */
    public synchronized void previewAt(float timeSeconds) {
        if (!enabled || !hasValidActiveTrack()) {
            return;
        }

        Optional<TimeSample> sampleOpt = sampler.sample(timeSeconds);
        if (sampleOpt.isEmpty()) {
            return;
        }

        TimeSample sample = sampleOpt.get();
        
        // ⭐ v2.0：应用采样到缓存（供 Mixin 读取）
        applySampleInternal(sample, true);
        
        LOGGER.debug("[TimelineWorld] 预览时间 @ {}s: {}", timeSeconds, sample);
    }

    /**
     * 预览当前时间线时间的世界时间（用于属性面板实时预览）
     */
    public synchronized void previewAtCurrentTime() {
        if (!enabled || timelineModel == null) {
            return;
        }
        float currentTime = timelineModel.getCurrentTime();
        previewAt(currentTime);
    }
    
    /**
     * 预览指定关键帧所在位置的时间（用于属性面板实时预览）
     * 
     * <p>v3.2 修复：当修改关键帧参数时，应该预览关键帧所在位置，而不是当前播放头位置
     * 这样可以确保设置的时间能够持久，不会被播放头位置的采样结果覆盖
     * 
     * @param keyframeTime 关键帧在时间线上的位置（秒）
     */
    public synchronized void previewAtKeyframeTime(float keyframeTime) {
        if (!enabled) {
            return;
        }
        previewAt(keyframeTime);
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
        if (newSample == null) {
            clear();
            return;
        }

        // ⭐ v2.0 核心：更新状态缓存（Mixin 的唯一真相源）
        this.hasCustomSample = true;
        this.currentSample = newSample; // TimeSample 是不可变的，直接赋值即可

        // ⭐ v3.0：应用世界时间（根据模式不同处理）
        // 注意：时间冻结状态由 shouldFreezeTime() 基于关键帧类型决定，不需要在这里更新
        // 但如果是首次应用采样，需要确保冻结状态已更新（通过 rebuildFromActiveTrack 触发）
        
        // 应用世界时间（自然时间模式和自定义太阳模式都需要设置时间，用于月相等）
        long normalizedTicks = newSample.getWorldTimeTicks() % 24000L;
        applyWorldTime(normalizedTicks);
        
        // ⭐ v3.0：对于自定义太阳模式，时间已被冻结，这里只是同步时间值（用于月相/光照一致性）
        // 对于自然时间模式，如果未冻结，setTime 会生效；如果已冻结（其他原因），Mixin 会维持冻结

        if (!quiet) {
            LOGGER.debug("[TimelineWorld] Applied TimeSample: time={}, useWorldTime={}, sunElevation={}°, sunAzimuth={}°",
                    newSample.getWorldTimeTicks(), 
                    newSample.isUseWorldTime(),
                    newSample.getSunElevation(),
                    newSample.getSunAzimuth());
        }
    }

    /**
     * 对外接口（命令式调用）
     */
    public synchronized void applyTimeSample(TimeSample sample) {
        applySampleInternal(sample, false);
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
        freezeTimeActive = false;
        
        // 恢复原版时间系统
        resumeMinecraftTime();
        
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

    /**
     * 获取当前采样结果（供 Mixin 使用）
     * 
     * <p>v2.0 核心接口：Mixin 永远从这里读取，而不是从 keyframe 或 sampler
     */
    public synchronized Optional<TimeSample> getCurrentSample() {
        if (!hasOverride()) {
            return Optional.empty();
        }
        return Optional.of(currentSample);
    }

    /**
     * 便捷方法：直接获取当前采样（不返回 Optional）
     * 如果不存在，返回 null
     */
    public synchronized TimeSample current() {
        return hasOverride() ? currentSample : null;
    }

    /**
     * 世界 tick 时调用（来自 mixin），维持冻结时间
     * 
     * <p>v2.0 核心改进：严格的时间冻结控制
     */
    public synchronized void maintainFrozenTime(ClientWorld world) {
        if (!freezeTimeActive || world == null) {
            return;
        }

        // 维持冻结的时间
        world.setTime(frozenGameTime, frozenGameTime, false);
        
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("[TimelineWorld] Maintain frozen time: {} ticks", frozenGameTime);
        }
    }

    // ================== 模型变更处理 ==================

    /**
     * 处理模型结构变更（轨道增删、关键帧变化等）
     */
    private synchronized void onModelStructureChanged() {
        if (timelineModel == null) {
            return;
        }

        // 如果当前激活轨道无效，重新选择
        if (!isValidTimeTrack(activeTimeTrack)) {
            activeTimeTrack = findFirstTimeTrack();
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
        if (!hasValidActiveTrack()) {
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
     * 应用世界时间（自然时间模式）
     */
    private void applyWorldTime(long ticks) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return;
        }

        ClientWorld world = client.world;
        long normalizedTicks = ticks % 24000L;
        world.setTime(normalizedTicks, normalizedTicks, false);
        
        // 更新冻结时间快照
        if (freezeTimeActive) {
            frozenGameTime = normalizedTicks;
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
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return;
        }

        ClientWorld world = client.world;
        
        // ⭐ v3.0：如果有活跃采样，使用采样时间作为冻结时间，避免跳变
        // 否则使用当前世界时间
        if (hasOverride() && currentSample != null) {
            frozenGameTime = currentSample.getWorldTimeTicks() % 24000L;
            // 立即同步到世界，确保一致性
            world.setTime(frozenGameTime, frozenGameTime, false);
        } else {
            frozenGameTime = world.getTimeOfDay();
        }
        
        freezeTimeActive = true;

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
        if (!freezeTimeActive) {
            return; // 如果未冻结，无需恢复
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
        
        // ⭐ v3.0 核心逻辑：检查是否有"自定义太阳"关键帧
        // 如果有至少一个自定义太阳关键帧，就需要冻结
        boolean hasCustomSunKeyframe = keyframes.stream()
                .anyMatch(kf -> !kf.isUseNaturalTime());
        
        if (hasCustomSunKeyframe) {
            // 有自定义太阳关键帧 → 需要冻结
            return true;
        }
        
        // 所有关键帧都是自然时间模式 → 不冻结
        // 这种情况下，原版时间系统可以正常运行，只需要在采样时同步时间即可
        return false;
    }

    // ================== 兼容性方法 ==================

    /**
     * 获取指定时间的采样结果（便于外部直接查询）
     * 
     * @param currentTime 时间线时间（秒）
     * @return 采样结果，如果无法采样则返回 empty
     */
    public synchronized Optional<TimeSample> getCurrentSample(float currentTime) {
        if (!enabled || !hasValidActiveTrack()) {
            return Optional.empty();
        }

        return sampler.sample(currentTime);
    }

    /**
     * 检查采样器是否为空（没有关键帧）
     */
    public synchronized boolean isEmpty() {
        return sampler.getKeyframes().isEmpty();
    }

    /**
     * 检查是否处于 scrubbing 状态
     */
    public synchronized boolean isScrubbing() {
        return timelineModel != null && timelineModel.isScrubbing();
    }
}
