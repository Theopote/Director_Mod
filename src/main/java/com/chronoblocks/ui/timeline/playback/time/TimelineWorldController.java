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
 * TimelineWorldController v1.0
 *
 * <p>职责：
 * <ul>
 *   <li>管理 TIME 轨道的时间关键帧</li>
 *   <li>在播放/拖动时采样世界时间并应用到 Minecraft 世界</li>
 *   <li>支持自然时间模式和自定义太阳模式</li>
 *   <li>处理时间循环和角度循环</li>
 * </ul>
 *
 * <p>注意：
 * <ul>
 *   <li>与 TimelineCameraController 类似，但控制的是世界时间而非摄像机</li>
 *   <li>支持多个 TIME 轨道，但任意时刻只有一条为 active（可扩展）</li>
 *   <li>需要配合 Mixin 来实际应用时间（避免直接修改 world.timeOfDay）</li>
 * </ul>
 *
 * @author ChronoBlocks Team
 * @version 1.0
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

    /** 是否已冻结原版时间系统 */
    private boolean timeFrozen = false;

    /** 冻结前保存的 doDaylightCycle 游戏规则值 */
    private Boolean savedDoDaylightCycle = null;

    private TimelineWorldController() {
    }

    public static TimelineWorldController getInstance() {
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
        
        // ⭐ 当有时间轨道时，冻结原版时间系统
        updateTimeFreezeState();
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
        
        // ⭐ 更新时间冻结状态
        updateTimeFreezeState();
    }

    /**
     * 启用/禁用时间控制
     */
    public synchronized void setEnabled(boolean enabled) {
        this.enabled = enabled;
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
     * <p>关键设计：只有使用"自定义太阳"模式时才真正接管时间控制。
     * 如果采样结果是"使用世界时间"模式，则不干预，让原版时间系统自行处理。
     */
    public synchronized void applyTimeSample(float timeSeconds) {
        if (!enabled || sampler.getKeyframes().isEmpty()) {
            return;
        }

        Optional<TimeSample> sampleOpt = sampler.sample(timeSeconds);
        if (sampleOpt.isEmpty()) {
            return;
        }

        TimeSample sample = sampleOpt.get();
        
        // ⭐ 如果当前采样对应的是"使用世界时间"，那就不干预，让原版自己走
        if (!sample.isUseCustomSun()) {
            return;
        }

        // 真正"接管时间/太阳"的只在自定义太阳模式下发生
        applyToWorld(sample);
    }

    /**
     * 预览指定时间的世界时间（不改变播放状态）
     */
    public synchronized void previewAt(float timeSeconds) {
        if (!enabled || !hasValidActiveTrack()) {
            return;
        }

        Optional<TimeSample> sample = sampler.sample(timeSeconds);
        if (sample.isPresent()) {
            applyToWorld(sample.get());
            LOGGER.debug("[TimelineWorld] 预览时间 @ {}s: {}", timeSeconds, sample.get());
        }
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
        
        // ⭐ 更新时间冻结状态（可能轨道被删除，需要恢复时间）
        updateTimeFreezeState();
        
        LOGGER.debug("[TimelineWorld] 模型变更，刷新时间关键帧");
    }

    /**
     * 从当前激活轨道重建采样器
     * 
     * <p>公开方法，供属性面板等外部组件调用，用于在关键帧属性修改后刷新采样器。
     */
    public synchronized void rebuildFromActiveTrack() {
        if (!hasValidActiveTrack()) {
            sampler.setKeyframes(new ArrayList<>());
            return;
        }

        List<TimeKeyframe> kfs = collectTimeKeyframes(activeTimeTrack);
        sampler.setKeyframes(kfs);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[TimelineWorld] Rebuild from track '{}', keyframes = {}",
                    activeTimeTrack.getName(), kfs.size());
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
     * 将采样结果应用到 Minecraft 世界
     * 
     * <p>应用世界时间和太阳角度到 Minecraft 世界。
     * 
     * <p>在 scrubbing 状态下，可以调整玩家视角以提供更好的预览体验。
     */
    private void applyToWorld(TimeSample sample) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return;
        }

        ClientWorld world = client.world;

        // ⭐ 设置世界时间（使用采样的 worldTimeTicks）
        // 使用 setTime 方法，参考 ChronoBlocksScreen.freezeWorld() 的实现
        long worldTime = sample.getWorldTimeTicks();
        world.setTime(worldTime, worldTime, false);

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("[TimelineWorld] 应用时间采样: {}", sample);
        }

        // ⭐ 如果使用自定义太阳模式，可以调整玩家视角预览太阳位置
        // 注意：这在 scrubbing 和属性面板预览时都很有用
        if (sample.isUseCustomSun() && client.player != null) {
            // 将太阳方位角转换为玩家 yaw（需要转换坐标系）
            // Minecraft 中：yaw 0 = 正南，90 = 正西，180 = 正北，270 = 正东
            // 太阳方位角：0 = 正北，90 = 正东，180 = 正南，270 = 正西
            // 转换：yaw = 180 - azimuth
            float yaw = 180.0f - sample.getSunAzimuth();
            // 将高度角转换为 pitch（需要反转，因为 pitch 向下为正）
            float pitch = -sample.getSunElevation();
            
            client.player.setYaw(yaw);
            client.player.setPitch(pitch);
        }

        // ⭐ 自定义太阳角度的实际应用已通过 WorldTimeMixin 实现
        // WorldTimeMixin 会拦截 World.getSkyAngle()，当使用自定义太阳模式时，
        // 会根据 sunElevation 和 sunAzimuth 计算并返回自定义的天空角度，
        // 从而真正影响太阳的渲染位置。
    }

    /**
     * 获取当前采样结果（供 Mixin 使用）
     * 
     * <p>注意：此方法会返回当前时间线的采样结果，无论是否在播放或 scrubbing。
     * 这允许 Mixin 在渲染时（即使时间线静止）也能获取正确的太阳角度。
     */
    public synchronized Optional<TimeSample> getCurrentSample() {
        if (!enabled || timelineModel == null) {
            return Optional.empty();
        }

        if (!hasValidActiveTrack()) {
            return Optional.empty();
        }

        // ⭐ 不再检查 playing/scrubbing 状态，允许在属性面板修改时也能获取采样
        // 这样 Mixin 可以在任何时候（包括静止状态）使用正确的太阳角度
        float currentTime = timelineModel.getCurrentTime();
        return sampler.sample(currentTime);
    }

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

    // ================== 原版时间系统冻结/恢复 ==================

    /**
     * 冻结原版时间系统
     * 
     * <p>当时间轨道被创建或激活时调用，禁用原版的时间流逝功能（doDaylightCycle），
     * 避免时间轨道控制的世界时间与原版时间系统冲突。
     */
    private synchronized void freezeMinecraftTime() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return;
        }

        // 在客户端，需要通过服务器来修改游戏规则
        // 如果服务器存在（单机模式或多人模式），则修改游戏规则
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
                    timeFrozen = true;

                    LOGGER.info("[TimelineWorld] ✅ 已冻结原版时间系统 (doDaylightCycle = false)");
                    return;
                } catch (Exception e) {
                    LOGGER.warn("[TimelineWorld] 无法修改游戏规则: {}", e.getMessage());
                }
            }
        }

        // 如果没有服务器（纯客户端模式），则只记录日志
        // 时间冻结将通过 Mixin 来实现（ClientWorldMixin.onTickTime）
        timeFrozen = true;
        LOGGER.info("[TimelineWorld] ✅ 已标记时间冻结（将通过 Mixin 实现，无服务器访问）");
    }

    /**
     * 恢复原版时间系统
     * 
     * <p>当时间轨道被禁用或删除时调用，恢复原版的时间流动功能。
     */
    private synchronized void resumeMinecraftTime() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
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

                    timeFrozen = false;
                    savedDoDaylightCycle = null;
                    return;
                } catch (Exception e) {
                    LOGGER.warn("[TimelineWorld] 无法恢复游戏规则: {}", e.getMessage());
                }
            }
        }

        // 如果没有服务器，只清除标记
        timeFrozen = false;
        savedDoDaylightCycle = null;
        LOGGER.info("[TimelineWorld] ✅ 已清除时间冻结标记");
    }

    /**
     * 更新时间冻结状态
     * 
     * <p>关键设计：只有存在"自定义太阳"关键帧时才需要冻结原版时间系统。
     * 如果所有关键帧都是"使用世界时间"模式，则不需要冻结，让原版时间系统正常工作。
     * 
     * <p>冻结逻辑：
     * - 只有存在自定义太阳关键帧 → 冻结时间（避免原版时间与轨道时间冲突）
     * - 如果所有关键帧都是"使用世界时间" → 不冻结（原版时间系统正常工作）
     * - 如果没有激活轨道或没有关键帧 → 恢复时间
     */
    private synchronized void updateTimeFreezeState() {
        // 检查是否有"自定义太阳"的关键帧
        boolean hasCustomSunKeyframe = sampler.getKeyframes().stream()
                .anyMatch(kf -> !kf.isUseNaturalTime());

        // 只有存在自定义太阳关键帧时才需要冻结
        boolean shouldFreeze = enabled && hasValidActiveTrack() && hasCustomSunKeyframe;

        if (shouldFreeze && !timeFrozen) {
            freezeMinecraftTime();
        } else if (!shouldFreeze && timeFrozen) {
            resumeMinecraftTime();
        }
    }
}
