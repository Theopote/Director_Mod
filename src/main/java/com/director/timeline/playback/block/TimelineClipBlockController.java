package com.director.timeline.playback.block;

import com.director.core.DirectorServices;
import com.director.core.animation.BlockAnimationEngine;
import com.director.core.animation.BlockAnimationSnapshot;
import com.director.core.animation.ClipAnimationConfig;
import com.director.core.animation.sequence.BlockSequenceEntry;
import com.director.core.animation.sequence.BlockSequenceGenerator;
import com.director.core.container.Container;
import com.director.core.container.ContainerManager;
import com.director.core.container.ContainerType;
import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.timeline.playback.TimelinePlaybackSampler;
import com.director.core.container.visibility.BlockVisualStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 时间线片段方块控制器
 *
 * <p>
 * 根据播放头位置和片段配置控制片段对应容器的方块在Minecraft中的显示/隐藏。
 *
 * <p>
 * ◆ 核心设计原则：片段完全独立于容器状态
 * <ul>
 * <li>片段拥有独立的动画配置（ClipAnimationConfig），不依赖容器的状态</li>
 * <li>方块出现完全按照片段的配置（动画模式、序列顺序、缓动曲线等）</li>
 * <li>容器的状态（VISIBLE/HIDDEN）不影响片段的动画控制</li>
 * <li>片段通过 BlockVisualStateManager 控制方块显示，而不是直接修改容器状态</li>
 * </ul>
 *
 * <p>
 * 重要约束：
 * <ul>
 * <li>每个容器有且只有一个片段</li>
 * <li>同一个容器只能在轨道上产生一个片段</li>
 * </ul>
 *
 * <p>
 * 方块出现规则（完全基于片段配置）：
 * <ul>
 * <li>播放头在片段左侧 → 隐藏所有方块</li>
 * <li>播放头在片段范围内 → 根据片段的动画配置（AnimationMode、SequenceParameters）逐个显示方块</li>
 * <li>播放头在片段右侧 → 显示所有方块（完全显示）</li>
 * <li>轨道锁定或隐藏 → 强制隐藏方块（覆盖片段配置）</li>
 * </ul>
 *
 * <p>
 * 动画配置来源：
 * <ul>
 * <li>片段创建时：从容器的
 * {@link com.director.core.container.ContainerAnimationPreset} 初始化</li>
 * <li>片段编辑时：通过
 * {@link com.director.ui.properties.adapters.TimelineClipPropertyAdapter}
 * 修改</li>
 * <li>运行时：完全基于片段的
 * {@link com.director.core.animation.ClipAnimationConfig}</li>
 * </ul>
 */
public class TimelineClipBlockController {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineClipBlockController.class);

    private static TimelineClipBlockController instance;
    private TimelineModel timelineModel;

    // 不再需要旧的AnimationEngineFactory，改用BlockSequenceGenerator

    // 简化遮挡检测器
    private final BlockOcclusionResolver occlusionResolver = new BlockOcclusionResolver();

    // ✅ 缓存清理计数器：每 N 帧清理一次过期缓存
    private int cacheCleanupCounter = 0;

    // ⭐ 缓存容量限制：防止内存无限增长
    private static final int MAX_CONTAINER_CACHE_SIZE = 100;
    private static final int MAX_CLIP_CACHE_SIZE = 200;
    private static final int CACHE_CLEANUP_INTERVAL = 300; // 每300帧检查一次（约5秒）

    /** 上一 UI 帧是否在拖动播放头（用于松手后的追帧预算）。 */
    private boolean wasScrubbing;
    /** 时间线模型变更代数（用于静止预览短路）。 */
    private long timelineRevision = 1L;
    private long lastHandledRevision = -1L;
    private float lastHandledPreviewTime = Float.NaN;
    private boolean timelineDrivingPaused = true;
    private final WholeContainerEffectCursor wholeEffectCursor = new WholeContainerEffectCursor();
    private TimelineModel observedModel;
    private final TimelineModel.ModelChangeListener modelChangeListener = changeType -> timelineRevision++;

    // =====================================================================
    // Perf caches (播放性能关键)
    // =====================================================================

    /**
     * 容器方块列表缓存：避免每帧调用 {@code container.getBlocks()} 造成全量复制与分配。
     * 以 container.modifiedTime 作为版本号，变化时才重建 List。
     */
    private static final class ContainerBlocksCache {
        long modifiedTime;
        List<BlockPos> blocks = List.of();
    }

    /**
     * 片段序列缓存：避免在播放时每帧重新 generate + sort。
     */
    private static final class ClipSequenceCache {
        long containerModifiedTime;
        int containerBlockCount;
        int sequenceParamsHash;
        float clipDuration;
        float animDuration;
        float totalDelay;
        List<BlockSequenceEntry> entries = List.of();
    }

    /**
     * ⭐ 性能关键：可见方块结果缓存
     * 避免每帧重新构建包含数万元素的 HashSet
     */
    private static final class VisibleBlocksCache {
        float lastCurrentTime;
        boolean lastTrackEnabled;
        boolean lastPreviewMode;
        long containerModifiedTime;
        int sequenceParamsHash;
        int animationHash;
        VisibleBlocksResult result;
    }

    private final Map<Container, ContainerBlocksCache> blocksCacheByContainer = new IdentityHashMap<>();
    private final Map<TimelineClip, ClipSequenceCache> sequenceCacheByClip = new IdentityHashMap<>();
    private final Map<UUID, SequenceOrderCache> sequenceOrderCacheByContainerId = new HashMap<>();
    // ⭐ 新增：可见方块结果缓存
    private final Map<TimelineClip, VisibleBlocksCache> visibleBlocksCacheByClip = new IdentityHashMap<>();

    private static final class SequenceOrderCache {
        List<BlockSequenceEntry> entries;
        Map<BlockPos, Integer> order = Map.of();
    }

    private List<BlockPos> getCachedContainerBlocks(Container container) {
        if (container == null) {
            return List.of();
        }
        long mod = container.getModifiedTime();
        ContainerBlocksCache cache = blocksCacheByContainer.get(container);
        if (cache == null) {
            cache = new ContainerBlocksCache();
            blocksCacheByContainer.put(container, cache);
        }
        if (cache.blocks == null
                || cache.modifiedTime != mod
                || (cache.blocks.isEmpty() && container.getBlockCount() > 0)) {
            cache.modifiedTime = mod;
            cache.blocks = new ArrayList<>(container.getBlocksView());
        }
        return cache.blocks != null ? cache.blocks : List.of();
    }

    private static int hashSequenceParameters(com.director.core.animation.sequence.SequenceParameters p) {
        if (p == null) {
            return 0;
        }
        int h = 1;
        h = 31 * h + (p.getFlowType() != null ? p.getFlowType().name().hashCode() : 0);
        h = 31 * h + (p.getFillType() != null ? p.getFillType().name().hashCode() : 0);
        h = 31 * h + (p.getOriginType() != null ? p.getOriginType().name().hashCode() : 0);
        h = 31 * h + Float.floatToIntBits(p.getPlaneYaw());
        h = 31 * h + Float.floatToIntBits(p.getPlanePitch());
        h = 31 * h + Float.floatToIntBits(p.getLayerStep());
        h = 31 * h + p.getCustomX();
        h = 31 * h + p.getCustomY();
        h = 31 * h + p.getCustomZ();
        h = 31 * h + Float.floatToIntBits(p.getPriorityWeight());
        h = 31 * h + (p.isReverse() ? 1 : 0);
        h = 31 * h + (p.isAlternating() ? 1 : 0);
        h = 31 * h + (p.getFloodSeedMode() != null ? p.getFloodSeedMode().name().hashCode() : 0);
        h = 31 * h + (p.getConcurrencyMode() != null ? p.getConcurrencyMode().name().hashCode() : 0);
        h = 31 * h + p.getBatchSize();
        h = 31 * h + Float.floatToIntBits(p.getBatchInterval());
        h = 31 * h + (p.getTimeMappingStrategy() != null ? p.getTimeMappingStrategy().name().hashCode() : 0);
        h = 31 * h + Float.floatToIntBits(p.getPerBlockDuration());
        h = 31 * h + Float.floatToIntBits(p.getGlobalDelay());
        h = 31 * h + (p.getRasterScanAxis() != null ? p.getRasterScanAxis().hashCode() : 0);
        h = 31 * h + Float.floatToIntBits(p.getRasterRowStep());
        h = 31 * h + (p.isRasterZigZag() ? 1 : 0);
        return h;
    }

    private List<BlockSequenceEntry> getOrGenerateSequenceEntries(TimelineClip clip,
                                                                  Container container,
                                                                  ClipAnimationConfig config,
                                                                  float clipDuration,
                                                                  float animDuration,
                                                                  float totalDelay) {
        if (clip == null || container == null) {
            return List.of();
        }

        long containerMod = container.getModifiedTime();
        int blockCount = container.getBlockCount();
        var params = (config != null) ? config.getSequenceParameters() : null;
        int paramsHash = hashSequenceParameters(params);

        ClipSequenceCache cache = sequenceCacheByClip.get(clip);
        if (cache != null
                && cache.containerModifiedTime == containerMod
                && cache.containerBlockCount == blockCount
                && cache.sequenceParamsHash == paramsHash
                && Math.abs(cache.clipDuration - clipDuration) < 1e-4f
                && Math.abs(cache.animDuration - animDuration) < 1e-4f
                && Math.abs(cache.totalDelay - totalDelay) < 1e-4f
                && cache.entries != null
                && !cache.entries.isEmpty()) {
            return cache.entries;
        }

        List<BlockPos> allBlocks = getCachedContainerBlocks(container);
        List<BlockSequenceEntry> generated = BlockSequenceGenerator.generate(
                allBlocks,
                container,
                params,
                clipDuration,
                animDuration,
                totalDelay);
        if (generated != null && generated.size() > 1) {
            // 性能关键：确保按 normalizedTime 单调，便于在可见集计算时提前 break（避免每帧扫描 5w+ entry）
            generated.sort(Comparator.comparingDouble(BlockSequenceEntry::getNormalizedTime));
        }

        ClipSequenceCache next = (cache != null) ? cache : new ClipSequenceCache();
        next.containerModifiedTime = containerMod;
        next.containerBlockCount = blockCount;
        next.sequenceParamsHash = paramsHash;
        next.clipDuration = clipDuration;
        next.animDuration = animDuration;
        next.totalDelay = totalDelay;
        next.entries = (generated != null) ? generated : List.of();
        sequenceCacheByClip.put(clip, next);

        return next.entries;
    }

    private static int upperBoundNormalized(List<BlockSequenceEntry> entries, float normalizedThreshold) {
        if (entries == null || entries.isEmpty()) {
            return 0;
        }
        if (normalizedThreshold <= 0.0f) {
            return 0;
        }
        if (normalizedThreshold >= 1.0f) {
            return entries.size();
        }
        int lo = 0;
        int hi = entries.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            BlockSequenceEntry e = entries.get(mid);
            float n = (e != null) ? e.getNormalizedTime() : 0.0f;
            if (n <= normalizedThreshold) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private static boolean isBlockTrackType(TrackType trackType) {
        return trackType == TrackType.CONTAINER;
    }

    /**
     * 记录每个容器上一帧“应当显示/保留”的方块集合。
     *
     * <p>
     * 用于推导本帧世界方块状态的变化量，并触发区块重建，
     * 避免在逐步清除时出现“黑洞/虚空（区块未及时重渲染）”的视觉问题。
     */
    private final Map<UUID, Set<BlockPos>> lastFrameVisibleBlocks = new HashMap<>();

    /**
     * 上一帧“世界中真实存在”的方块集合（用于驱动 showContainerBlocks 与区块重渲染）。
     *
     * <p>
     * 与 {@link #lastFrameVisibleBlocks} 的语义不同：
     * <ul>
     * <li>lastFrameVisibleBlocks：用于渲染/视觉（包含 ENTER 动画窗口内的 overlay 方块）</li>
     * <li>lastFrameWorldBlocks：用于世界落地（ENTER 仅在动画结束后才进入世界，避免与动画重叠）</li>
     * </ul>
     */
    private final Map<UUID, Set<BlockPos>> lastFrameWorldBlocks = new HashMap<>();

    /** 上一帧该容器的视觉参数（用于避免每帧全量 setBlocksState） */
    private static final class LastVisualParams {
        float opacity;
        boolean occluded;
        long containerModifiedTime;
    }

    private final Map<UUID, LastVisualParams> lastVisualParamsByContainerId = new HashMap<>();

    // ===== 性能采样（节流日志）=====
    private long lastPerfLogMs = 0L;

    private TimelineClipBlockController() {
        // 注册容器删除监听器，主动清理缓存防止内存泄漏
        DirectorServices.get().containers().addListener(new ContainerManager.ContainerListener() {
            @Override
            public void onContainerRemoved(Container container) {
                cleanupCacheForContainer(container);
            }
        });
    }

    /**
     * 清理指定容器的所有相关缓存
     * <p>
     * 当容器被删除时调用，防止缓存无限增长导致内存泄漏
     *
     * @param container 被删除的容器
     */
    private void cleanupCacheForContainer(Container container) {
        if (container == null) {
            return;
        }

        try {
            // 清理容器方块缓存
            blocksCacheByContainer.remove(container);

            // 清理序列顺序缓存
            sequenceOrderCacheByContainerId.remove(container.getId());

            // 清理容器相关的所有片段缓存
            // 注意：需要遍历找到引用该容器的片段
            sequenceCacheByClip.entrySet().removeIf(entry -> container.getId().equals(entry.getKey().getBoundContainerId()));

            visibleBlocksCacheByClip.entrySet().removeIf(entry -> container.getId().equals(entry.getKey().getBoundContainerId()));

            // 清理可见方块跟踪
            lastFrameVisibleBlocks.remove(container.getId());
            lastFrameWorldBlocks.remove(container.getId());
            lastVisualParamsByContainerId.remove(container.getId());

            LOGGER.debug("[TimelineClipBlockController] 已清理容器 {} 的所有缓存", container.getName());
        } catch (Exception e) {
            LOGGER.warn("[TimelineClipBlockController] 清理容器缓存时出错: {}", e.getMessage());
        }
    }

    /**
     * 强制执行容器缓存容量限制
     * <p>
     * 当缓存超过最大容量时，移除最老的条目（简单FIFO策略）
     */
    private void enforceContainerCacheLimit() {
        if (blocksCacheByContainer.size() > MAX_CONTAINER_CACHE_SIZE) {
            int toRemove = blocksCacheByContainer.size() - MAX_CONTAINER_CACHE_SIZE;
            Iterator<Container> iter = blocksCacheByContainer.keySet().iterator();
            for (int i = 0; i < toRemove && iter.hasNext(); i++) {
                iter.next();
                iter.remove();
            }
            LOGGER.debug("[TimelineClipBlockController] 容器缓存超限，已清理 {} 条", toRemove);
        }
    }

    /**
     * 强制执行片段缓存容量限制
     * <p>
     * 当缓存超过最大容量时，移除最老的条目（简单FIFO策略）
     */
    private void enforceClipCacheLimit() {
        // 清理序列缓存
        if (sequenceCacheByClip.size() > MAX_CLIP_CACHE_SIZE) {
            int toRemove = sequenceCacheByClip.size() - MAX_CLIP_CACHE_SIZE;
            Iterator<TimelineClip> iter = sequenceCacheByClip.keySet().iterator();
            for (int i = 0; i < toRemove && iter.hasNext(); i++) {
                iter.next();
                iter.remove();
            }
            LOGGER.debug("[TimelineClipBlockController] 片段序列缓存超限，已清理 {} 条", toRemove);
        }

        // 清理可见方块缓存
        if (visibleBlocksCacheByClip.size() > MAX_CLIP_CACHE_SIZE) {
            int toRemove = visibleBlocksCacheByClip.size() - MAX_CLIP_CACHE_SIZE;
            Iterator<TimelineClip> iter = visibleBlocksCacheByClip.keySet().iterator();
            for (int i = 0; i < toRemove && iter.hasNext(); i++) {
                iter.next();
                iter.remove();
            }
            LOGGER.debug("[TimelineClipBlockController] 可见方块缓存超限，已清理 {} 条", toRemove);
        }
    }

    /**
     * 获取单例实例
     */
    public static TimelineClipBlockController getInstance() {
        if (instance == null) {
            instance = new TimelineClipBlockController();
        }
        return instance;
    }

    /**
     * 设置时间线模型（由TimelinePanel初始化时调用）
     */
    public void setTimelineModel(TimelineModel model) {
        this.timelineModel = model;
        invalidateState();
    }

    /** 强制下一帧重算（片段绑定/拖放后调用）。 */
    public void invalidateState() {
        timelineRevision++;
        lastHandledRevision = -1L;
        lastHandledPreviewTime = Float.NaN;
        timelineDrivingPaused = false;
    }

    /**
     * 是否需要在当前帧运行方块控制器（播放 / 拖动 / 模型或时间变化后的单次同步）。
     */
    public boolean needsClipBlockUpdate(TimelineModel model) {
        if (model == null) {
            return false;
        }
        if (model.isPlaying() || model.isScrubbing()) {
            return true;
        }
        if (timelineRevision != lastHandledRevision) {
            return true;
        }
        float currentTime = model.getCurrentTime();
        boolean timeChanged = Float.isNaN(lastHandledPreviewTime)
                || Math.abs(currentTime - lastHandledPreviewTime) > 1e-4f;
        if (timeChanged) {
            return true;
        }
        // 【修复】静止预览时，若 timelineDrivingPaused 还是 false（分帧追平尚未被暂停），
        // 仍然需要进入 update() 执行一次帧末尾的 pauseTimelineDriving()，
        // 否则 tick() 会持续处理已经完成的时间线目标，浪费服务端资源。
        return !timelineDrivingPaused;
    }

    private void ensureModelListenerBound(TimelineModel model) {
        if (model == observedModel) {
            return;
        }
        if (observedModel != null) {
            observedModel.removeModelChangeListener(modelChangeListener);
        }
        wholeEffectCursor.reset();
        observedModel = model;
        if (observedModel != null) {
            observedModel.addModelChangeListener(modelChangeListener);
        }
        invalidateState();
    }

    private TimelineModel resolveActiveModel() {
        if (timelineModel != null) {
            return timelineModel;
        }
        return TimelinePlaybackSampler.resolveModel();
    }

    /**
     * 更新所有片段对应容器的方块显示状态（每帧调用）
     *
     * <p>
     * ◆ 核心逻辑：完全基于片段配置，不依赖容器状态
     * <ul>
     * <li>遍历所有片段，根据播放头位置和片段配置计算应该显示的方块</li>
     * <li>使用 BlockVisualStateManager 控制方块显示，而不是直接修改容器状态</li>
     * <li>容器的状态（VISIBLE/HIDDEN）不影响片段的动画控制</li>
     * </ul>
     *
     * <p>
     * 注意：每个容器有且只有一个片段，所以逻辑可以简化
     */
    public void update() {
        TimelineModel activeModel = resolveActiveModel();
        if (activeModel == null) {
            wholeEffectCursor.reset();
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[TimelineClipBlockController] timelineModel 为空，跳过更新");
            }
            return;
        }

        final long t0 = System.nanoTime();
        ensureModelListenerBound(activeModel);

        float currentTime = activeModel.getCurrentTime();
        boolean playing = activeModel.isPlaying();
        boolean scrubbing = activeModel.isScrubbing();
        wholeEffectCursor.beginFrame(currentTime, playing, scrubbing);
        boolean previewMode = !playing;
        boolean previewTimeChanged = Float.isNaN(lastHandledPreviewTime)
                || Math.abs(currentTime - lastHandledPreviewTime) > com.director.core.animation.AnimationConstants.TIME_EPSILON;

        if (!playing
                && !scrubbing
                && !previewTimeChanged
                && timelineRevision == lastHandledRevision) {
            return;
        }
        TimelinePlacementBudget.beginFrame(scrubbing, wasScrubbing, previewTimeChanged);
        wasScrubbing = scrubbing;
        final boolean placementAccelerated = TimelinePlacementBudget.isPlacementAccelerated();

        BlockVisualStateManager visualStateManager = BlockVisualStateManager.getInstance();

        // 预览模式：世界方块由 Minecraft 直接渲染，不维护数万规模的 visualOverrides
        visualStateManager.clearFrame();

        // currentFrameVisibleBlocks：保持为“渲染可见集合”（用于遮挡/overlay/视觉状态）
        Map<UUID, Set<BlockPos>> currentFrameVisibleBlocks = new LinkedHashMap<>();
        // currentFrameWorldBlocks：用于真实世界方块落地（showContainerBlocks）
        Map<UUID, Set<BlockPos>> currentFrameWorldBlocks = new LinkedHashMap<>();
        Map<UUID, Float> currentFrameOpacity = new LinkedHashMap<>();
        Map<UUID, List<BlockSequenceEntry>> currentFrameSequences = new LinkedHashMap<>();
        Map<UUID, ClipAnimationConfig> currentFrameConfigs = new LinkedHashMap<>();

        // 本帧世界方块（AIR/恢复）变化导致的重渲染位置（与视觉覆盖层无关）

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("[TimelineClipBlockController] 开始更新 (当前时间: {:.2f}s)", currentTime);
        }

        final long tAfterClear = System.nanoTime();

        // ◆ 步骤1：遍历所有轨道和片段，计算每个容器在当前时间应该显示的方块和其默认透明度
        for (TimelineTrack track : activeModel.getAllTracks()) {
            if (track == null) {
                continue;
            }

            boolean trackEnabled = !track.isLocked() && track.isVisible();
            TrackType trackType = track.getType();
            if (!isBlockTrackType(trackType)) {
                continue;
            }

            for (TimelineClip clip : track.getClips()) {
                if (clip == null || clip.getBoundContainerId() == null) {
                    continue;
                }

                UUID containerId = clip.getBoundContainerId();
                Container container = DirectorServices.get().containers().getContainer(containerId);
                if (container == null) {
                    continue;
                }

                ClipAnimationConfig config = clip.getAnimationConfig();
                if (config == null) {
                    config = ClipAnimationConfig.defaultConfig();
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("[TimelineClipBlockController] Clip {} animation config missing, using default",
                                clip.getLabel());
                    }
                }
                // ✅ 兼容旧模式：把“随机/螺旋/切片/波浪/扩散/向下生长”等迁移到 SequenceParameters
                com.director.core.animation.LegacyAnimationModeMigrator.migrateIfNeeded(config);
                // ✅ 兼容旧高级参数：例如 flashWhite -> EffectInstance
                com.director.core.animation.LegacyAdvancedParamsMigrator.migrateIfNeeded(config);
                currentFrameConfigs.put(containerId, config);

                // ◆ 根据播放头位置和片段配置计算应该显示的方块
                // 完全基于片段的ClipAnimationConfig，不依赖容器状态
                VisibleBlocksResult result = calculateVisibleBlocks(
                        clip, currentTime, trackEnabled, container, config, previewMode);
                Set<BlockPos> renderBlocks = result.renderBlocks();
                Set<BlockPos> worldBlocks = result.worldBlocks();
                currentFrameVisibleBlocks.put(containerId, renderBlocks);
                currentFrameWorldBlocks.put(containerId, worldBlocks);

                // ✅ 计算透明度：使用可见性关键帧和 post-animation fade
                // 支持可见性关键帧和 post-animation fade 功能
                float finalOpacity = clip.computeAlphaAtGlobalTime(currentTime);
                currentFrameOpacity.put(containerId, finalOpacity);

                currentFrameSequences.put(containerId, result.sequenceEntries());

                if (LOGGER.isTraceEnabled() && !renderBlocks.isEmpty()) {
                    LOGGER.trace(
                            "[TimelineClipBlockController] 片段 {} (容器: {}) render={} world={} (时间: {:.2f}s, 片段范围: [{:.2f}s, {:.2f}s])",
                            clip.getLabel(), container.getName(), renderBlocks.size(), worldBlocks.size(), currentTime,
                            clip.getStartTime(), clip.getEndTime());
                }
            }
        }

        Map<UUID, Set<BlockPos>> desiredWorldBlocks = new LinkedHashMap<>();
        for (Map.Entry<UUID, Set<BlockPos>> entry : currentFrameWorldBlocks.entrySet()) {
            Set<BlockPos> worldSet = entry.getValue();
            desiredWorldBlocks.put(entry.getKey(), worldSet != null ? worldSet : Collections.emptySet());
        }

        if (!placementAccelerated) {
            throttleWorldPlacements(currentFrameWorldBlocks, currentFrameSequences);
        }

        Map<UUID, Set<BlockPos>> visibilityWorldBlocks = placementAccelerated ? desiredWorldBlocks : currentFrameWorldBlocks;
        final long tAfterVisible = System.nanoTime();

        // ✅ 步骤1.5：计算世界落地集的变化量（仅用于 needsVisibilitySync 判断）
        //
        // 【关键修复】彻底去掉 addBlockAndNeighborsForRerender 的调用。
        // previewMode 下，世界方块由服务端 tick() 里的 setBlockState 实际修改，
        // MC 客户端收到数据包后会自动触发区块重建，不需要客户端再调用 scheduleBlockRenders。
        // 如果客户端对可见集对称差（几万 pos）手动调用 scheduleBlockRenders，
        // 会导致 GPU 每帧重建几百个 chunk section，帧时间拉到 100ms+，FPS 掉到 7~10。
        // scheduleBlockRenders 仅在 BlockVisualStateManager overlay/suppress 状态变化时才需要，
        // 由下面的 !previewMode 分支通过 visualStateManager.getChangedBlocks() 驱动。
        Map<UUID, Boolean> worldSetChangedByContainer = new HashMap<>();
        for (Map.Entry<UUID, Set<BlockPos>> entry : visibilityWorldBlocks.entrySet()) {
            UUID containerId = entry.getKey();
            Set<BlockPos> now = entry.getValue() != null ? entry.getValue() : java.util.Collections.emptySet();
            Set<BlockPos> prev = lastFrameWorldBlocks.get(containerId);
            if (prev == null) {
                worldSetChangedByContainer.put(containerId, true);
                continue;
            }
            // ⭐ 性能优化：快速检测集合变化 - 先比较大小
            if (prev.size() != now.size()) {
                worldSetChangedByContainer.put(containerId, true);
                continue;
            }
            // 大小相同，再检查内容（可以提前break）
            boolean changed = false;
            for (BlockPos p : prev) {
                if (!now.contains(p)) { changed = true; break; }
            }
            if (!changed) {
                for (BlockPos p : now) {
                    if (!prev.contains(p)) { changed = true; break; }
                }
            }
            worldSetChangedByContainer.put(containerId, changed);
        }
        Map<UUID, Boolean> renderSetChangedByContainer = new HashMap<>();
        for (Map.Entry<UUID, Set<BlockPos>> entry : currentFrameVisibleBlocks.entrySet()) {
            UUID containerId = entry.getKey();
            Set<BlockPos> now = entry.getValue() != null ? entry.getValue() : java.util.Collections.emptySet();
            Set<BlockPos> prev = lastFrameVisibleBlocks.get(containerId);
            if (prev == null) {
                renderSetChangedByContainer.put(containerId, true);
                continue;
            }

            // ⭐ 性能优化：快速检测集合变化 - 先比较大小
            if (prev.size() != now.size()) {
                renderSetChangedByContainer.put(containerId, true);
                continue;
            }
            // 大小相同，再检查内容（可以提前break）
            boolean changed = false;
            for (BlockPos p : prev) {
                if (!now.contains(p)) {
                    changed = true;
                    break;
                }
            }
            if (!changed) {
                for (BlockPos p : now) {
                    if (!prev.contains(p)) {
                        changed = true;
                        break;
                    }
                }
            }
            renderSetChangedByContainer.put(containerId, changed);
        }

        final long tAfterDiff = System.nanoTime();

        // ⭐ 步骤2：应用遮挡检测（容器之间的半透明效果）
        // 现在主要性能问题（音频渲染）已经解决，可以重新启用遮挡检测
        // 构建容器ID到片段的映射
        Map<UUID, TimelineClip> containerToClipMap = new HashMap<>();
        for (TimelineTrack track : activeModel.getAllTracks()) {
            if (track == null)
                continue;
            if (!isBlockTrackType(track.getType())) {
                continue;
            }
            for (TimelineClip clip : track.getClips()) {
                if (clip != null && clip.getBoundContainerId() != null) {
                    containerToClipMap.put(clip.getBoundContainerId(), clip);
                }
            }
        }

        // ⭐ 重新启用遮挡检测
        Set<UUID> occludedContainers = new HashSet<>();
        applyOcclusionOverrides(currentTime, currentFrameVisibleBlocks, currentFrameOpacity,
                                occludedContainers, containerToClipMap);
        final long tAfterOcclusion = System.nanoTime();

        // ✅ 步骤3：应用方块可见性状态（两层系统）
        // 双重控制机制：
        // 1. 通过 ContainerVisibilityService 控制真实方块显示/隐藏
        // 2. 通过 BlockVisualStateManager 控制视觉状态（透明度、覆盖层等）
        var visibilityService = DirectorServices.get().visibilityOrNull();

        for (Map.Entry<UUID, Set<BlockPos>> entry : currentFrameVisibleBlocks.entrySet()) {
            UUID containerId = entry.getKey();
            // renderBlocks：用于视觉状态/overlay/动画采样
            Set<BlockPos> renderBlocks = entry.getValue();
            // worldBlocks：用于真实世界方块落地（ENTER 延后到动画结束）
            Set<BlockPos> worldBlocks = visibilityWorldBlocks.getOrDefault(containerId,
                    java.util.Collections.emptySet());
            float opacity = currentFrameOpacity.getOrDefault(containerId, 1.0f);

            Container container = DirectorServices.get().containers().getContainer(containerId);
            if (container == null) {
                continue;
            }

            boolean worldSetChanged = worldSetChangedByContainer.getOrDefault(containerId, true);
            boolean renderSetChanged = renderSetChangedByContainer.getOrDefault(containerId, true);
            long containerMod = container.getModifiedTime();
            LastVisualParams lastParams = lastVisualParamsByContainerId.get(containerId);
            boolean containerStructureChanged = (lastParams == null)
                    || (lastParams.containerModifiedTime != containerMod);

            // 🔹 关键改动：判断容器是否被遮挡
            // 如果被遮挡，方块仍然需要在世界中存在，但通过 Mixin 阻止渲染
            TimelineClip occludedClip = containerToClipMap.get(containerId);
            boolean occlusionTransparencyEnabled = occludedClip == null
                    || (occludedClip.getAnimationConfig() != null
                    && occludedClip.getAnimationConfig().isOcclusionTransparencyEnabled());
            boolean isOccluded = occlusionTransparencyEnabled && occludedContainers.contains(containerId);

            boolean visualParamsChanged = lastParams == null
                    || Math.abs(lastParams.opacity - opacity) > 1e-4f
                    || lastParams.occluded != isOccluded
                    || containerStructureChanged;

            // ◆ 关键：先恢复真实方块（即使容器状态是 HIDDEN）
            // 容器被拖拽到时间线时会被设置为HIDDEN，但我们需要根据片段配置显示方块
            // 🔹 对于被遮挡的容器：方块需要存在（不是 AIR），但通过 Mixin 阻止 Minecraft 渲染
            // 🔹 对于未被遮挡的容器：正常恢复方块，让 Minecraft 正常渲染
            if (visibilityService != null) {
                // ◆ 步骤1：确保容器绑定了世界
                ensureContainerWorldBinding(container);

                // ◆ 步骤2：确保容器有快照（如果没有，先创建快照）
                TimelineClip boundClip = containerToClipMap.get(containerId);
                TrackType boundType = boundClip != null && boundClip.getParentTrack() != null
                        ? boundClip.getParentTrack().getType()
                        : TrackType.CONTAINER;
                ensureContainerSnapshot(container, visibilityService, boundType);

                // 仅在世界落地集变化、拖动/追帧/定位时才同步世界方块。
                // 【修复】去掉 containerStructureChanged：容器结构变化只影响渲染，不应触发世界方块同步。
                // containerStructureChanged=true 时（如容器刚拖入时间线），若同时触发 showContainerBlocksForTimeline，
                // 会绕过 backfill 幂等保护，导致每帧调用 O(N) 扫描 + 向服务端提交无用任务。
                boolean needsVisibilitySync = worldSetChanged
                        || scrubbing
                        || TimelinePlacementBudget.isCatchUp()
                        || (previewMode && previewTimeChanged);
                boolean deferWorldSync = previewMode
                        && !scrubbing
                        && !TimelinePlacementBudget.isCatchUp()
                        && boundClip != null
                        && currentTime < boundClip.getStartTime() - 1e-3f
                        && (worldBlocks == null || worldBlocks.isEmpty());
                if (needsVisibilitySync && !deferWorldSync) {
                    visibilityService.resumeTimelineDriving();
                    // 时间线驱动：统一走分帧追平，避免 showContainerBlocks 的 firstCall 全量同步/强制光照刷新卡死主线程
                    visibilityService.showContainerBlocksForTimeline(container, worldBlocks);
                }
            } else {
                LOGGER.warn("[TimelineClipBlockController] ContainerVisibilityService 未初始化，无法控制方块显示");
            }

            // ◆ 然后设置视觉状态（用于半透明效果和遮挡淡化）——仅播放时需要
            ClipAnimationConfig frameConfig = currentFrameConfigs.get(containerId);
            if (frameConfig != null && frameConfig.isWholeContainer()) {
                registerWholeContainerEntries(visualStateManager, container, occludedClip,
                        frameConfig, currentTime, renderBlocks, opacity);
            } else if (!previewMode) {
                Set<BlockPos> prevVisible = lastFrameVisibleBlocks.getOrDefault(containerId,
                        java.util.Collections.emptySet());

                if (containerStructureChanged) {
                    List<BlockPos> all = getCachedContainerBlocks(container);
                    if (renderBlocks == null || renderBlocks.isEmpty()) {
                        visualStateManager.setBlocksState(all, BlockVisualStateManager.BlockVisualState.hidden());
                    } else {
                        visualStateManager.setBlocksState(all, BlockVisualStateManager.BlockVisualState.hidden());
                        BlockVisualStateManager.BlockVisualState visibleState = (opacity < 1.0f)
                                ? BlockVisualStateManager.BlockVisualState.ghost(opacity, true, true)
                                .withSuppressWorldRendering(true)
                                : BlockVisualStateManager.BlockVisualState.normal();
                        visualStateManager.setBlocksState(renderBlocks, visibleState);
                    }
                } else {
                    if (renderSetChanged) {
                        if (prevVisible != null && !prevVisible.isEmpty()) {
                            Set<BlockPos> removed = new HashSet<>(prevVisible);
                            if (renderBlocks != null && !renderBlocks.isEmpty()) {
                                removed.removeAll(renderBlocks);
                            }
                            if (!removed.isEmpty()) {
                                visualStateManager.setBlocksState(removed,
                                        BlockVisualStateManager.BlockVisualState.hidden());
                            }
                        }

                        if (renderBlocks != null && !renderBlocks.isEmpty()) {
                            Set<BlockPos> added = new HashSet<>(renderBlocks);
                            if (prevVisible != null && !prevVisible.isEmpty()) {
                                added.removeAll(prevVisible);
                            }
                            if (!added.isEmpty()) {
                                BlockVisualStateManager.BlockVisualState visibleState = (opacity < 1.0f)
                                        ? BlockVisualStateManager.BlockVisualState.ghost(opacity, true, true)
                                        .withSuppressWorldRendering(true)
                                        : BlockVisualStateManager.BlockVisualState.normal();
                                visualStateManager.setBlocksState(added, visibleState);
                            }
                        }
                    }

                    if (visualParamsChanged) {
                        if (renderBlocks != null && !renderBlocks.isEmpty()) {
                            BlockVisualStateManager.BlockVisualState visibleState = (opacity < 1.0f)
                                    ? BlockVisualStateManager.BlockVisualState.ghost(opacity, true, true)
                                    .withSuppressWorldRendering(true)
                                    : BlockVisualStateManager.BlockVisualState.normal();
                            visualStateManager.setBlocksState(renderBlocks, visibleState);
                        }
                    }
                }

                TimelineClip playbackClip = containerToClipMap.get(containerId);
                ClipAnimationConfig playbackConfig = currentFrameConfigs.get(containerId);
                List<BlockSequenceEntry> sequenceEntries = currentFrameSequences.getOrDefault(containerId,
                        Collections.emptyList());
                registerRenderEntriesForContainer(
                        visualStateManager,
                        container,
                        playbackClip,
                        playbackConfig,
                        sequenceEntries,
                        currentTime,
                        renderBlocks,
                        previewMode);
            } else {
                visualStateManager.clearOverridesFor(container);
            }

            if (lastParams == null) {
                lastParams = new LastVisualParams();
                lastVisualParamsByContainerId.put(containerId, lastParams);
            }
            lastParams.opacity = opacity;
            lastParams.occluded = isOccluded;
            lastParams.containerModifiedTime = containerMod;
        }
        final long tAfterEntries = System.nanoTime();

        // ⭐ 区块重渲染策略优化
        //
        // 优化策略：
        // 1. 播放模式：正常重渲染，使用节流机制避免过于频繁
        // 2. 预览模式（拖动播放头）：允许重渲染，确保用户看到正确的方块状态
        //    - 但需要限制重渲染范围，避免性能问题
        Set<BlockPos> rerender = new HashSet<>();

        try {
            Set<BlockPos> vsChanged = visualStateManager.getChangedBlocks();
            if (vsChanged != null && !vsChanged.isEmpty()) {
                rerender.addAll(vsChanged);
            }
        } catch (Exception ignored) {
        }
        cleanupContainersNoLongerControlled(desiredWorldBlocks.keySet(), visibilityService, visualStateManager, rerender);

        // ⭐ 额外优化：如果重渲染方块数量太多，分批处理或跳过
        // 避免一次性重渲染导致游戏卡顿
        if (rerender.size() > com.director.core.animation.AnimationConstants.MAX_RERENDER_BLOCKS) {
            LOGGER.warn("[TimelineClipBlockController] 重渲染方块数量过多 ({}), 跳过本帧重渲染以避免卡顿", rerender.size());
            rerender.clear();
        } else if (!rerender.isEmpty()) {
            scheduleBlockRerender(rerender);
        }

        final long tAfterRerender = System.nanoTime();

        // 更新 lastFrameVisibleBlocks（渲染可见集）与 lastFrameWorldBlocks（世界落地集），并清理已不存在的容器
        lastFrameVisibleBlocks.keySet().retainAll(currentFrameVisibleBlocks.keySet());
        lastFrameWorldBlocks.keySet().retainAll(desiredWorldBlocks.keySet());
        lastVisualParamsByContainerId.keySet().retainAll(currentFrameVisibleBlocks.keySet());
        for (Map.Entry<UUID, Set<BlockPos>> e : currentFrameVisibleBlocks.entrySet()) {
            UUID id = e.getKey();
            Set<BlockPos> s = e.getValue();
            // 性能关键：避免每帧复制大 Set；该 Set 在本帧之后不再被修改，直接复用引用即可。
            lastFrameVisibleBlocks.put(id, s != null ? s : java.util.Collections.emptySet());
        }
        Map<UUID, Set<BlockPos>> lastWorldBlocksSource = placementAccelerated ? desiredWorldBlocks : currentFrameWorldBlocks;
        for (Map.Entry<UUID, Set<BlockPos>> e : lastWorldBlocksSource.entrySet()) {
            UUID id = e.getKey();
            Set<BlockPos> s = e.getValue();
            lastFrameWorldBlocks.put(id, s != null ? s : java.util.Collections.emptySet());
        }
        lastHandledRevision = timelineRevision;
        if (!playing && !scrubbing) {
            lastHandledPreviewTime = currentTime;
            if (!timelineDrivingPaused && visibilityService != null) {
                visibilityService.pauseTimelineDriving();
                timelineDrivingPaused = true;
            }
        } else {
            lastHandledPreviewTime = Float.NaN;
            timelineDrivingPaused = false;
        }
        visualStateManager.finishFrame();

        // ✅ 修复：定期清理不再活跃的容器/片段缓存，防止内存泄漏
        // 每 N 帧（约 3 秒 @ 60fps）执行一次清理
        if (++cacheCleanupCounter >= com.director.core.animation.AnimationConstants.CACHE_CLEANUP_INTERVAL) {
            cacheCleanupCounter = 0;
            Set<UUID> activeContainerIds = currentFrameVisibleBlocks.keySet();

            // 清理不活跃的缓存
            blocksCacheByContainer.entrySet().removeIf(e -> {
                Container c = e.getKey();
                return c == null || !activeContainerIds.contains(c.getId());
            });
            sequenceCacheByClip.entrySet().removeIf(e -> {
                TimelineClip c = e.getKey();
                return c == null || !activeContainerIds.contains(c.getBoundContainerId());
            });
            // ⭐ 同时清理可见方块结果缓存
            visibleBlocksCacheByClip.entrySet().removeIf(e -> {
                TimelineClip c = e.getKey();
                return c == null || !activeContainerIds.contains(c.getBoundContainerId());
            });
            sequenceOrderCacheByContainerId.keySet().retainAll(activeContainerIds);

            // ⭐ 新增：强制容量限制，防止缓存无限增长
            enforceContainerCacheLimit();
            enforceClipCacheLimit();
        }

        // ===== 节流性能日志（定期输出）=====
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastPerfLogMs > com.director.core.animation.AnimationConstants.PERF_LOG_INTERVAL_MS && LOGGER.isInfoEnabled()) {
            lastPerfLogMs = nowMs;
            int containerCount = currentFrameVisibleBlocks.size();
            long visibleTotal = 0L;
            long seqTotal = 0L;
            for (var e : currentFrameVisibleBlocks.entrySet()) {
                if (e.getValue() != null) {
                    visibleTotal += e.getValue().size();
                }
            }
            for (var e : currentFrameSequences.entrySet()) {
                var list = e.getValue();
                if (list != null) {
                    seqTotal += list.size();
                }
            }
            int renderEntryCount = 0;
            try {
                var entries = visualStateManager.getRenderEntries();
                if (entries != null) {
                    renderEntryCount = entries.size();
                }
            } catch (Exception ignored) {
            }
            double msTotal = (System.nanoTime() - t0) / 1_000_000.0;
            double msClear = (tAfterClear - t0) / 1_000_000.0;
            double msStep1 = (tAfterVisible - tAfterClear) / 1_000_000.0;
            double msDiff = (tAfterDiff - tAfterVisible) / 1_000_000.0;
            double msOcc = (tAfterOcclusion - tAfterDiff) / 1_000_000.0;
            double msStep3 = (tAfterEntries - tAfterOcclusion) / 1_000_000.0;
            double msRerender = (tAfterRerender - tAfterEntries) / 1_000_000.0;

            LOGGER.info(
                    "[Perf][TimelineClipBlockController] total={}ms (clear={}ms, step1_visible={}ms, diff={}ms, occlusion={}ms, step3_apply+entries={}ms, rerender={}ms) containers={}, visibleTotal={}, seqTotal={}, renderEntries={}",
                    String.format("%.2f", msTotal),
                    String.format("%.2f", msClear),
                    String.format("%.2f", msStep1),
                    String.format("%.2f", msDiff),
                    String.format("%.2f", msOcc),
                    String.format("%.2f", msStep3),
                    String.format("%.2f", msRerender),
                    containerCount,
                    visibleTotal,
                    seqTotal,
                    renderEntryCount);
        }
    }

    /**
     * 调度方块所在区块的重渲染，确保suppressWorldRendering 变化立即反映到原版渲染
     */
    private void scheduleBlockRerender(Set<BlockPos> changedBlocks) {
        if (changedBlocks == null || changedBlocks.isEmpty()) {
            return;
        }
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.worldRenderer == null) {
                return;
            }
            // 退出世界/关服阶段：不要再调度重渲染，避免底层 executor 已关闭导致异常/卡顿
            if (client.world == null || client.getNetworkHandler() == null) {
                return;
            }

            // 性能关键：用“全局包围盒”会把大量无关区块也重建，逐帧播放时极易导致 FPS 跌到个位数。
            // 改为按 chunk 分组调度：只重建实际受影响的 chunk 区域。
            final class ChunkBox {
                int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
                int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

                void include(BlockPos p) {
                    minX = Math.min(minX, p.getX());
                    minY = Math.min(minY, p.getY());
                    minZ = Math.min(minZ, p.getZ());
                    maxX = Math.max(maxX, p.getX());
                    maxY = Math.max(maxY, p.getY());
                    maxZ = Math.max(maxZ, p.getZ());
                }

                boolean valid() {
                    return minX <= maxX && minY <= maxY && minZ <= maxZ;
                }
            }

            Map<Long, ChunkBox> byChunk = new HashMap<>();
            for (BlockPos pos : changedBlocks) {
                if (pos == null)
                    continue;
                int cx = pos.getX() >> 4;
                int cz = pos.getZ() >> 4;
                long key = (((long) cx) << 32) ^ (cz & 0xffffffffL);
                ChunkBox box = byChunk.computeIfAbsent(key, k -> new ChunkBox());
                box.include(pos);
            }

            // 每个 chunk 单独调度（把范围扩到 chunk 边界，避免边缘面不刷新）
            for (Map.Entry<Long, ChunkBox> e : byChunk.entrySet()) {
                ChunkBox box = e.getValue();
                if (box == null || !box.valid())
                    continue;

                int minChunkX = box.minX >> 4;
                int maxChunkX = box.maxX >> 4;
                int minChunkZ = box.minZ >> 4;
                int maxChunkZ = box.maxZ >> 4;

                // 受影响的范围通常在单个 chunk 内；保险起见支持跨 chunk
                for (int x = minChunkX; x <= maxChunkX; x++) {
                    for (int z = minChunkZ; z <= maxChunkZ; z++) {
                        int x0 = (x << 4);
                        int z0 = (z << 4);
                        int x1 = x0 + 15;
                        int z1 = z0 + 15;
                        // Y 仅取本 chunk 内受影响的局部范围，并上下扩 1 格避免面刷新缺失
                        int y0 = Math.max(-64, box.minY - 1);
                        int y1 = Math.min(319, box.maxY + 1);
                        client.worldRenderer.scheduleBlockRenders(x0, y0, z0, x1, y1, z1);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[TimelineClipBlockController] 调度区块重渲染失败: {}", e.getMessage());
        }
    }

    /**
     * 计算片段中在当前时间应该“保留/显示”的方块集合（支持容器轨道与清除轨道）。
     *
     * <p>
     * 容器轨道（CONTAINER）规则：
     * <ul>
     * <li>片段左侧：空（全部隐藏）</li>
     * <li>片段范围：逐步显示</li>
     * <li>片段右侧：全量显示</li>
     * </ul>
     *
     * <p>
     * 清除轨道（ERASE）规则（反向）：
     * <ul>
     * <li>片段左侧：全量保留（不清除）</li>
     * <li>片段范围：逐步清除（从全量到空）</li>
     * <li>片段右侧：空（全部清除）</li>
     * </ul>
     *
     * <p>
     * 轨道锁定/隐藏处理：
     * <ul>
     * <li>容器轨道：强制隐藏</li>
     * <li>清除轨道：强制不清除（保留全量）</li>
     * </ul>
     */
    private void throttleWorldPlacements(Map<UUID, Set<BlockPos>> currentFrameWorldBlocks,
                                         Map<UUID, List<BlockSequenceEntry>> currentFrameSequences) {
        if (currentFrameWorldBlocks == null || currentFrameWorldBlocks.isEmpty()) {
            return;
        }

        int remainingGlobalBudget = TimelinePlacementBudget.getGlobalPerFrame();
        int perContainerBudget = TimelinePlacementBudget.getPerContainerPerFrame();
        int throttledContainers = 0;
        int deferredPlacements = 0;

        for (Map.Entry<UUID, Set<BlockPos>> entry : currentFrameWorldBlocks.entrySet()) {
            UUID containerId = entry.getKey();
            Set<BlockPos> desired = entry.getValue() != null ? entry.getValue() : Collections.emptySet();
            Set<BlockPos> previous = lastFrameWorldBlocks.getOrDefault(containerId, Collections.emptySet());

            LinkedHashSet<BlockPos> applied = new LinkedHashSet<>(previous);
            applied.retainAll(desired); // removals happen immediately

            if (desired.isEmpty() || remainingGlobalBudget <= 0) {
                if (remainingGlobalBudget <= 0 && desired.size() > applied.size()) {
                    throttledContainers++;
                    deferredPlacements += desired.size() - applied.size();
                }
                entry.setValue(applied);
                continue;
            }

            List<BlockPos> orderedAdditions = collectOrderedAdditions(containerId, desired, previous, currentFrameSequences);
            if (orderedAdditions.isEmpty()) {
                entry.setValue(applied);
                continue;
            }

            int containerBudget = Math.min(perContainerBudget, remainingGlobalBudget);
            int allowed = Math.min(containerBudget, orderedAdditions.size());
            for (int i = 0; i < allowed; i++) {
                applied.add(orderedAdditions.get(i));
            }

            remainingGlobalBudget -= allowed;
            int deferred = orderedAdditions.size() - allowed;
            if (deferred > 0) {
                throttledContainers++;
                deferredPlacements += deferred;
            }

            entry.setValue(applied);
        }

        if (throttledContainers > 0 && LOGGER.isDebugEnabled()) {
            LOGGER.debug("[TimelineClipBlockController] 节流世界落地: containers={}, deferredPlacements={}, budgetPerTick={}, perContainer={}",
                    throttledContainers, deferredPlacements, TimelinePlacementBudget.getGlobalPerFrame(), perContainerBudget);
        }
    }

    private List<BlockPos> collectOrderedAdditions(UUID containerId,
                                                   Set<BlockPos> desired,
                                                   Set<BlockPos> previous,
                                                   Map<UUID, List<BlockSequenceEntry>> currentFrameSequences) {
        List<BlockSequenceEntry> entries = currentFrameSequences != null ? currentFrameSequences.get(containerId) : null;
        List<BlockPos> additions = new ArrayList<>();
        for (BlockPos pos : desired) {
            if (!previous.contains(pos)) {
                additions.add(pos);
            }
        }
        if (additions.size() <= 1 || entries == null || entries.isEmpty()) {
            return additions;
        }

        Map<BlockPos, Integer> order = getSequenceOrder(containerId, entries);
        additions.sort(Comparator.comparingInt(pos -> order.getOrDefault(pos, Integer.MAX_VALUE)));
        return additions;
    }

    private Map<BlockPos, Integer> getSequenceOrder(UUID containerId, List<BlockSequenceEntry> entries) {
        if (containerId == null || entries == null || entries.isEmpty()) {
            return Map.of();
        }
        SequenceOrderCache cache = sequenceOrderCacheByContainerId.get(containerId);
        if (cache != null && cache.entries == entries) {
            return cache.order;
        }
        Map<BlockPos, Integer> order = new HashMap<>(Math.max(16, (int) (entries.size() / 0.75f) + 1));
        for (int i = 0; i < entries.size(); i++) {
            BlockSequenceEntry entry = entries.get(i);
            BlockPos pos = entry != null ? entry.getPos() : null;
            if (pos != null) {
                order.putIfAbsent(pos, i);
            }
        }
        SequenceOrderCache next = cache != null ? cache : new SequenceOrderCache();
        next.entries = entries;
        next.order = order;
        sequenceOrderCacheByContainerId.put(containerId, next);
        return order;
    }

    private VisibleBlocksResult calculateVisibleBlocks(TimelineClip clip,
                                                       float currentTime,
                                                       boolean trackEnabled,
                                                       Container container,
                                                       ClipAnimationConfig config,
                                                       boolean previewMode) {
        // Whole-object boundaries must also react to property edits at a stationary playhead.
        if (config != null && config.isWholeContainer()) {
            return calculateVisibleBlocksInternal(clip, currentTime, trackEnabled, container, config, previewMode);
        }
        // ⭐ 性能关键优化：检查可见方块结果缓存
        // 如果时间、轨道状态、容器内容都没变，直接返回缓存结果
        // 这避免了每帧重新构建包含数万元素的 HashSet
        VisibleBlocksCache cache = visibleBlocksCacheByClip.get(clip);
        if (cache != null && container != null && config != null) {
            long containerMod = container.getModifiedTime();
            var params = config.getSequenceParameters();
            int paramsHash = hashSequenceParameters(params);

            // 检查所有影响可见方块的因素是否都没变
            if (Math.abs(cache.lastCurrentTime - currentTime) < com.director.core.animation.AnimationConstants.VISIBLE_BLOCKS_CACHE_TIME_EPSILON &&
                cache.lastTrackEnabled == trackEnabled &&
                cache.lastPreviewMode == previewMode &&
                cache.containerModifiedTime == containerMod &&
                cache.sequenceParamsHash == paramsHash &&
                cache.animationHash == visibleAnimationHash(clip, config) &&
                cache.result != null) {
                // 缓存命中！直接返回
                return cache.result;
            }
        }

        // 缓存未命中，需要计算
        VisibleBlocksResult result = calculateVisibleBlocksInternal(
            clip, currentTime, trackEnabled, container, config, previewMode);

        // 保存到缓存
        if (cache == null) {
            cache = new VisibleBlocksCache();
            visibleBlocksCacheByClip.put(clip, cache);
        }
        cache.lastCurrentTime = currentTime;
        cache.lastTrackEnabled = trackEnabled;
        cache.lastPreviewMode = previewMode;
        cache.containerModifiedTime = container != null ? container.getModifiedTime() : 0;
        cache.sequenceParamsHash = config != null ? hashSequenceParameters(config.getSequenceParameters()) : 0;
        cache.animationHash = visibleAnimationHash(clip, config);
        cache.result = result;

        return result;
    }

    private static int visibleAnimationHash(TimelineClip clip, ClipAnimationConfig config) {
        return config == null ? 0 : Objects.hash(clip.getStartTime(), clip.getEndTime(), config.getScope(),
                config.getMode(), config.getPhase(), config.getDuration(), config.getDelay());
    }

    private VisibleBlocksResult calculateVisibleBlocksInternal(TimelineClip clip,
                                                       float currentTime,
                                                       boolean trackEnabled,
                                                       Container container,
                                                       ClipAnimationConfig config,
                                                       boolean previewMode) {
        List<BlockSequenceEntry> sequenceEntries = List.of();

        // ✅ 基于容器类型判断是否为拆除容器（不再依赖轨道类型）
        boolean isDemolition = container != null && container.getType() == ContainerType.DEMOLITION;

        List<BlockPos> allBlocks = getCachedContainerBlocks(container);
        if (allBlocks.isEmpty()) {
            return new VisibleBlocksResult(Collections.emptySet(), Collections.emptySet(), sequenceEntries);
        }

        // 轨道禁用：普通容器 => 隐藏；拆除容器 => 不清除（保留全量）
        if (!trackEnabled) {
            if (isDemolition) {
                Set<BlockPos> all = newBlockSet(allBlocks);
                return new VisibleBlocksResult(all, all, sequenceEntries);
            }
            return new VisibleBlocksResult(Collections.emptySet(), Collections.emptySet(), sequenceEntries);
        }

        float clipStart = clip.getStartTime();
        float clipEnd = clip.getEndTime();

        // 片段左侧
        if (currentTime < clipStart) {
            if (isDemolition) {
                Set<BlockPos> all = newBlockSet(allBlocks);
                return new VisibleBlocksResult(all, all, sequenceEntries);
            }
            return new VisibleBlocksResult(Collections.emptySet(), Collections.emptySet(), sequenceEntries);
        }

        if (config != null && config.isWholeContainer()) {
            var frame = com.director.core.animation.WholeContainerAnimation.sample(
                    config, clipStart, clipEnd, currentTime, isDemolition);
            Set<BlockPos> all = newBlockSet(allBlocks);
            return new VisibleBlocksResult(frame.worldVisible() ? all : Collections.emptySet(),
                    frame.renderVisible() ? all : Collections.emptySet(), List.of());
        }

        // 片段范围内：按序列推进
        if (currentTime >= clipStart && currentTime <= clipEnd) {
            float clipDuration = clipEnd - clipStart;
            float animDuration = clipDuration;
            float delay = 0.0f;
            if (config != null) {
                animDuration = Math.clamp(config.getDuration(), 0.05f, clipDuration);
                delay = Math.max(0.0f, config.getDelay());
                try {
                    var seq = config.getSequenceParameters();
                    if (seq != null) {
                        delay += Math.max(0.0f, seq.getGlobalDelay());
                    }
                } catch (Exception ignored) {
                }
            }
            float baseSpan = clipDuration - animDuration - delay;
            boolean fullWindow = baseSpan > 0.0001f;
            float sequenceSpan = fullWindow ? baseSpan : Math.max(0.0001f, clipDuration - delay);

            boolean isInstant = false;
            try {
                isInstant = config != null
                        && config.getMode() == com.director.core.container.AnimationMode.INSTANT;
            } catch (Exception ignored) {
            }

            // 播放头尚未推进到首个方块时刻：跳过 5w+ 序列生成（拖入片段起点时常见）
            float normalizedReveal = (currentTime - clipStart - delay) / sequenceSpan;
            if (!isDemolition) {
                if ((previewMode || isInstant) && normalizedReveal <= 0.0f) {
                    return new VisibleBlocksResult(Collections.emptySet(), Collections.emptySet(), List.of());
                }
                if (!previewMode && !isInstant && normalizedReveal <= 0.0f) {
                    return new VisibleBlocksResult(Collections.emptySet(), Collections.emptySet(), List.of());
                }
            } else if (previewMode || isInstant) {
                if (normalizedReveal <= 0.0f) {
                    Set<BlockPos> all = newBlockSet(allBlocks);
                    return new VisibleBlocksResult(all, all, List.of());
                }
            } else {
                float normalizedEnd = (currentTime - clipStart - delay - (fullWindow ? animDuration : 0.0f))
                        / sequenceSpan;
                if (normalizedEnd <= 0.0f) {
                    Set<BlockPos> all = newBlockSet(allBlocks);
                    return new VisibleBlocksResult(all, all, List.of());
                }
            }

            sequenceEntries = getOrGenerateSequenceEntries(
                    clip,
                    container,
                    config,
                    clipDuration,
                    animDuration,
                    delay);

            if (sequenceEntries == null || sequenceEntries.isEmpty()) {
                // 后备：序列为空时，直接视为全量
                Set<BlockPos> all = newBlockSet(allBlocks);
                return new VisibleBlocksResult(all, all, sequenceEntries);
            }

            // ✅ 基于容器类型判断（不再依赖轨道类型）
            Set<BlockPos> renderBlocks;
            Set<BlockPos> worldBlocks;
            if (!isDemolition) {
                renderBlocks = newBlockSetForExpectedSize(Math.min(allBlocks.size(), sequenceEntries.size()));
                worldBlocks = newBlockSetForExpectedSize(Math.min(allBlocks.size(), sequenceEntries.size()));
                if (previewMode || isInstant) {
                    // ENTER + (preview/instant)：可见集是序列的前缀，按阈值直接取前缀避免扫描全量 entry
                    float normalizedThreshold = (currentTime - clipStart - delay) / sequenceSpan;
                    int cutoff = upperBoundNormalized(sequenceEntries, normalizedThreshold);
                    for (int i = 0; i < cutoff; i++) {
                        BlockSequenceEntry entry = sequenceEntries.get(i);
                        if (entry != null && entry.getPos() != null) {
                            renderBlocks.add(entry.getPos());
                            worldBlocks.add(entry.getPos());
                        }
                    }
                } else {
                    // ENTER + 播放：render 是 start 前缀；world 是 end 前缀（start+animDuration）
                    float normalizedThresholdRender = (currentTime - clipStart - delay) / sequenceSpan;
                    float normalizedThresholdWorld = (currentTime - clipStart - delay - animDuration) / sequenceSpan;
                    int cutoffRender = upperBoundNormalized(sequenceEntries, normalizedThresholdRender);
                    int cutoffWorld = upperBoundNormalized(sequenceEntries, normalizedThresholdWorld);
                    for (int i = 0; i < cutoffRender; i++) {
                        BlockSequenceEntry entry = sequenceEntries.get(i);
                        if (entry != null && entry.getPos() != null) {
                            renderBlocks.add(entry.getPos());
                        }
                    }
                    for (int i = 0; i < cutoffWorld; i++) {
                        BlockSequenceEntry entry = sequenceEntries.get(i);
                        if (entry != null && entry.getPos() != null) {
                            worldBlocks.add(entry.getPos());
                        }
                    }
                }
            } else {
                renderBlocks = newBlockSet(allBlocks);
                worldBlocks = newBlockSet(allBlocks);

                if (previewMode || isInstant) {
                    // ERASE + (preview/instant)：移除序列前缀即可
                    float normalizedThreshold = (currentTime - clipStart - delay) / sequenceSpan;
                    int cutoff = upperBoundNormalized(sequenceEntries, normalizedThreshold);
                    for (int i = 0; i < cutoff; i++) {
                        BlockSequenceEntry entry = sequenceEntries.get(i);
                        if (entry != null && entry.getPos() != null) {
                            renderBlocks.remove(entry.getPos());
                            worldBlocks.remove(entry.getPos());
                        }
                    }
                } else {
                    // 非 INSTANT 模式：逐步清除（带退出动画窗口）
                    // ✅ 与容器片段对称：在动画窗口内时，方块只在 renderBlocks 中（用于 overlay），不在 worldBlocks
                    // 中（避免与真实方块叠加导致黑色）
                    // 以 normalizedTime 计算 end 阈值的前缀；其余 entry 都处于“动画开始前”，无需扫描
                    float base = currentTime - clipStart - delay - (fullWindow ? animDuration : 0.0f);
                    float normalizedThresholdEnd = base / sequenceSpan;
                    int cutoffEnd = upperBoundNormalized(sequenceEntries, normalizedThresholdEnd);
                    for (int i = 0; i < cutoffEnd; i++) {
                        BlockSequenceEntry entry = sequenceEntries.get(i);
                        if (entry == null || entry.getPos() == null) {
                            continue;
                        }
                        float normalizedTime = entry.getNormalizedTime();
                        float blockEndTime = clipStart + delay + (fullWindow ? animDuration : 0.0f)
                                + normalizedTime * sequenceSpan;
                        float blockStartTime = blockEndTime - animDuration;

                        if (currentTime >= blockEndTime) {
                            renderBlocks.remove(entry.getPos());
                            worldBlocks.remove(entry.getPos());
                        } else if (currentTime >= blockStartTime) {
                            worldBlocks.remove(entry.getPos());
                        }
                    }
                }
            }
            return new VisibleBlocksResult(worldBlocks, renderBlocks, sequenceEntries);
        }

        // 片段右侧
        if (currentTime > clipEnd) {
            if (!isDemolition) {
                // 普通容器：全量显示
                Set<BlockPos> all = newBlockSet(allBlocks);
                return new VisibleBlocksResult(all, all, sequenceEntries);
            } else {
                // 拆除容器：全清除 => 保持空集合
                return new VisibleBlocksResult(Collections.emptySet(), Collections.emptySet(), sequenceEntries);
            }
        }

        return new VisibleBlocksResult(Collections.emptySet(), Collections.emptySet(), sequenceEntries);
    }

    private static Set<BlockPos> newBlockSet(Collection<BlockPos> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return Collections.emptySet();
        }
        Set<BlockPos> set = newBlockSetForExpectedSize(blocks.size());
        set.addAll(blocks);
        return set;
    }

    private static Set<BlockPos> newBlockSetForExpectedSize(int expectedSize) {
        int capacity = Math.max(16, (int) (expectedSize / 0.75f) + 1);
        return new HashSet<>(capacity);
    }

    /**
     * 🔥 为容器注册所有需要渲染的方块（RenderEntry 2.0）
     *
     * <p>
     * 核心逻辑：
     * <ol>
     * <li>遍历当前片段的所有 BlockSequenceEntry</li>
     * <li>为每个可见的 block 生成 RenderEntry 2.0（包含 clip、seqEntry 和 snapshot）</li>
     * <li>使用时间线时间采样动画 snapshot（不是世界时间）</li>
     * <li>将 snapshot 作为 overrideSnapshot 传入 RenderEntry</li>
     * <li>加入 BlockVisualStateManager</li>
     * </ol>
     *
     * <p>
     * 关键修复：snapshot 在此处使用时间线时间（currentTime）生成，
     * 并作为 overrideSnapshot 传入 RenderEntry，避免渲染时用世界时间重采样导致动画错误。
     */
    private void registerWholeContainerEntries(BlockVisualStateManager manager, Container container,
            TimelineClip clip, ClipAnimationConfig config, float time, Set<BlockPos> visibleBlocks,
            float opacity) {
        if (clip == null) return;
        boolean exit = container.getType() == ContainerType.DEMOLITION;
        boolean enabled = clip.getParentTrack() == null
                || (!clip.getParentTrack().isLocked() && clip.getParentTrack().isVisible());
        // Retain the intact structure before demolition starts and while its track is disabled.
        if (exit && (!enabled || time < clip.getStartTime())) opacity = 1.0f;
        var frame = com.director.core.animation.WholeContainerAnimation.sample(
                config, clip.getStartTime(), clip.getEndTime(), enabled ? time : clip.getStartTime() - 1, exit);
        if (enabled && opacity > 0 && config.getWholeContainer().isDustEnabled()
                && wholeEffectCursor.crosses(com.director.core.animation.WholeContainerDust.eventTime(
                        config, clip.getStartTime(), clip.getEndTime(), exit))) {
            emitWholeContainerDust(container, config);
        }
        var pivot = com.director.core.animation.WholeContainerAnimation.pivot(
                container.getBoundingBox(), config.getWholeContainer());
        // Rebuild overrides even while scrubbing: the original world structure must stay hidden.
        manager.setBlocksState(getCachedContainerBlocks(container), BlockVisualStateManager.BlockVisualState.hidden());
        float end = com.director.core.animation.WholeContainerAnimation.window(
                config, clip.getStartTime(), clip.getEndTime()).end();
        boolean handoff = enabled && !exit && opacity >= 0.999f && !frame.animating() && time >= end
                && time < end + com.director.core.animation.AnimationConstants.HANDOFF_POST_WINDOW;
        for (BlockPos pos : visibleBlocks) {
            BlockState state = resolveBlockStateForRender(container, pos);
            if (state == null) continue;
            var visual = opacity < 0.999f
                    ? BlockVisualStateManager.BlockVisualState.ghost(opacity, true, true)
                    : BlockVisualStateManager.BlockVisualState.normal();
            boolean worldReady = MinecraftClient.getInstance().world != null
                    && MinecraftClient.getInstance().world.getBlockState(pos).equals(state);
            boolean overlay = frame.animating() || handoff || !worldReady || opacity < 0.999f;
            if (!overlay) {
                manager.setBlockState(pos, visual);
                continue;
            }
            var snapshot = new BlockAnimationSnapshot(pos, state);
            snapshot.markFullyVisible();
            snapshot.setRawProgress(frame.progress());
            snapshot.setTransform(frame.localTransform(pos, pivot));
            snapshot.setAlpha(frame.alpha());
            if (handoff && worldReady) {
                // Preserve the assembly's shape during the world-mesh handoff too.
                var correction = new com.director.core.animation.WholeContainerAnimation.Frame(
                        true, true, true, 1,
                        com.director.core.animation.AnimationConstants.HANDOFF_ENTER_SCALE, 0, 0, 0);
                snapshot.setTransform(correction.localTransform(pos, pivot));
                visual = BlockVisualStateManager.BlockVisualState.ghost(opacity, true, false);
            } else if (overlay) {
                visual = visual.withSuppressWorldRendering(true);
            }
            manager.setBlockState(pos, visual);
            manager.addRenderEntry(new BlockVisualStateManager.RenderEntry(pos, state, visual, clip,
                    new BlockSequenceEntry(pos, 0, 0, 0), snapshot));
        }
    }

    private void emitWholeContainerDust(Container container, ClipAnimationConfig config) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.particleManager == null) return;
        if (config.getWholeContainer().getDustPreset()
                == com.director.core.animation.WholeContainerAnimationConfig.DustPreset.SHOCKWAVE) {
            com.director.core.animation.WholeContainerImpactRingPlayer.play(client,
                    getCachedContainerBlocks(container), config.getWholeContainer(), container.getId().hashCode());
            return;
        }
        var particles = com.director.core.animation.WholeContainerDust.sample(
                getCachedContainerBlocks(container), config.getWholeContainer(), container.getId().hashCode());
        for (var particle : particles) {
            BlockState state = resolveBlockStateForRender(container, particle.source());
            net.minecraft.particle.ParticleEffect effect = state != null && !state.isAir()
                    ? new net.minecraft.particle.BlockStateParticleEffect(net.minecraft.particle.ParticleTypes.BLOCK, state)
                    : net.minecraft.particle.ParticleTypes.POOF;
            // Visible smoke remains readable even with dark block textures.
            client.particleManager.addParticle(net.minecraft.particle.ParticleTypes.POOF,
                    particle.x(), particle.y(), particle.z(), particle.vx(), particle.vy(), particle.vz());
            if (config.getWholeContainer().getDustPreset()
                    != com.director.core.animation.WholeContainerAnimationConfig.DustPreset.IMPACT_DUST) continue;
            client.particleManager.addParticle(effect, particle.x(), particle.y(), particle.z(),
                    particle.vx(), particle.vy(), particle.vz());
        }
    }

    private void registerRenderEntriesForContainer(
            BlockVisualStateManager visualStateManager,
            Container container,
            TimelineClip clip,
            ClipAnimationConfig config,
            List<BlockSequenceEntry> sequenceEntries,
            float currentTime,
            Set<BlockPos> visibleBlocks,
            boolean previewMode) {
        if (previewMode) {
            // 预览/拖动：结构由真实世界方块呈现，不叠加 ENTER/EXIT overlay 动画
            return;
        }
        if (visualStateManager == null || container == null || clip == null || config == null) {
            return;
        }
        if (visibleBlocks == null || visibleBlocks.isEmpty()) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace(
                        "[TimelineClipBlockController] registerRenderEntries: visibleBlocks is empty for container {}",
                        container.getName());
            }
            return;
        }
        if (sequenceEntries == null || sequenceEntries.isEmpty()) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace(
                        "[TimelineClipBlockController] registerRenderEntries: sequenceEntries is empty for container {}",
                        container.getName());
            }
            return;
        }

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(
                    "[TimelineClipBlockController] registerRenderEntries: container={} visibleBlocks={} entries={}",
                    container.getName(), visibleBlocks.size(), sequenceEntries.size());
        }

        float clipStart = clip.getStartTime();
        float clipLength = clip.getLength();
        float clipEnd = clipStart + clipLength;

        // ===== 预计算单块时间窗口参数（避免每个 entry 重复走 BlockAnimationEngine 的时间窗口逻辑） =====
        ClipAnimationConfig.AnimationPhase phase = config.getPhase();
        float clipDuration = Math.max(0.0001f, clipLength);
        float animDuration = Math.max(0.0001f, Math.min(config.getDuration(), clipDuration));
        float delay = Math.max(0.0f, config.getDelay());
        try {
            var seq = config.getSequenceParameters();
            if (seq != null) {
                delay += Math.max(0.0f, seq.getGlobalDelay());
            }
        } catch (Exception ignored) {
        }
        float baseSpan = clipDuration - animDuration - delay;
        boolean fullWindow = baseSpan > 0.0001f;
        float sequenceSpan = fullWindow ? baseSpan : Math.max(0.0001f, clipDuration - delay);

        // 🔥 步骤 1：遍历当前片段的所有 BlockSequenceEntry
        for (BlockSequenceEntry entry : sequenceEntries) {
            if (entry == null) {
                continue;
            }
            BlockPos pos = entry.getPos();
            if (pos == null || !visibleBlocks.contains(pos)) {
                continue;
            }

            // 🔥 步骤 2：获取视觉状态（可能已经被遮挡逻辑修改过）
            BlockVisualStateManager.BlockVisualState visualState = visualStateManager.getBlockState(pos);
            if (visualState.isHidden()) {
                continue;
            }

            // ===== 性能关键：只有在“当前块处于动画窗口内”或“视觉状态本身需要 overlay（遮挡/透明）”时才 sample =====
            float seqNormalizedTime = net.minecraft.util.math.MathHelper.clamp(entry.getNormalizedTime(), 0.0f, 1.0f);
            float blockStartTime;
            float blockEndTime;
            if (phase == ClipAnimationConfig.AnimationPhase.EXIT) {
                blockEndTime = clipStart + delay + (fullWindow ? animDuration : 0.0f)
                        + seqNormalizedTime * sequenceSpan;
                blockStartTime = blockEndTime - animDuration;
            } else {
                blockStartTime = clipStart + delay + seqNormalizedTime * sequenceSpan;
                blockEndTime = blockStartTime + animDuration;
            }
            if (blockStartTime < clipStart)
                blockStartTime = clipStart;
            if (blockEndTime > clipEnd)
                blockEndTime = clipEnd;

            // INSTANT 模式：不需要动画窗口，直接按序列出现
            boolean isInstant = config.getMode() == com.director.core.container.AnimationMode.INSTANT;
            boolean inAnimWindow = !isInstant && currentTime >= blockStartTime && currentTime < blockEndTime;

            // ENTER：未到开始时间，理论上不应在 visibleBlocks 中；保险跳过
            if (phase != ClipAnimationConfig.AnimationPhase.EXIT && currentTime < blockStartTime) {
                continue;
            }
            // EXIT：超过 endTime 且非瞬时，视为已退出
            if (phase == ClipAnimationConfig.AnimationPhase.EXIT
                    && currentTime >= blockEndTime
                    && config.getMode() != com.director.core.container.AnimationMode.INSTANT) {
                continue;
            }

            BlockState blockState;
            BlockAnimationSnapshot snapshot;

            // ✅ 修复：INSTANT 模式或需要特效时，即使不在动画窗口内也应该调用 sample() 来触发特效
            boolean needsEffects = !config.getEffects().isEmpty();
            boolean shouldSampleForEffects = isInstant && needsEffects && currentTime >= blockStartTime;

            // ===== 使用 HandoffWindowManager 判断交接窗口 =====
            boolean inHandoffWindow = com.director.core.animation.HandoffWindowManager.isInHandoffWindow(
                    currentTime, blockStartTime, blockEndTime, phase);

            if (!inAnimWindow && !shouldSampleForEffects) {
                // 静态块：如果视觉状态是 NORMAL 且不抑制世界渲染，完全不需要 overlay / snapshot
                // ✅ 关键修复：但如果处于交接窗口内，必须继续渲染 overlay 以遮盖区块重建延迟
                if (visualState.isNormal() && !visualState.shouldSuppressWorldRendering() && !inHandoffWindow) {
                    continue;
                }

                // 🔥 步骤 3：静态但需要 overlay，才解析方块状态（避免每帧对海量静态块做 world.getBlockState）
                blockState = resolveBlockStateForRender(container, pos);
                if (blockState == null) {
                    continue;
                }

                // ✅ 修复：即使不在动画窗口内，如果需要特效，也应该调用 sample() 来触发特效
                // 对于静态方块（动画已结束），设置 rawProgress = 1.0，允许后段特效（如 DUST_BURST）触发
                if (needsEffects && currentTime >= blockStartTime) {
                    // 调用 sample() 来触发特效，即使方块不在动画窗口内
                    long randomSeed = container.getId().getLeastSignificantBits() ^ pos.asLong();
                    snapshot = BlockAnimationEngine.sample(
                            pos,
                            blockState,
                            config,
                            entry,
                            clipStart,
                            clipEnd,
                            currentTime,
                            randomSeed);
                } else {
                    // 视觉状态需要 overlay（遮挡/透明）：构造轻量 snapshot（不走 sample）
                    snapshot = new BlockAnimationSnapshot(pos, blockState);
                    snapshot.markFullyVisible();
                    snapshot.setSequenceNormalizedTime(seqNormalizedTime);
                    snapshot.setTransform(null); // null = identity
                }
            } else {
                // 🔥 步骤 3：动画窗口内或需要特效时，解析方块状态 + 采样
                blockState = resolveBlockStateForRender(container, pos);
                if (blockState == null) {
                    continue;
                }
                // 动画窗口内或需要特效：进行真实采样（会触发特效）
                long randomSeed = container.getId().getLeastSignificantBits() ^ pos.asLong();
                snapshot = BlockAnimationEngine.sample(
                        pos,
                        blockState,
                        config,
                        entry,
                        clipStart,
                        clipEnd,
                        currentTime, // ✅ 使用时间线时间，不是世界时间
                        randomSeed);
            }

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[TimelineClipBlockController] temp snapshot: pos={} visible={} alpha={} hasTransform={}",
                        pos, snapshot.isVisible(),
                        snapshot.getAlpha(),
                        snapshot.getTransform() != null);
            }

            // ✅ EXIT 阶段在动画窗口内时，即使 alpha 为 0 也应该注册 renderEntry（显示动画过程）
            // 其他情况：如果不可见或 alpha <= 0，跳过
            if (!snapshot.isVisible()) {
                continue;
            }
            if (snapshot.getAlpha() <= 0.0f && !(phase == ClipAnimationConfig.AnimationPhase.EXIT && inAnimWindow)) {
                continue;
            }

            // 🔥 步骤 5：检查是否需要覆盖层渲染
            // 引入交接窗口（Handoff Window），解决区块重建延迟导致的黑影/闪烁/Z-fighting问题
            // 策略：在动画边界处强制渲染一个"微放大且不透明"的 Overlay，以覆盖底下的真实方块
            // （PRE_POST_WINDOW / MASK_WINDOW / inEnterHandoff / inExitHandoff 已在前面提前计算）

            // 需要 overlay 的情况：
            // - 视觉状态不是 NORMAL（有透明度或遮挡）
            // - 动画 alpha < 0.999（有透明度变化）
            // - 有非单位变换（有动画变换）
            // - EXIT 阶段在动画窗口内
            // - ✅ 处于交接窗口内（强制 overlay 接管）
            boolean needsOverlay = !visualState.isNormal()
                    || snapshot.getAlpha() < 0.999f
                    || !isIdentityTransform(snapshot)
                    || (phase == ClipAnimationConfig.AnimationPhase.EXIT && inAnimWindow)
                    || inHandoffWindow;

            if (!needsOverlay) {
                continue;
            }

            // 如果处于交接窗口，强制修正 snapshot 状态，确保平滑过渡且遮挡底下的瑕疵
            // 如果处于交接窗口，应用交接窗口修正
            if (inHandoffWindow) {
                // 使用 HandoffWindowManager 统一处理交接窗口修正
                com.director.core.animation.HandoffWindowManager.applyHandoffCorrection(snapshot, phase);

                // 设置交接窗口专用的视觉状态
                visualState = com.director.core.animation.HandoffWindowManager.createHandoffVisualState();
                visualStateManager.setBlockState(pos, visualState);
            }

            // 🔥 步骤 6：确保视觉状态抑制世界渲染（除非在交接窗口期间）
            // 注意：在交接窗口期间，已经设置 suppressWorldRendering = false
            // 这里只对非交接窗口的动画方块启用抑制
            if (!inHandoffWindow && !visualState.shouldSuppressWorldRendering()) {
                visualState = visualState.withSuppressWorldRendering(true);
                visualStateManager.setBlockState(pos, visualState);
            }

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[TimelineClipBlockController] Adding render entry: pos={} alpha={}",
                        pos, snapshot.getAlpha());
            }

            // 🔥 步骤 7：创建 RenderEntry 2.0 并加入 BlockVisualStateManager
            // 关键：传入使用时间线时间采样好的 snapshot，避免渲染时用世界时间重采样
            visualStateManager.addRenderEntry(
                    new BlockVisualStateManager.RenderEntry(
                            pos,
                            blockState,
                            visualState,
                            clip, // 🔥 timeline clip（动画需要）
                            entry, // 🔥 sequence entry（决定 normalizedTime）
                            snapshot // ✅ 传入使用时间线时间采样的 snapshot
                    ));
        }

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("[TimelineClipBlockController] registerRenderEntries completed: {} entries added",
                    sequenceEntries.stream().filter(e -> e != null && visibleBlocks.contains(e.getPos())).count());
        }
    }

    private void cleanupContainersNoLongerControlled(Set<UUID> activeContainerIds,
                                                     com.director.core.container.visibility.ContainerVisibilityService visibilityService,
                                                     BlockVisualStateManager visualStateManager,
                                                     Set<BlockPos> rerender) {
        if (lastFrameWorldBlocks.isEmpty()) {
            return;
        }

        Set<UUID> removed = new HashSet<>(lastFrameWorldBlocks.keySet());
        if (activeContainerIds != null && !activeContainerIds.isEmpty()) {
            removed.removeAll(activeContainerIds);
        }
        if (removed.isEmpty()) {
            return;
        }

        ContainerManager manager = DirectorServices.get().containers();
        for (UUID containerId : removed) {
            Container container = manager != null ? manager.getContainer(containerId) : null;
            if (container == null) {
                continue;
            }

            List<BlockPos> allBlocks = getCachedContainerBlocks(container);
            if (rerender != null) {
                rerender.addAll(allBlocks);
            }

            if (visualStateManager != null) {
                visualStateManager.setBlocksState(allBlocks,
                        container.isHidden()
                                ? BlockVisualStateManager.BlockVisualState.hidden()
                                : BlockVisualStateManager.BlockVisualState.normal());
            }

            if (visibilityService == null) {
                continue;
            }

            try {
                if (container.isHidden()) {
                    visibilityService.showContainerBlocksForTimeline(container, Collections.emptySet());
                } else if (visibilityService.hasSnapshot(container)) {
                    visibilityService.showContainer(container);
                }
            } catch (Exception e) {
                LOGGER.warn("[TimelineClipBlockController] 清理脱离时间线控制的容器失败: {} - {}",
                        container.getName(), e.getMessage(), e);
            }
        }
    }

    /**
     * 检查 snapshot 的变换是否是单位变换（无动画）
     *
     * <p>
     * 通过逐元素比较矩阵与单位矩阵，允许一定的浮点精度误差。
     * 如果变换矩阵接近单位矩阵，说明方块处于静止状态（INSTANT 模式或动画未开始），
     * 可以跳过 overlay 渲染，直接使用世界渲染。
     *
     * @return true 如果是单位变换（无动画），false 如果有动画变换
     */
    private boolean isIdentityTransform(BlockAnimationSnapshot snapshot) {
        // 约定：transform == null 表示单位矩阵（无局部变换）
        return snapshot == null || snapshot.getTransform() == null;
    }

    private BlockState resolveBlockStateForRender(Container container, BlockPos pos) {
        if (container != null && container.getBlockStore() != null) {
            var entry = container.getBlockStore().get(pos);
            if (entry != null && entry.state != null) {
                return entry.state;
            }
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.world != null) {
            return client.world.getBlockState(pos);
        }
        return null;
    }

    private record VisibleBlocksResult(Set<BlockPos> worldBlocks, Set<BlockPos> renderBlocks,
                                       List<BlockSequenceEntry> sequenceEntries) {
    }

    private void applyOcclusionOverrides(float currentTime,
                                         Map<UUID, Set<BlockPos>> currentFrameVisibleBlocks,
                                         Map<UUID, Float> currentFrameOpacity,
                                         Set<UUID> occludedContainers,
                                         Map<UUID, TimelineClip> containerToClipMap) {

        List<TimelineClip> activeFadeClips = findActiveAutoFadeClips(currentTime);
        if (activeFadeClips.isEmpty()) {
            return;
        }

        for (TimelineClip activeClip : activeFadeClips) {
            UUID activeContainerId = activeClip.getBoundContainerId();
            if (activeContainerId == null) {
                continue;
            }
            Container activeContainer = DirectorServices.get().containers()
                    .getContainer(activeContainerId);
            if (activeContainer == null) {
                continue;
            }

            List<Container> candidates = collectPreviousContainers(activeClip, currentFrameVisibleBlocks);
            if (candidates.isEmpty()) {
                continue;
            }

            Map<UUID, Set<BlockPos>> occludingBlocks = occlusionResolver.resolveOccludingBlocks(activeContainer,
                    candidates);
            if (occludingBlocks.isEmpty()) {
                continue;
            }

            float fadeOpacity = activeClip.getAutoFadeOpacity();

            for (Map.Entry<UUID, Set<BlockPos>> entry : occludingBlocks.entrySet()) {
                UUID containerId = entry.getKey();
                Set<BlockPos> occludingPositions = entry.getValue();

                if (occludingPositions.isEmpty()) {
                    continue;
                }

                TimelineClip occludedClip = containerToClipMap != null ? containerToClipMap.get(containerId) : null;
                boolean occlusionTransparencyEnabled = occludedClip == null
                        || (occludedClip.getAnimationConfig() != null
                        && occludedClip.getAnimationConfig().isOcclusionTransparencyEnabled());
                if (!occlusionTransparencyEnabled) {
                    continue;
                }

                // 更新透明度映射，影响后续的视觉状态设置
                Set<BlockPos> currentVisible = currentFrameVisibleBlocks.get(containerId);
                if (currentVisible != null && !currentVisible.isEmpty()) {
                    // 更新透明度
                    currentFrameOpacity.put(containerId, fadeOpacity);

                    // 🔹 标记该容器被遮挡，需要隐藏世界方块
                    occludedContainers.add(containerId);

                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("[Occlusion] clip={} 强制淡化容器 {} (透明度 {}), 标记为需要隐藏世界方块",
                                activeClip.getLabel(), containerId, fadeOpacity);
                    }
                }
            }
        }
    }

    private List<TimelineClip> findActiveAutoFadeClips(float currentTime) {
        TimelineModel model = resolveActiveModel();
        if (model == null) {
            return List.of();
        }
        List<TimelineClip> active = new ArrayList<>();
        for (TimelineTrack track : model.getAllTracks()) {
            if (track == null || track.isLocked() || !track.isVisible()) {
                continue;
            }
            if (!isBlockTrackType(track.getType())) {
                continue;
            }
            for (TimelineClip clip : track.getClips()) {
                if (clip == null || !clip.isAutoFadePreviousClips()) {
                    continue;
                }
                if (currentTime >= clip.getStartTime() && currentTime <= clip.getEndTime()) {
                    active.add(clip);
                }
            }
        }
        return active;
    }

    private List<Container> collectPreviousContainers(TimelineClip activeClip,
                                                      Map<UUID, Set<BlockPos>> currentFrameVisibleBlocks) {
        TimelineModel model = resolveActiveModel();
        if (model == null) {
            return List.of();
        }
        List<Container> result = new ArrayList<>();
        float activeStart = activeClip.getStartTime();

        for (TimelineTrack track : model.getAllTracks()) {
            if (track == null || track.isLocked() || !track.isVisible()) {
                continue;
            }
            if (!isBlockTrackType(track.getType())) {
                continue;
            }
            for (TimelineClip clip : track.getClips()) {
                if (clip == null || clip == activeClip) {
                    continue;
                }
                if (clip.getBoundContainerId() == null) {
                    continue;
                }
                // 仅处理比当前片段更早的片段
                if (clip.getStartTime() >= activeStart) {
                    continue;
                }
                Container container = DirectorServices.get().containers()
                        .getContainer(clip.getBoundContainerId());
                if (container == null) {
                    continue;
                }
                // 只有当前帧仍然可见的容器才需要处理遮挡
                // 获取当前帧应该显示的方块（从当前帧的映射中获取）
                Set<BlockPos> visible = currentFrameVisibleBlocks.get(container.getId());
                if (visible != null && visible.isEmpty()) {
                    continue;
                }
                result.add(container);
            }
        }
        return result;
    }

    /**
     * 确保容器绑定了世界
     *
     * <p>
     * 如果容器没有绑定世界，尝试从客户端获取当前世界并绑定
     *
     * @param container 容器对象
     */
    private void ensureContainerWorldBinding(Container container) {
        if (container == null) {
            return;
        }

        if (container.getLevelKey() != null) {
            return; // 已经绑定世界
        }

        // 尝试从客户端获取当前世界
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.world != null) {
                RegistryKey<World> worldKey = client.world.getRegistryKey();
                container.setLevelKey(worldKey);

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[TimelineClipBlockController] 容器 {} 已绑定世界 {}",
                            container.getName(), worldKey.getValue());
                }
            } else {
                LOGGER.warn("[TimelineClipBlockController] 无法获取客户端世界，容器 {} 无法绑定世界",
                        container.getName());
            }
        } catch (Exception e) {
            LOGGER.error("[TimelineClipBlockController] 绑定容器世界失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 确保容器有快照
     *
     * <p>
     * 如果容器在ContainerVisibilityService 中没有快照，但有方块数据，
     * 需要先创建快照。
     *
     * <p>
     * 策略：
     * <ol>
     * <li>优先使用容器的 blockStore（如果已有数据），因为它是创建容器时捕获的原始状态</li>
     * <li>如果容器的 blockStore 为空，调用 hideContainer 从世界中捕获（但此时方块可能已经是 AIR）</li>
     * </ol>
     *
     * @param container         容器对象
     * @param visibilityService 可见性服务
     */
    private void ensureContainerSnapshot(Container container,
                                         com.director.core.container.visibility.ContainerVisibilityService visibilityService,
                                         TrackType trackType) {
        if (container == null || visibilityService == null) {
            return;
        }

        // 检查容器是否已经有快照
        if (visibilityService.hasSnapshot(container)) {
            return; // 已经有快照
        }

        // 如果容器没有快照，但有方块，需要创建快照
        if (container.getBlockCount() > 0) {
            // 检查容器是否绑定了世界
            if (container.getLevelKey() == null) {
                LOGGER.warn("[TimelineClipBlockController] 容器 {} 未绑定世界，无法创建快照",
                        container.getName());
                return;
            }

            // ◆ 策略1：优先使用容器的 blockStore（如果已有数据）
            // 容器的 blockStore 是在创建容器时捕获的原始方块状态，即使世界中的方块已经是 AIR，也能恢复
            if (visibilityService.createSnapshotFromBlockStore(container)) {
                // 成功从 blockStore 创建快照
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[TimelineClipBlockController] 容器 {} 已从 blockStore 创建快照",
                            container.getName());
                }
            } else {
                visibilityService.prepareContainerForTimeline(container);

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[TimelineClipBlockController] 容器 {} 已通过时间线准备流程创建快照 (方块数: {})",
                            container.getName(), container.getBlockCount());
                }
            }

            // ✅ 轨道差异：
            // - 普通容器：容器应保持 HIDDEN（世界方块由 showContainerBlocks 控制出现）
            // - 拆除容器：容器不应被强制隐藏，否则会改变编辑预期
            boolean isDemolition = container.getType() == ContainerType.DEMOLITION;
            if (!isDemolition) {
                if (container.getState() != com.director.core.container.ContainerState.HIDDEN) {
                    container.setState(com.director.core.container.ContainerState.HIDDEN, false);
                }
            }
        }
    }

}
