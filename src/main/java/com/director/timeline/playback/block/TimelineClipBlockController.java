package com.director.timeline.playback.block;

import com.director.core.DirectorServices;
import com.director.core.animation.BlockAnimationEngine;
import com.director.core.animation.BlockAnimationSnapshot;
import com.director.core.animation.ClipAnimationConfig;
import com.director.core.animation.PerBlockAnimationTiming;
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
import com.director.timeline.playback.TimelineSamplingContext;
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
    private boolean worldHandoffPending;
    private boolean previewResyncPending;
    private long frameSequenceVisits;
    private long frameSettledChecks, frameSettledSkipped;
    private long lastHandledSectionRevision = -1;
    private final WholeContainerEffectCursor wholeEffectCursor = new WholeContainerEffectCursor();
    private TimelineModel observedModel;
    private final TimelineModel.ModelChangeListener modelChangeListener = changeType -> timelineRevision++;

    // =====================================================================
    // Perf caches (播放性能关键)
    // =====================================================================

    /**
     * 容器方块列表缓存：避免每帧调用 {@code container.getBlocks()} 造成全量复制与分配。
     * 仅坐标归属变化时重建，改名、颜色等属性不影响坐标缓存。
     */
    private static final class ContainerBlocksCache {
        long geometryRevision = -1;
        boolean initialized;
        RegistryKey<World> levelKey;
        List<BlockPos> blocks = List.of();
        Set<BlockPos> all = Set.of();
        final SettledBlockSections settled = new SettledBlockSections(
                com.director.core.container.visibility.ClientSectionInvalidation.getInstance());
    }

    /**
     * 片段序列缓存：避免在播放时每帧重新 generate + sort。
     */
    private static final class ClipSequenceCache {
        Container container;
        long geometryRevision;
        int containerBlockCount;
        int sequenceParamsHash;
        float clipDuration;
        List<BlockSequenceEntry> ordered = List.of();
        List<BlockSequenceEntry> entries = List.of();
        SequenceVisibility visibility;
    }

    /**
     * ⭐ 性能关键：可见方块结果缓存
     * 避免每帧重新构建包含数万元素的 HashSet
     */
    private static final class VisibleBlocksCache {
        boolean lastTrackEnabled;
        boolean lastPreviewMode;
        Container container;
        long geometryRevision;
        ContainerType containerType;
        int sequenceParamsHash;
        int animationHash;
        int started = -1;
        int finished = -1;
        int staticBucket = -2;
        VisibleBlocksResult result;
    }

    private final Map<Container, ContainerBlocksCache> blocksCacheByContainer = new IdentityHashMap<>();
    private final Map<TimelineClip, ClipSequenceCache> sequenceCacheByClip = new IdentityHashMap<>();
    // ⭐ 新增：可见方块结果缓存
    private final Map<TimelineClip, VisibleBlocksCache> visibleBlocksCacheByClip = new IdentityHashMap<>();

    private List<BlockPos> getCachedContainerBlocks(Container container) {
        if (container == null) {
            return List.of();
        }
        long mod = container.getGeometryRevision();
        ContainerBlocksCache cache = blocksCacheByContainer.get(container);
        if (cache == null) {
            cache = new ContainerBlocksCache();
            blocksCacheByContainer.put(container, cache);
        }
        if (cache.blocks == null
                || cache.geometryRevision != mod
                || !Objects.equals(cache.levelKey, container.getLevelKey())
                || (cache.blocks.isEmpty() && container.getBlockCount() > 0)) {
            cache.geometryRevision = mod;
            cache.initialized = true;
            cache.levelKey = container.getLevelKey();
            cache.blocks = new ArrayList<>(container.getBlocksView());
            cache.all = BlockPositionSets.immutableCopy(cache.blocks);
        }
        return cache.blocks != null ? cache.blocks : List.of();
    }

    private Set<BlockPos> getCachedAllBlocks(Container container) {
        getCachedContainerBlocks(container);
        return container == null ? Set.of() : blocksCacheByContainer.get(container).all;
    }

    /** Reconcile client ownership against the previous geometry, not only the new block set. */
    private void reconcileContainerGeometry(Container container) {
        ContainerBlocksCache cache = blocksCacheByContainer.get(container);
        if (cache == null || !cache.initialized) return;
        long revision = container.getGeometryRevision();
        RegistryKey<World> level = container.getLevelKey();
        if (cache.geometryRevision == revision && Objects.equals(cache.levelKey, level)) return;

        Set<BlockPos> oldAll = cache.all;
        var manager = BlockVisualStateManager.getInstance();
        if (!Objects.equals(cache.levelKey, level)) {
            manager.clearOverridesFor(container);
        } else {
            getCachedContainerBlocks(container);
            Set<BlockPos> removed = new HashSet<>(oldAll);
            removed.removeAll(cache.all);
            if (!removed.isEmpty() && level != null) manager.releaseContainerPositions(container.getId(), level, removed);
            if (!removed.isEmpty() && level != null && DirectorServices.isInitialized()) {
                var visibility = DirectorServices.get().visibilityOrNull();
                if (visibility != null) visibility.restoreContainerBlocks(container, removed);
            }
        }
        stableFramesByContainer.remove(container.getId());
        wholeRenderCacheByContainer.remove(container.getId());
        perBlockRenderCacheByContainer.remove(container.getId());
        lastVisualParamsByContainerId.remove(container.getId());
        if (TimelineSamplingContext.current().isPreview()) resetPerBlockEffects();
    }

    private static int hashSequenceParameters(com.director.core.animation.sequence.SequenceParameters p) {
        if (p == null) {
            return 0;
        }
        return p.fingerprint();
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

        long containerMod = container.getGeometryRevision();
        int blockCount = container.getBlockCount();
        var params = (config != null) ? config.getSequenceParameters() : null;
        int paramsHash = hashSequenceParameters(params);

        ClipSequenceCache cache = sequenceCacheByClip.get(clip);
        boolean sameOrder = cache != null
                && cache.container == container
                && cache.geometryRevision == containerMod
                && cache.containerBlockCount == blockCount
                && cache.sequenceParamsHash == paramsHash;
        if (sameOrder && Math.abs(cache.clipDuration - clipDuration) < 1e-4f) {
            return cache.entries;
        }

        ClipSequenceCache next = (cache != null) ? cache : new ClipSequenceCache();
        if (!sameOrder) {
            next.ordered = BlockSequenceGenerator.generate(getCachedContainerBlocks(container), container, params);
        }
        // Spatial scoring/BFS is independent of clip length and individual action timing.
        // Scheduling retains its own sorted result and never mutates the cached spatial order.
        var generated = com.director.core.animation.sequence.PerBlockSchedule.create(
                next.ordered, params, clipDuration).entries();
        next.container = container;
        next.geometryRevision = containerMod;
        next.containerBlockCount = blockCount;
        next.sequenceParamsHash = paramsHash;
        next.clipDuration = clipDuration;
        next.entries = (generated != null) ? generated : List.of();
        next.visibility = new SequenceVisibility(next.entries, getCachedAllBlocks(container));
        sequenceCacheByClip.put(clip, next);

        return next.entries;
    }

    private static int upperBoundNormalized(List<BlockSequenceEntry> entries, float normalizedThreshold) {
        if (entries == null || entries.isEmpty()) {
            return 0;
        }
        if (normalizedThreshold < 0.0f) {
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

    private static int[] sequenceCutoffs(List<BlockSequenceEntry> entries, PerBlockAnimationTiming.Layout timing,
                                         float currentTime, float clipStart, boolean instant) {
        float base = currentTime - clipStart - timing.delay();
        int started = upperBoundNormalized(entries, timing.threshold(base));
        int finished = instant ? started
                : upperBoundNormalized(entries, timing.threshold(base - timing.duration()));
        return new int[] {started, finished};
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
        Container container;
        long geometryRevision;
        RegistryKey<World> levelKey;
        int animationHash;

        LastVisualParams copy() {
            var copy = new LastVisualParams();
            copy.opacity = opacity;
            copy.occluded = occluded;
            copy.container = container;
            copy.geometryRevision = geometryRevision;
            copy.levelKey = levelKey;
            copy.animationHash = animationHash;
            return copy;
        }
    }

    private final Map<UUID, LastVisualParams> lastVisualParamsByContainerId = new HashMap<>();

    private record StableContainerFrame(VisibleBlocksResult visibility, float opacity,
                                        ClipAnimationConfig config, TimelineClip clip, int staticBucket) {}
    private record VisualWrite(BlockPos pos, BlockVisualStateManager.BlockVisualState state) {}
    private record WholeRenderCache(ClipAnimationConfig config, long geometryRevision, float opacity,
                                    Set<BlockPos> visible, com.director.core.animation.WholeContainerAnimation.Frame frame,
                                    net.minecraft.util.math.Vec3d pivot, long sectionRevision,
                                    RegistryKey<World> level, List<VisualWrite> writes,
                                    List<BlockVisualStateManager.RenderEntry> entries) {}
    private record PerBlockRenderCache(ClipAnimationConfig config, long geometryRevision, float opacity,
                                       Set<BlockPos> visible, int staticBucket, long sectionRevision,
                                       RegistryKey<World> level, List<VisualWrite> writes,
                                       List<BlockVisualStateManager.RenderEntry> entries) {}

    private final Map<UUID, StableContainerFrame> stableFramesByContainer = new HashMap<>();
    private final Map<TimelineClip, Integer> lastStaticBucketByClip = new IdentityHashMap<>();
    private final Map<UUID, WholeRenderCache> wholeRenderCacheByContainer = new HashMap<>();
    private final Map<UUID, PerBlockRenderCache> perBlockRenderCacheByContainer = new HashMap<>();

    private final class PreviewFrameState {
        final Map<UUID, Set<BlockPos>> visibleBlocks = new HashMap<>(lastFrameVisibleBlocks);
        final Map<UUID, Set<BlockPos>> worldBlocks = new HashMap<>(lastFrameWorldBlocks);
        final Map<UUID, LastVisualParams> visualParams = new HashMap<>();
        final Map<UUID, StableContainerFrame> stableFrames = new HashMap<>(stableFramesByContainer);
        final Map<TimelineClip, Integer> staticBuckets = new IdentityHashMap<>(lastStaticBucketByClip);
        final Map<UUID, WholeRenderCache> wholeRenderCache = new HashMap<>(wholeRenderCacheByContainer);
        final Map<UUID, PerBlockRenderCache> perBlockRenderCache = new HashMap<>(perBlockRenderCacheByContainer);
        final Set<UUID> retries = new HashSet<>(containersNeedingRetry);
        final boolean wasScrubbing = TimelineClipBlockController.this.wasScrubbing;
        final boolean timelineDrivingPaused = TimelineClipBlockController.this.timelineDrivingPaused;
        final boolean worldHandoffPending = TimelineClipBlockController.this.worldHandoffPending;
        final boolean lastObservedPlaying = TimelineClipBlockController.this.lastObservedPlaying;
        final long lastHandledRevision = TimelineClipBlockController.this.lastHandledRevision;
        final long lastHandledSectionRevision = TimelineClipBlockController.this.lastHandledSectionRevision;
        final float lastHandledPreviewTime = TimelineClipBlockController.this.lastHandledPreviewTime;
        final int cacheCleanupCounter = TimelineClipBlockController.this.cacheCleanupCounter;
        final long frameSequenceVisits = TimelineClipBlockController.this.frameSequenceVisits;
        final long frameSettledChecks = TimelineClipBlockController.this.frameSettledChecks;
        final long frameSettledSkipped = TimelineClipBlockController.this.frameSettledSkipped;

        PreviewFrameState() {
            lastVisualParamsByContainerId.forEach((id, params) -> visualParams.put(id, params.copy()));
        }

        void restore() {
            restoreMap(lastFrameVisibleBlocks, visibleBlocks);
            restoreMap(lastFrameWorldBlocks, worldBlocks);
            restoreMap(lastVisualParamsByContainerId, visualParams);
            restoreMap(stableFramesByContainer, stableFrames);
            restoreMap(lastStaticBucketByClip, staticBuckets);
            restoreMap(wholeRenderCacheByContainer, wholeRenderCache);
            restoreMap(perBlockRenderCacheByContainer, perBlockRenderCache);
            containersNeedingRetry.clear();
            containersNeedingRetry.addAll(retries);
            TimelineClipBlockController.this.wasScrubbing = wasScrubbing;
            TimelineClipBlockController.this.timelineDrivingPaused = timelineDrivingPaused;
            TimelineClipBlockController.this.worldHandoffPending = worldHandoffPending;
            TimelineClipBlockController.this.lastObservedPlaying = lastObservedPlaying;
            TimelineClipBlockController.this.lastHandledRevision = lastHandledRevision;
            TimelineClipBlockController.this.lastHandledSectionRevision = lastHandledSectionRevision;
            TimelineClipBlockController.this.lastHandledPreviewTime = lastHandledPreviewTime;
            TimelineClipBlockController.this.cacheCleanupCounter = cacheCleanupCounter;
            TimelineClipBlockController.this.frameSequenceVisits = frameSequenceVisits;
            TimelineClipBlockController.this.frameSettledChecks = frameSettledChecks;
            TimelineClipBlockController.this.frameSettledSkipped = frameSettledSkipped;
        }
    }

    private static <K, V> void restoreMap(Map<K, V> target, Map<K, V> saved) {
        target.clear();
        target.putAll(saved);
    }

    private final Map<UUID, Long> containerFailureCounts = new HashMap<>();
    private final Set<UUID> containersNeedingRetry = new HashSet<>();
    private long containerFailureCount;
    private long lastContainerFailureNanos;
    private String lastContainerFailureMessage = "";
    private boolean lastObservedPlaying;

    // ===== 性能采样（节流日志）=====
    private long lastPerfLogMs = 0L;
    private float lastPerBlockEffectTime = Float.NaN;
    private boolean perBlockEffectsPlaying;

    private void resetPerBlockEffects() {
        BlockAnimationEngine.resetEffects();
        perBlockEffectSampleTime.clear();
        lastPerBlockEffectTime = Float.NaN;
        perBlockEffectsPlaying = false;
    }

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
            // Owner indexes retain historical coordinates even after geometry shrink or registry removal.
            BlockVisualStateManager.getInstance().clearOverridesFor(container);
            blocksCacheByContainer.remove(container);
            sequenceCacheByClip.entrySet().removeIf(entry -> container.getId().equals(entry.getKey().getBoundContainerId()));
            visibleBlocksCacheByClip.entrySet().removeIf(entry -> container.getId().equals(entry.getKey().getBoundContainerId()));
            lastStaticBucketByClip.entrySet().removeIf(entry -> container.getId().equals(entry.getKey().getBoundContainerId()));
            perBlockEffectSampleTime.entrySet().removeIf(entry -> container.getId().equals(entry.getKey().getBoundContainerId()));
            containersNeedingRetry.remove(container.getId());
            stableFramesByContainer.remove(container.getId());
            wholeRenderCacheByContainer.remove(container.getId());
            perBlockRenderCacheByContainer.remove(container.getId());
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
        if (model == null) return false;
        if (previewResyncPending) return true;
        boolean playing = model.isPlaying();
        if (model.isScrubbing()) return true;
        if (!containersNeedingRetry.isEmpty()) return true;
        if (worldHandoffPending || TimelinePlacementBudget.isCatchUp()) return true;
        if (lastHandledSectionRevision != com.director.core.container.visibility.ClientSectionInvalidation.getInstance().revision())
            return true;
        if (timelineRevision != lastHandledRevision || playing != lastObservedPlaying) return true;

        float currentTime = model.getCurrentTime();
        if (playing) {
            // Stable playback buckets retain their render entries and do no set diff or sampling.
            // A bucket transition (including a loop/seek to another static phase) still gets one pass.
            for (TimelineTrack track : model.getAllTracks()) {
                if (track == null || !isBlockTrackType(track.getType())) continue;
                boolean enabled = !track.isLocked() && track.isVisible();
                for (TimelineClip clip : track.getClips()) {
                    if (clip == null || clip.getBoundContainerId() == null) continue;
                    Container container = DirectorServices.get().containers().getContainer(clip.getBoundContainerId());
                    if (container == null) return true;
                    int bucket = staticTimeBucket(clip, currentTime, enabled, container);
                    if (bucket < 0 || !Objects.equals(lastStaticBucketByClip.get(clip), bucket)) return true;
                }
            }
            return false;
        }

        boolean timeChanged = Float.isNaN(lastHandledPreviewTime)
                || Math.abs(currentTime - lastHandledPreviewTime) > 1e-4f;
        if (timeChanged) return true;
        // A paused timeline may still owe the visibility service one final pause call.
        return !timelineDrivingPaused;
    }

    /** -1 means dynamic; nonnegative values identify stable before/disabled/delayed/after phases. */
    static int staticTimeBucket(TimelineClip clip, float time, boolean enabled, Container container) {
        if (!enabled) return 0;
        float start = clip.getStartTime();
        float end = clip.getEndTime();
        if (time < start) return 1;
        ClipAnimationConfig config = clip.getAnimationConfigForEditing();
        if (config == null) config = ClipAnimationConfig.defaultConfig();
        float completion = com.director.core.animation.PerBlockAnimationTiming.effectiveAnimationEndTime(config, start, end);
        boolean fading = clip.isPostAnimationFadeEnabled()
                && clip.getPostAnimationFadeEnd() > clip.getPostAnimationFadeStart()
                && time >= completion + clip.getPostAnimationFadeStart()
                && time <= completion + clip.getPostAnimationFadeEnd();
        if (fading || (!clip.getVisibilityKeyframes().isEmpty() && time <= end)) return -1;
        if (config.isWholeContainer()) {
            var window = com.director.core.animation.WholeContainerAnimation.window(config, start, end);
            if (time < window.start()) return 2;
            if (time == window.start() && window.end() <= window.start()) return -1;
            return time < window.end() ? -1 : 3;
        }
        var timing = PerBlockAnimationTiming.layout(config, start, end);
        float firstAction = start + timing.delay();
        if (time < firstAction) return 2;
        boolean instantAtStart = config.getMode() == com.director.core.container.AnimationMode.INSTANT
                && time == firstAction;
        return instantAtStart || time < completion ? -1 : 3;
    }

    public long getContainerFailureCount() {
        return containerFailureCount;
    }

    public long getContainerFailureCount(UUID containerId) {
        return containerFailureCounts.getOrDefault(containerId, 0L);
    }

    public String getLastContainerFailureMessage() {
        return lastContainerFailureMessage;
    }

    /** Package-level seam used by focused isolation tests; production applies the same gate per container. */
    boolean runIsolatedContainer(UUID containerId, Runnable action) {
        try {
            action.run();
            return true;
        } catch (RuntimeException failure) {
            containerFailureCount++;
            containerFailureCounts.merge(containerId, 1L, Long::sum);
            lastContainerFailureNanos = System.nanoTime();
            lastContainerFailureMessage = containerId + ":" + failure.getClass().getSimpleName() + ":" + failure.getMessage();
            long failures = containerFailureCounts.getOrDefault(containerId, 1L);
            if (failures <= 3 || (failures & (failures - 1)) == 0) {
                LOGGER.warn("[TimelineClipBlockController] container {} update failed; {}: {}",
                        containerId, TimelineSamplingContext.current().isStrict()
                                ? "aborting deterministic sample" : "retaining last stable frame",
                        failure.toString(), failure);
            }
            if (TimelineSamplingContext.current().isStrict()) throw failure;
            return false;
        }
    }

    /** Guarantees frame-tail cleanup even when an unexpected subsystem exception escapes. */
    void runFrame(Runnable frameBody) {
        try {
            frameBody.run();
        } finally {
            BlockVisualStateManager.getInstance().finishFrame();
        }
    }

    private void ensureModelListenerBound(TimelineModel model) {
        if (!TimelineSamplingContext.current().isPreview()) {
            return;
        }
        if (model == observedModel) {
            return;
        }
        if (observedModel != null) {
            observedModel.removeModelChangeListener(modelChangeListener);
        }
        if (TimelineSamplingContext.current().isPreview()) {
            wholeEffectCursor.reset();
            resetPerBlockEffects();
            com.director.core.animation.WholeContainerImpactRingPlayer.clear();
        }
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
            if (TimelineSamplingContext.current().isPreview()) {
                wholeEffectCursor.reset();
                resetPerBlockEffects();
                com.director.core.animation.WholeContainerImpactRingPlayer.clear();
            }
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[TimelineClipBlockController] timelineModel 为空，跳过更新");
            }
            return;
        }

        final long t0 = System.nanoTime();
        ensureModelListenerBound(activeModel);
        var sectionChanges = com.director.core.container.visibility.ClientSectionInvalidation.getInstance();
        long sectionRevision = sectionChanges.revision();

        final boolean preview = TimelineSamplingContext.current().isPreview();
        final boolean forceStructureRefresh = preview && previewResyncPending;
        float currentTime = activeModel.getCurrentTime();
        boolean playing = preview && activeModel.isPlaying();
        boolean scrubbing = preview && activeModel.isScrubbing();
        boolean effectsFrame = false;
        if (preview) {
            wholeEffectCursor.beginFrame(currentTime, playing, scrubbing);
            if (wholeEffectCursor.shouldClearEffects(scrubbing))
                com.director.core.animation.WholeContainerImpactRingPlayer.clear();
            // Stateless poses can seek freely; particle gates belong to one forward playback run.
            boolean finalAdvancingFrame = perBlockEffectsPlaying && currentTime > lastPerBlockEffectTime;
            effectsFrame = !scrubbing && (playing || finalAdvancingFrame);
            if (!effectsFrame || !perBlockEffectsPlaying || currentTime < lastPerBlockEffectTime) {
                resetPerBlockEffects();
            }
            lastPerBlockEffectTime = currentTime;
            perBlockEffectsPlaying = playing && !scrubbing;
        }
        boolean previewMode = !effectsFrame;
        boolean previewTimeChanged = Float.isNaN(lastHandledPreviewTime)
                || Math.abs(currentTime - lastHandledPreviewTime) > com.director.core.animation.AnimationConstants.TIME_EPSILON;

        if (preview && !playing
                && !scrubbing
                && playing == lastObservedPlaying
                && !previewResyncPending
                && !previewTimeChanged
                && !worldHandoffPending
                && !TimelinePlacementBudget.isCatchUp()
                && sectionRevision == lastHandledSectionRevision
                && timelineRevision == lastHandledRevision) {
            timelineDrivingPaused = true;
            return;
        }
        if (preview) {
            TimelinePlacementBudget.beginFrame(scrubbing, wasScrubbing, !playing && previewTimeChanged);
            wasScrubbing = scrubbing;
            worldHandoffPending = false;
            // Changes made during this update receive a follow-up pass, including overlapping containers.
            lastHandledSectionRevision = sectionRevision;
        }
        frameSequenceVisits = 0;
        frameSettledChecks = frameSettledSkipped = 0;

        BlockVisualStateManager visualStateManager = BlockVisualStateManager.getInstance();
        PreviewFrameState savedPreviewState = preview ? null : new PreviewFrameState();

        try {
            runFrame(() -> {
        Set<UUID> failedContainerIds = new HashSet<>();
        Map<UUID, BlockVisualStateManager.ContainerCheckpoint> checkpoints = new HashMap<>();

        // currentFrameVisibleBlocks：保持为“渲染可见集合”（用于遮挡/overlay/视觉状态）
        Map<UUID, Set<BlockPos>> currentFrameVisibleBlocks = new LinkedHashMap<>();
        // currentFrameWorldBlocks：用于真实世界方块落地（showContainerBlocks）
        Map<UUID, Set<BlockPos>> currentFrameWorldBlocks = new LinkedHashMap<>();
        Map<UUID, Float> currentFrameOpacity = new LinkedHashMap<>();
        Map<UUID, List<BlockSequenceEntry>> currentFrameSequences = new LinkedHashMap<>();
        Map<UUID, ClipAnimationConfig> currentFrameConfigs = new LinkedHashMap<>();
        Map<UUID, Container> currentFrameContainers = new LinkedHashMap<>();
        Map<UUID, TimelineClip> currentFrameClips = new LinkedHashMap<>();
        Map<UUID, Integer> currentFrameStaticBuckets = new LinkedHashMap<>();

        // 本帧世界方块（AIR/恢复）变化导致的重渲染位置（与视觉覆盖层无关）

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("[TimelineClipBlockController] 开始更新 (当前时间: {}s)", currentTime);
        }

        final long tAfterClear = System.nanoTime();

        // Sample each container independently. A bad sequence/config/world read cannot abort
        // the rest of the frame; a previous stable sample is retained until the next success.
        for (TimelineTrack track : activeModel.getAllTracks()) {
            if (track == null) continue;
            boolean trackEnabled = !track.isLocked() && track.isVisible();
            if (!isBlockTrackType(track.getType())) continue;
            for (TimelineClip clip : track.getClips()) {
                if (clip == null || clip.getBoundContainerId() == null) continue;
                UUID containerId = clip.getBoundContainerId();
                Container container = DirectorServices.get().containers().getContainer(containerId);
                if (container == null) continue;

                boolean sampled = runIsolatedContainer(containerId, () -> {
                    ensureContainerWorldBinding(container);
                    if (preview) {
                        reconcileContainerGeometry(container);
                    }
                    ClipAnimationConfig config = clip.getAnimationConfigForEditing();
                    if (config == null) {
                        config = ClipAnimationConfig.defaultConfig();
                        LOGGER.debug("[TimelineClipBlockController] Clip {} animation config missing, using default",
                                clip.getLabel());
                    }
                    ClipFrameSample sample = sampleClipFrame(clip, currentTime, trackEnabled, container, config, previewMode);
                    Set<BlockPos> renderBlocks = isContainerInClientWorld(container)
                            ? sample.visibility().renderBlocks() : Collections.emptySet();
                    currentFrameContainers.put(containerId, container);
                    currentFrameClips.put(containerId, clip);
                    currentFrameConfigs.put(containerId, config);
                    currentFrameVisibleBlocks.put(containerId, renderBlocks);
                    currentFrameWorldBlocks.put(containerId, sample.visibility().worldBlocks());
                    currentFrameOpacity.put(containerId, sample.opacity());
                    currentFrameSequences.put(containerId, sample.visibility().sequenceEntries());
                    currentFrameStaticBuckets.put(containerId, sample.staticBucket());
                });
                if (sampled) {
                    containersNeedingRetry.remove(containerId);
                } else {
                    failedContainerIds.add(containerId);
                    containersNeedingRetry.add(containerId);
                    putStableFrame(containerId, stableFramesByContainer.get(containerId), container, clip,
                            currentFrameVisibleBlocks, currentFrameWorldBlocks, currentFrameOpacity,
                            currentFrameSequences, currentFrameConfigs, currentFrameContainers,
                            currentFrameClips, currentFrameStaticBuckets);
                }
            }
        }

        Map<UUID, Set<BlockPos>> desiredWorldBlocks = new LinkedHashMap<>();
        for (Map.Entry<UUID, Set<BlockPos>> entry : currentFrameWorldBlocks.entrySet()) {
            Set<BlockPos> worldSet = entry.getValue();
            desiredWorldBlocks.put(entry.getKey(), worldSet != null ? worldSet : Collections.emptySet());
        }

        // Submit the complete sampled target. Only the server tick budgets actual world work;
        // render FPS must never limit the rate at which a completed container can land.
        Map<UUID, Set<BlockPos>> visibilityWorldBlocks = desiredWorldBlocks;
        final long tAfterVisible = System.nanoTime();

        // ✅ 步骤1.5：计算世界落地集的变化量（仅用于 needsVisibilitySync 判断）
        //
        // 【关键修复】彻底去掉 addBlockAndNeighborsForRerender 的调用。
        // previewMode 下，世界方块由服务端 tick() 里的 setBlockState 实际修改，
        // MC 客户端收到数据包后会自动触发区块重建，不需要客户端再调用 scheduleBlockRenders。
        // 如果客户端对可见集对称差（几万 pos）手动调用 scheduleBlockRenders，
        // 会导致 GPU 每帧重建几百个 chunk section，帧时间拉到 100ms+，FPS 掉到 7~10。
        // scheduleBlockRenders 仅在 BlockVisualStateManager overlay/suppress 状态变化时才需要，
        // 播放和拖动预览都通过 visualStateManager.getChangedBlocks() 驱动。
        Map<UUID, Boolean> worldSetChangedByContainer = new HashMap<>();
        for (Map.Entry<UUID, Set<BlockPos>> entry : visibilityWorldBlocks.entrySet()) {
            UUID containerId = entry.getKey();
            Set<BlockPos> now = entry.getValue() != null ? entry.getValue() : java.util.Collections.emptySet();
            Set<BlockPos> prev = lastFrameWorldBlocks.get(containerId);
            if (prev == now) {
                worldSetChangedByContainer.put(containerId, false);
                continue;
            }
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
            if (prev == now) {
                renderSetChangedByContainer.put(containerId, false);
                continue;
            }
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
                                occludedContainers, containerToClipMap, failedContainerIds);
        final long tAfterOcclusion = System.nanoTime();

        // Capture before beginTimelineFrame restores animation baselines and clears entries.
        Set<UUID> checkpointIds = new HashSet<>(currentFrameContainers.keySet());
        checkpointIds.addAll(failedContainerIds);
        for (UUID containerId : checkpointIds) {
            checkpoints.put(containerId, visualStateManager.beginContainerUpdate(containerId));
        }
        visualStateManager.beginTimelineFrame();

        // ✅ 步骤3：应用方块可见性状态（两层系统）
        // 双重控制机制：
        // 1. 通过 ContainerVisibilityService 控制真实方块显示/隐藏
        // 2. 通过 BlockVisualStateManager 控制视觉状态（透明度、覆盖层等）
        var visibilityService = DirectorServices.get().visibilityOrNull();

        for (Map.Entry<UUID, Set<BlockPos>> entry : currentFrameVisibleBlocks.entrySet()) {
            UUID containerId = entry.getKey();
            if (failedContainerIds.contains(containerId)) {
                visualStateManager.rollbackContainerUpdate(containerId);
                continue;
            }
            // renderBlocks：用于视觉状态/overlay/动画采样
            Set<BlockPos> renderBlocks = entry.getValue();
            // worldBlocks：用于真实世界方块落地（ENTER 延后到动画结束）
            Set<BlockPos> worldBlocks = visibilityWorldBlocks.getOrDefault(containerId,
                    java.util.Collections.emptySet());
            float opacity = currentFrameOpacity.getOrDefault(containerId, 1.0f);

            Container container = currentFrameContainers.get(containerId);
            if (container == null) {
                visualStateManager.rollbackContainerUpdate(containerId);
                continue;
            }

            boolean worldSetChanged = worldSetChangedByContainer.getOrDefault(containerId, true);
            boolean renderSetChanged = renderSetChangedByContainer.getOrDefault(containerId, true);
            long containerMod = container.getGeometryRevision();
            LastVisualParams lastParams = lastVisualParamsByContainerId.get(containerId);
            boolean containerStructureChanged = forceStructureRefresh || (lastParams == null)
                    || lastParams.container != container
                    || (lastParams.geometryRevision != containerMod)
                    || !Objects.equals(lastParams.levelKey, container.getLevelKey());

            // 🔹 关键改动：判断容器是否被遮挡
            // 如果被遮挡，方块仍然需要在世界中存在，但通过 Mixin 阻止渲染
            TimelineClip occludedClip = containerToClipMap.get(containerId);
            boolean occlusionTransparencyEnabled = occludedClip == null
                    || (occludedClip.getAnimationConfigForEditing() != null
                    && occludedClip.getAnimationConfigForEditing().isOcclusionTransparencyEnabled());
            boolean isOccluded = occlusionTransparencyEnabled && occludedContainers.contains(containerId);

            boolean visualParamsChanged = lastParams == null
                    || Math.abs(lastParams.opacity - opacity) > 1e-4f
                    || lastParams.occluded != isOccluded
                    || lastParams.animationHash != visibleAnimationHash(containerToClipMap.get(containerId), currentFrameConfigs.get(containerId))
                    || containerStructureChanged;

            boolean applied = runIsolatedContainer(containerId, () -> {
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
                if (preview && needsVisibilitySync) {
                    visibilityService.resumeTimelineDriving();
                    // 时间线驱动：统一走分帧追平，避免 showContainerBlocks 的 firstCall 全量同步/强制光照刷新卡死主线程
                    visibilityService.showContainerBlocksForTimeline(container, worldBlocks);
                }
            } else {
                LOGGER.warn("[TimelineClipBlockController] ContainerVisibilityService 未初始化，无法控制方块显示");
            }

            // Client visual/render state is dimension-local. Server visibility work above still
            // uses the container's bound world, even while another client dimension is active.
            if (isContainerInClientWorld(container) && container.getLevelKey() != null) {
            ClipAnimationConfig frameConfig = currentFrameConfigs.get(containerId);
            if (frameConfig != null && frameConfig.isWholeContainer()) {
                registerWholeContainerEntries(visualStateManager, container, occludedClip,
                        frameConfig, currentTime, renderBlocks, opacity);
            } else {
                Set<BlockPos> prevVisible = lastFrameVisibleBlocks.getOrDefault(containerId,
                        java.util.Collections.emptySet());

                if (containerStructureChanged) {
                    List<BlockPos> all = getCachedContainerBlocks(container);
                    if (renderBlocks == null || renderBlocks.isEmpty()) {
                        visualStateManager.setBlocksState(all, BlockVisualStateManager.BlockVisualState.hidden(),
                                containerId, container.getLevelKey());
                    } else {
                        visualStateManager.setBlocksState(all, BlockVisualStateManager.BlockVisualState.hidden(),
                                containerId, container.getLevelKey());
                        BlockVisualStateManager.BlockVisualState visibleState = (opacity < 1.0f)
                                ? BlockVisualStateManager.BlockVisualState.ghost(opacity, true, true)
                                .withSuppressWorldRendering(true)
                                : BlockVisualStateManager.BlockVisualState.normal();
                        visualStateManager.setBlocksState(renderBlocks, visibleState, containerId, container.getLevelKey());
                    }
                } else {
                    if (renderSetChanged) {
                        var hidden = BlockVisualStateManager.BlockVisualState.hidden();
                        var visible = opacity < 1.0f
                                ? BlockVisualStateManager.BlockVisualState.ghost(opacity, true, true)
                                .withSuppressWorldRendering(true)
                                : BlockVisualStateManager.BlockVisualState.normal();
                        SequenceVisibility.forEachDifference(prevVisible, renderBlocks,
                                pos -> visualStateManager.setBlockState(
                                        new BlockVisualStateManager.PositionKey(container.getLevelKey(), pos), hidden, containerId));
                        SequenceVisibility.forEachDifference(renderBlocks, prevVisible,
                                pos -> visualStateManager.setBlockState(
                                        new BlockVisualStateManager.PositionKey(container.getLevelKey(), pos), visible, containerId));
                    }

                    if (visualParamsChanged) {
                        if (renderBlocks != null && !renderBlocks.isEmpty()) {
                            BlockVisualStateManager.BlockVisualState visibleState = (opacity < 1.0f)
                                    ? BlockVisualStateManager.BlockVisualState.ghost(opacity, true, true)
                                    .withSuppressWorldRendering(true)
                                    : BlockVisualStateManager.BlockVisualState.normal();
                            visualStateManager.setBlocksState(renderBlocks, visibleState, containerId, container.getLevelKey());
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
                        opacity,
                        previewMode,
                        currentFrameStaticBuckets.getOrDefault(containerId, -1));
            }
            }
            });
            if (applied) {
                containersNeedingRetry.remove(containerId);
                visualStateManager.commitContainerUpdate(containerId);
                StableContainerFrame stable = new StableContainerFrame(
                        new VisibleBlocksResult(currentFrameWorldBlocks.get(containerId),
                                currentFrameVisibleBlocks.get(containerId), currentFrameSequences.get(containerId)),
                        opacity, currentFrameConfigs.get(containerId).copy(), currentFrameClips.get(containerId),
                        currentFrameStaticBuckets.getOrDefault(containerId, -1));
                stableFramesByContainer.put(containerId, stable);
                lastStaticBucketByClip.put(currentFrameClips.get(containerId), stable.staticBucket());
                LastVisualParams updated = lastParams != null ? lastParams : new LastVisualParams();
                updated.opacity = opacity;
                updated.occluded = isOccluded;
                updated.container = container;
                updated.geometryRevision = containerMod;
                updated.levelKey = container.getLevelKey();
                updated.animationHash = visibleAnimationHash(containerToClipMap.get(containerId), currentFrameConfigs.get(containerId));
                lastVisualParamsByContainerId.put(containerId, updated);
            } else {
                failedContainerIds.add(containerId);
                containersNeedingRetry.add(containerId);
                visualStateManager.rollbackContainerUpdate(containerId);
                // If server submission had started before a client-side failure, restore the
                // previous stable target rather than leaving a half-applied container.
                if (preview && visibilityService != null) {
                    try {
                        Set<BlockPos> previousWorld = lastFrameWorldBlocks.get(containerId);
                        visibilityService.showContainerBlocksForTimeline(container,
                                previousWorld != null ? previousWorld : Collections.emptySet());
                    } catch (RuntimeException rollbackFailure) {
                        LOGGER.warn("[TimelineClipBlockController] visibility rollback failed for {}: {}",
                                containerId, rollbackFailure.toString());
                    }
                }
            }
        }

        // Failed containers keep their previous sampled target in all bookkeeping maps.
        for (UUID containerId : failedContainerIds) {
            StableContainerFrame stable = stableFramesByContainer.get(containerId);
            if (stable != null) {
                putStableFrame(containerId, stable, currentFrameContainers.get(containerId), stable.clip(),
                        currentFrameVisibleBlocks, currentFrameWorldBlocks, currentFrameOpacity,
                        currentFrameSequences, currentFrameConfigs, currentFrameContainers,
                        currentFrameClips, currentFrameStaticBuckets);
            } else {
                currentFrameVisibleBlocks.remove(containerId);
                currentFrameWorldBlocks.remove(containerId);
                currentFrameOpacity.remove(containerId);
                currentFrameSequences.remove(containerId);
                currentFrameConfigs.remove(containerId);
                currentFrameContainers.remove(containerId);
                currentFrameClips.remove(containerId);
                currentFrameStaticBuckets.remove(containerId);
            }
            if (stable != null) desiredWorldBlocks.put(containerId, stable.visibility().worldBlocks());
            else desiredWorldBlocks.remove(containerId);
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
        cleanupContainersNoLongerControlled(desiredWorldBlocks.keySet(), visibilityService, visualStateManager, rerender,
                failedContainerIds);
        int visualCandidates = visualStateManager.getChangeCandidateCount();

        for (var pos : rerender) {
            if (visualStateManager.hasWorldSuppressionChanged(pos))
                com.director.core.animation.WholeContainerRenderSections.getInstance()
                        .request(MinecraftClient.getInstance().world, pos);
        }

        // Suppression changes above are queued by section and drained with a render budget.
        // Opacity-only changes need no world mesh rebuild; world edits use vanilla updates.

        final long tAfterRerender = System.nanoTime();

        if (preview) {
            // 更新 lastFrameVisibleBlocks（渲染可见集）与 lastFrameWorldBlocks（世界落地集），并清理已不存在的容器
            Set<UUID> retainedContainerIds = new HashSet<>(currentFrameContainers.keySet());
            retainedContainerIds.addAll(failedContainerIds);
            lastFrameVisibleBlocks.keySet().retainAll(retainedContainerIds);
            lastFrameWorldBlocks.keySet().retainAll(retainedContainerIds);
            lastVisualParamsByContainerId.keySet().retainAll(retainedContainerIds);
            for (Map.Entry<UUID, Set<BlockPos>> e : currentFrameVisibleBlocks.entrySet()) {
                UUID id = e.getKey();
                Set<BlockPos> s = e.getValue();
                // 性能关键：避免每帧复制大 Set；该 Set 在本帧之后不再被修改，直接复用引用即可。
                lastFrameVisibleBlocks.put(id, s != null ? s : java.util.Collections.emptySet());
            }
            Map<UUID, Set<BlockPos>> lastWorldBlocksSource = desiredWorldBlocks;
            for (Map.Entry<UUID, Set<BlockPos>> e : lastWorldBlocksSource.entrySet()) {
                UUID id = e.getKey();
                Set<BlockPos> s = e.getValue();
                lastFrameWorldBlocks.put(id, s != null ? s : java.util.Collections.emptySet());
            }
            lastStaticBucketByClip.keySet().retainAll(currentFrameClips.keySet());
            stableFramesByContainer.keySet().retainAll(retainedContainerIds);
            wholeRenderCacheByContainer.keySet().retainAll(retainedContainerIds);
            perBlockRenderCacheByContainer.keySet().retainAll(retainedContainerIds);
            lastHandledRevision = timelineRevision;
            lastHandledPreviewTime = currentTime;
            lastObservedPlaying = playing;
            if (!playing && !scrubbing) {
                if (!timelineDrivingPaused && visibilityService != null) {
                    visibilityService.pauseTimelineDriving();
                }
                timelineDrivingPaused = true;
            } else {
                timelineDrivingPaused = false;
            }
        }

        // ✅ 修复：定期清理不再活跃的容器/片段缓存，防止内存泄漏
        // 每 N 帧（约 3 秒 @ 60fps）执行一次清理
        if (preview && ++cacheCleanupCounter >= com.director.core.animation.AnimationConstants.CACHE_CLEANUP_INTERVAL) {
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
                    "[Perf][TimelineClipBlockController] total={}ms (clear={}ms, step1_visible={}ms, diff={}ms, occlusion={}ms, step3_apply+entries={}ms, rerender={}ms) containers={}, visibleTotal={}, seqTotal={}, sequenceVisits={}, settledChecks={}, settledSkipped={}, visualCandidates={}, renderEntries={}",
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
                    frameSequenceVisits,
                    frameSettledChecks,
                    frameSettledSkipped,
                    visualCandidates,
                    renderEntryCount);
            }
            });
            if (preview) previewResyncPending = false;
        } finally {
            if (savedPreviewState != null) {
                savedPreviewState.restore();
                previewResyncPending = true;
            }
        }
    }

    /** Unified visibility/opacity phase sample (C-06), also used by focused boundary tests. */
    record ClipFrameSample(VisibleBlocksResult visibility, float opacity, int staticBucket) {}

    ClipFrameSample sampleClipFrame(TimelineClip clip, float currentTime, boolean trackEnabled,
                                    Container container, ClipAnimationConfig config, boolean previewMode) {
        VisibleBlocksResult visibility = calculateVisibleBlocks(
                clip, currentTime, trackEnabled, container, config, previewMode);
        boolean demolition = container != null && container.getType() == ContainerType.DEMOLITION;
        // A disabled or not-yet-started demolition clip is a no-op: preserve structure and alpha.
        float opacity = demolition && (!trackEnabled || currentTime < clip.getStartTime())
                ? 1.0f : clip.computeAlphaAtGlobalTime(currentTime);
        return new ClipFrameSample(visibility, opacity, staticTimeBucket(clip, currentTime, trackEnabled, container));
    }

    static RegistryKey<World> currentClientWorldKey() {
        try {
            var world = MinecraftClient.getInstance().world;
            return world == null ? null : world.getRegistryKey();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean isContainerInClientWorld(Container container) {
        RegistryKey<World> current = currentClientWorldKey();
        return current != null && container != null && Objects.equals(container.getLevelKey(), current);
    }

    private void putStableFrame(UUID containerId, StableContainerFrame stable, Container container, TimelineClip clip,
                                Map<UUID, Set<BlockPos>> visible, Map<UUID, Set<BlockPos>> world,
                                Map<UUID, Float> opacity, Map<UUID, List<BlockSequenceEntry>> sequences,
                                Map<UUID, ClipAnimationConfig> configs, Map<UUID, Container> containers,
                                Map<UUID, TimelineClip> clips, Map<UUID, Integer> buckets) {
        if (stable == null) return;
        visible.put(containerId, stable.visibility().renderBlocks());
        world.put(containerId, stable.visibility().worldBlocks());
        opacity.put(containerId, stable.opacity());
        sequences.put(containerId, stable.visibility().sequenceEntries());
        configs.put(containerId, stable.config());
        if (container != null) containers.put(containerId, container);
        clips.put(containerId, clip != null ? clip : stable.clip());
        buckets.put(containerId, stable.staticBucket());
    }

    private VisibleBlocksResult calculateVisibleBlocks(TimelineClip clip,
                                                       float currentTime,
                                                       boolean trackEnabled,
                                                       Container container,
                                                       ClipAnimationConfig config,
                                                       boolean previewMode) {
        if (!TimelineSamplingContext.current().isPreview()) {
            return calculateVisibleBlocksInternal(clip, currentTime, trackEnabled, container, config, previewMode);
        }
        // Whole-object boundaries must also react to property edits at a stationary playhead.
        if (config != null && config.isWholeContainer()) {
            return calculateVisibleBlocksInternal(clip, currentTime, trackEnabled, container, config, previewMode);
        }
        // ⭐ 性能关键优化：检查可见方块结果缓存
        // 如果时间、轨道状态、容器内容都没变，直接返回缓存结果
        // 这避免了每帧重新构建包含数万元素的 HashSet
        VisibleBlocksCache cache = visibleBlocksCacheByClip.get(clip);
        if (cache != null && container != null && config != null) {
            long containerMod = container.getGeometryRevision();
            var params = config.getSequenceParameters();
            int paramsHash = hashSequenceParameters(params);

            boolean sameInputs = cache.lastTrackEnabled == trackEnabled
                    && cache.lastPreviewMode == previewMode
                    && cache.container == container
                    && cache.geometryRevision == containerMod
                    && cache.containerType == container.getType()
                    && cache.sequenceParamsHash == paramsHash
                    && cache.animationHash == visibleAnimationHash(clip, config)
                    && cache.result != null;
            if (sameInputs) {
                int bucket = staticTimeBucket(clip, currentTime, trackEnabled, container);
                if (bucket >= 0 && cache.staticBucket == bucket) return cache.result;
                var sequenceCache = sequenceCacheByClip.get(clip);
                if (bucket < 0 && sequenceCache != null && sequenceCache.entries == cache.result.sequenceEntries()
                        && sequenceCache.geometryRevision == containerMod
                        && sequenceCache.sequenceParamsHash == paramsHash
                        && Math.abs(sequenceCache.clipDuration - clip.getLength()) < 1e-4f) {
                    var timing = PerBlockAnimationTiming.layout(config, clip.getStartTime(), clip.getEndTime());
                    int[] cutoffs = sequenceCutoffs(sequenceCache.entries, timing, currentTime, clip.getStartTime(),
                            config.getMode() == com.director.core.container.AnimationMode.INSTANT);
                    if (cache.started == cutoffs[0] && cache.finished == cutoffs[1]) return cache.result;
                }
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
        cache.lastTrackEnabled = trackEnabled;
        cache.lastPreviewMode = previewMode;
        cache.container = container;
        cache.geometryRevision = container != null ? container.getGeometryRevision() : 0;
        cache.containerType = container != null ? container.getType() : null;
        cache.sequenceParamsHash = config != null ? hashSequenceParameters(config.getSequenceParameters()) : 0;
        cache.animationHash = visibleAnimationHash(clip, config);
        cache.result = result;
        cache.staticBucket = staticTimeBucket(clip, currentTime, trackEnabled, container);
        if (!result.sequenceEntries().isEmpty() && config != null) {
            var timing = PerBlockAnimationTiming.layout(config, clip.getStartTime(), clip.getEndTime());
            int[] cutoffs = sequenceCutoffs(result.sequenceEntries(), timing, currentTime, clip.getStartTime(),
                    config.getMode() == com.director.core.container.AnimationMode.INSTANT);
            cache.started = cutoffs[0];
            cache.finished = cutoffs[1];
        } else {
            cache.started = cache.finished = -1;
        }

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
                Set<BlockPos> all = getCachedAllBlocks(container);
                return new VisibleBlocksResult(all, all, sequenceEntries);
            }
            return new VisibleBlocksResult(Collections.emptySet(), Collections.emptySet(), sequenceEntries);
        }

        float clipStart = clip.getStartTime();
        float clipEnd = clip.getEndTime();

        // 片段左侧
        if (currentTime < clipStart) {
            if (isDemolition) {
                Set<BlockPos> all = getCachedAllBlocks(container);
                return new VisibleBlocksResult(all, all, sequenceEntries);
            }
            return new VisibleBlocksResult(Collections.emptySet(), Collections.emptySet(), sequenceEntries);
        }

        if (config != null && config.isWholeContainer()) {
            var frame = com.director.core.animation.WholeContainerAnimation.sample(
                    config, clipStart, clipEnd, currentTime, isDemolition);
            Set<BlockPos> all = getCachedAllBlocks(container);
            return new VisibleBlocksResult(frame.worldVisible() ? all : Collections.emptySet(),
                    frame.renderVisible() ? all : Collections.emptySet(), List.of());
        }

        // 片段范围内：按序列推进。有效完成时间包含逐块 tail，且 ALL_AT_ONCE
        // 可能早于片段右边界完成；两种情况都必须走同一套序列采样。
        float effectiveEnd = config == null ? clipEnd
                : com.director.core.animation.PerBlockAnimationTiming.effectiveAnimationEndTime(config, clipStart, clipEnd);
        if (currentTime >= clipStart && currentTime <= effectiveEnd) {
            float clipDuration = clipEnd - clipStart;
            float animDuration = clipDuration;
            float delay = 0.0f;
            if (config != null) {
                animDuration = config.getDuration();
                delay = Math.max(0.0f, config.getDelay());
                try {
                    var seq = config.getSequenceParameters();
                    if (seq != null) {
                        delay += Math.max(0.0f, seq.getGlobalDelay());
                    }
                } catch (Exception ignored) {
                }
            }
            PerBlockAnimationTiming.Layout timing = null;
            if (config != null) {
                timing = PerBlockAnimationTiming.layout(config, clipStart, clipEnd);
            }

            if (timing != null) {
                delay = timing.delay();
            }
            float sequenceSpan = 0;
            if (timing != null) {
                sequenceSpan = timing.sequenceSpan();
            }

            boolean isInstant = false;
            try {
                isInstant = config != null
                        && config.getMode() == com.director.core.container.AnimationMode.INSTANT;
            } catch (Exception ignored) {
            }

            // 播放头尚未推进到首个方块时刻：跳过 5w+ 序列生成（拖入片段起点时常见）
            float normalizedReveal = 0;
            if (timing != null) {
                normalizedReveal = timing.threshold(currentTime - clipStart - delay);
            }
            if (normalizedReveal < 0.0f) {
                if (isDemolition) {
                    Set<BlockPos> all = getCachedAllBlocks(container);
                    return new VisibleBlocksResult(all, all, List.of());
                }
                return new VisibleBlocksResult(Collections.emptySet(), Collections.emptySet(), List.of());
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
                Set<BlockPos> all = getCachedAllBlocks(container);
                return new VisibleBlocksResult(all, all, sequenceEntries);
            }

            if (config != null) {
                timing = PerBlockAnimationTiming.layout(config, clipStart, clipEnd, sequenceEntries.getFirst());
                animDuration = timing.duration();
                sequenceSpan = timing.sequenceSpan();
            }

            // Only the sequence cutoffs affect membership. Moving time within the same
            // boundaries reuses immutable sets; animation poses still sample the exact time.
            int[] cutoffs = timing == null ? new int[] {0, 0}
                    : sequenceCutoffs(sequenceEntries, timing, currentTime, clipStart, isInstant);
            int started = cutoffs[0];
            int finished = cutoffs[1];
            var visibility = sequenceCacheByClip.get(clip).visibility;
            var frame = visibility.sample(isDemolition ? finished : started,
                    isDemolition ? started : finished, isDemolition);
            return new VisibleBlocksResult(frame.world(), frame.render(), sequenceEntries);
        }

        // 有效动画完成之后：普通容器全量显示，拆除容器保持全清除。
        if (currentTime > effectiveEnd) {
            if (!isDemolition) {
                // 普通容器：全量显示
                Set<BlockPos> all = getCachedAllBlocks(container);
                return new VisibleBlocksResult(all, all, sequenceEntries);
            } else {
                // 拆除容器：全清除 => 保持空集合
                return new VisibleBlocksResult(Collections.emptySet(), Collections.emptySet(), sequenceEntries);
            }
        }

        return new VisibleBlocksResult(Collections.emptySet(), Collections.emptySet(), sequenceEntries);
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
        if (clip == null || container.getLevelKey() == null) return;
        boolean exit = container.getType() == ContainerType.DEMOLITION;
        boolean enabled = clip.getParentTrack() == null
                || (!clip.getParentTrack().isLocked() && clip.getParentTrack().isVisible());
        var frame = com.director.core.animation.WholeContainerAnimation.sample(
                config, clip.getStartTime(), clip.getEndTime(), enabled ? time : clip.getStartTime() - 1, exit);
        if (TimelineSamplingContext.current().isPreview() && enabled && opacity > 0 && config.getWholeContainer().isDustEnabled()
                && wholeEffectCursor.crosses(com.director.core.animation.WholeContainerDust.eventTime(
                        config, clip.getStartTime(), clip.getEndTime(), exit))) {
            emitWholeContainerDust(container, config);
        }
        var pivot = com.director.core.animation.WholeContainerAnimation.pivot(
                container.getBoundingBox(), config.getWholeContainer());
        long geometry = container.getGeometryRevision();
        long sectionRevision = com.director.core.container.visibility.ClientSectionInvalidation.getInstance().revision();
        RegistryKey<World> level = container.getLevelKey();
        WholeRenderCache cached = wholeRenderCacheByContainer.get(container.getId());
        boolean same = cached != null && cached.config.hasSameValues(config)
                && cached.geometryRevision == geometry && Float.compare(cached.opacity, opacity) == 0
                && (cached.visible == visibleBlocks || cached.visible.equals(visibleBlocks))
                && cached.frame.equals(frame) && cached.pivot.equals(pivot)
                && cached.sectionRevision == sectionRevision && Objects.equals(cached.level, level);
        if (same) {
            manager.setBlocksState(getCachedContainerBlocks(container),
                    BlockVisualStateManager.BlockVisualState.hidden().withSuppressWorldRendering(true),
                    container.getId(), level);
            for (var write : cached.writes) {
                manager.setBlockState(new BlockVisualStateManager.PositionKey(level, write.pos()), write.state(), container.getId());
            }
            manager.addRenderEntries(container.getId(), level, cached.entries);
            return;
        }

        List<VisualWrite> writes = new ArrayList<>();
        List<BlockVisualStateManager.RenderEntry> entries = new ArrayList<>();
        var hidden = BlockVisualStateManager.BlockVisualState.hidden().withSuppressWorldRendering(true);
        List<BlockPos> all = getCachedContainerBlocks(container);
        manager.setBlocksState(all, hidden, container.getId(), level);
        for (BlockPos pos : all) writes.add(new VisualWrite(pos, hidden));
        var sharedTransform = frame.worldTransform(pivot);
        for (BlockPos pos : visibleBlocks) {
            BlockState state = resolveBlockStateForRender(container, pos);
            if (state == null) continue;
            var visual = opacity < 0.999f
                    ? BlockVisualStateManager.BlockVisualState.ghost(opacity, true, true)
                    : BlockVisualStateManager.BlockVisualState.normal();
            boolean worldReady = MinecraftClient.getInstance().world != null
                    && MinecraftClient.getInstance().world.getBlockState(pos).equals(state);
            if (TimelineSamplingContext.current().isPreview() && !frame.animating() && !worldReady)
                worldHandoffPending = true;
            boolean overlay = frame.animating() || !worldReady || opacity < 0.999f;
            if (!overlay) {
                manager.setBlockState(new BlockVisualStateManager.PositionKey(level, pos), visual, container.getId());
                writes.add(new VisualWrite(pos, visual));
                continue;
            }
            var snapshot = new BlockAnimationSnapshot(pos, state);
            snapshot.markFullyVisible();
            snapshot.setRawProgress(frame.progress());
            // One shared pivot/world matrix is used by rendering, neighbors, AO, light,
            // biome color and BlockEntity display-coordinate queries.
            snapshot.setSharedWorldTransform(sharedTransform);
            snapshot.setAlpha(frame.alpha());
            visual = visual.withSuppressWorldRendering(true);
            manager.setBlockState(new BlockVisualStateManager.PositionKey(level, pos), visual, container.getId());
            writes.add(new VisualWrite(pos, visual));
            var renderEntry = new BlockVisualStateManager.RenderEntry(pos, state, visual, clip,
                    new BlockSequenceEntry(pos, 0, 0, 0), snapshot);
            entries.add(renderEntry);
            manager.addRenderEntry(renderEntry, container.getId(), level);
        }
        wholeRenderCacheByContainer.put(container.getId(), new WholeRenderCache(config.copy(), geometry, opacity,
                visibleBlocks, frame, pivot, sectionRevision, level, List.copyOf(writes), List.copyOf(entries)));
        // Rebuild old uploaded meshes before the next world draw, including large selections.
        var world = MinecraftClient.getInstance().world;
        for (var pos : all) {
            if (manager.hasWorldSuppressionChanged(pos))
                com.director.core.animation.WholeContainerRenderSections.getInstance().request(world, pos);
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
        int index = 0;
        for (var particle : particles) {
            if (config.getWholeContainer().getDustPreset()
                    == com.director.core.animation.WholeContainerAnimationConfig.DustPreset.DEBRIS_CLOUD) {
                BlockState source = resolveBlockStateForRender(container, particle.source());
                double variant = (index++ * 0.61803398875) % 1;
                if (variant < config.getWholeContainer().getDebrisRatio() && source != null
                        && !source.isAir() && source.hasBlockBreakParticles()) {
                    var debris = new net.minecraft.client.particle.BlockDustParticle(client.world,
                            particle.x(), particle.y(), particle.z(), particle.vx(), particle.vy(), particle.vz(),
                            source, particle.source());
                    debris.setVelocity(particle.vx(), particle.vy(), particle.vz());
                    debris.scale(0.45f + (float) variant * 0.55f);
                    debris.setMaxAge(20 + (int) (variant * 20));
                    client.particleManager.addParticle(debris);
                } else {
                    var smoke = client.particleManager.addParticle(net.minecraft.particle.ParticleTypes.POOF,
                            particle.x(), particle.y(), particle.z(), particle.vx() * 0.4,
                            particle.vy() * 0.4, particle.vz() * 0.4);
                    if (smoke != null) smoke.scale(0.4f);
                }
                continue;
            }
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

    private final Map<TimelineClip, Float> perBlockEffectSampleTime = new java.util.WeakHashMap<>();

    /** Final-pose fallback must not depend on a nonempty animation sequence. */
    private void registerSettledContainerEntries(BlockVisualStateManager manager, Container container,
            TimelineClip clip, Set<BlockPos> visibleBlocks) {
        var world = MinecraftClient.getInstance().world;
        getCachedContainerBlocks(container);
        var visits = blocksCacheByContainer.get(container).settled.visit(world, visibleBlocks,
                container.getBlockStore().getRevision(), pos -> {
            var visual = manager.getBlockState(container.getLevelKey(), pos);
            if (visual.isHidden()) return true;
            BlockState state = resolveBlockStateForRender(container, pos);
            if (state == null) return false;
            boolean ready = world != null && state.equals(world.getBlockState(pos));
            if (TimelineSamplingContext.current().isPreview() && !ready) worldHandoffPending = true;
            if (ready && visual.isNormal() && !visual.shouldSuppressWorldRendering()) return true;
            var snapshot = new BlockAnimationSnapshot(pos, state);
            snapshot.markFullyVisible();
            snapshot.setTransform(null);
            var overlay = visual.withSuppressWorldRendering(true);
            manager.setAnimatedBlockState(pos, overlay, container.getId(), container.getLevelKey());
            manager.addRenderEntry(new BlockVisualStateManager.RenderEntry(pos, state, overlay, clip, null, snapshot),
                    container.getId(), container.getLevelKey());
            return false;
        });
        frameSettledChecks += visits.checked();
        frameSettledSkipped += visits.skipped();
    }

    private void registerRenderEntriesForContainer(
            BlockVisualStateManager visualStateManager,
            Container container,
            TimelineClip clip,
            ClipAnimationConfig config,
            List<BlockSequenceEntry> sequenceEntries,
            float currentTime,
            Set<BlockPos> visibleBlocks,
            float opacity,
            boolean previewMode,
            int staticBucket) {
        if (TimelineSamplingContext.current().isPreview() && previewMode && clip != null) {
            // 拖动不是时间推进，恢复播放时不要补发拖动期间跨过的完成特效。
            perBlockEffectSampleTime.remove(clip);
        }
        if (visualStateManager == null || container == null || clip == null || config == null) {
            return;
        }
        if (visibleBlocks == null || visibleBlocks.isEmpty() && config.getEffects().isEmpty()) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace(
                        "[TimelineClipBlockController] registerRenderEntries: visibleBlocks is empty for container {}",
                        container.getName());
            }
            return;
        }
        if (sequenceEntries == null || sequenceEntries.isEmpty()) {
            registerSettledContainerEntries(visualStateManager, container, clip, visibleBlocks);
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace(
                        "[TimelineClipBlockController] registerRenderEntries: sequenceEntries is empty for container {}",
                        container.getName());
            }
            return;
        }

        long geometryRevision = container.getGeometryRevision();
        long sectionRevision = com.director.core.container.visibility.ClientSectionInvalidation.getInstance().revision();
        RegistryKey<World> level = container.getLevelKey();
        PerBlockRenderCache cached = perBlockRenderCacheByContainer.get(container.getId());
        boolean cacheHit = staticBucket >= 0 && cached != null && cached.config.hasSameValues(config)
                && cached.geometryRevision == geometryRevision && Float.compare(cached.opacity, opacity) == 0
                && (cached.visible == visibleBlocks || cached.visible.equals(visibleBlocks))
                && cached.staticBucket == staticBucket && cached.sectionRevision == sectionRevision
                && Objects.equals(cached.level, level);
        if (cacheHit) {
            if (!previewMode) perBlockEffectSampleTime.put(clip, currentTime);
            for (var write : cached.writes) {
                visualStateManager.setAnimatedBlockState(write.pos(), write.state(), container.getId(), level);
            }
            visualStateManager.addRenderEntries(container.getId(), level, cached.entries);
            return;
        }
        List<VisualWrite> staticWrites = staticBucket >= 0 ? new ArrayList<>() : null;
        List<BlockVisualStateManager.RenderEntry> staticEntries = staticBucket >= 0 ? new ArrayList<>() : null;

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
        var timing = com.director.core.animation.PerBlockAnimationTiming.layout(config, clipStart, clipEnd, sequenceEntries.getFirst());
        Float previousEffectsTime = previewMode ? null : perBlockEffectSampleTime.put(clip, currentTime);
        boolean isInstant = config.getMode() == com.director.core.container.AnimationMode.INSTANT;
        int[] visibleRange = null;
        var cache = sequenceCacheByClip.get(clip);
        if (cache != null && cache.entries == sequenceEntries)
            visibleRange = cache.visibility.entryRange(visibleBlocks);
        var ranges = PerBlockFrameRanges.select(sequenceEntries, timing, currentTime,
                config.getEffects().isEmpty() ? null : previousEffectsTime,
                phase == ClipAnimationConfig.AnimationPhase.EXIT, isInstant,
                visibleRange != null ? visibleRange[0] : 0,
                visibleRange != null ? visibleRange[1] : sequenceEntries.size());
        int registeredEntries = 0;
        for (var range : ranges) frameSequenceVisits += range.to() - range.from();
        // Merge render and event ranges so each position keeps its original event/sample ordering.
        for (var range : ranges) for (int index = range.from(); index < range.to(); index++) {
            BlockSequenceEntry entry = sequenceEntries.get(index);
            if (entry == null) {
                continue;
            }
            BlockPos pos = entry.getPos();
            if (pos != null && !config.getEffects().isEmpty() && previousEffectsTime != null) {
                var effectWindow = timing.window(entry.getNormalizedTime());
                if (previousEffectsTime < effectWindow.start() && currentTime >= effectWindow.start())
                    BlockAnimationEngine.primeEffectsBeforeStart(pos, config, currentTime);
                var end = effectWindow.end();
                if (previousEffectsTime < end && currentTime >= end) {
                    var source = resolveBlockStateForRender(container, pos);
                    if (source != null)
                        BlockAnimationEngine.sample(pos, source, config, entry, clipStart, clipEnd, currentTime,
                                container.getId().getLeastSignificantBits() ^ pos.asLong());
                }
            }
            if (pos == null || !visibleBlocks.contains(pos)) {
                continue;
            }

            // 🔥 步骤 2：获取视觉状态（可能已经被遮挡逻辑修改过）
            BlockVisualStateManager.BlockVisualState visualState = visualStateManager.getBlockState(level, pos);
            if (visualState.isHidden()) {
                continue;
            }

            // ===== 性能关键：只有在“当前块处于动画窗口内”或“视觉状态本身需要 overlay（遮挡/透明）”时才 sample =====
            float seqNormalizedTime = net.minecraft.util.math.MathHelper.clamp(entry.getNormalizedTime(), 0.0f, 1.0f);
            var window = timing.window(seqNormalizedTime);
            float blockStartTime = window.start();
            float blockEndTime = window.end();

            // INSTANT 模式：不需要动画窗口，直接按序列出现
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

            // Only an instant block's exact start needs a direct sample here. All other
            // effects are covered by active animation windows or the crossed start/end
            // event ranges above; settled blocks are never sampled every frame.
            boolean needsEffects = !previewMode && !config.getEffects().isEmpty();
            boolean shouldSampleForEffects = isInstant && needsEffects
                    && Math.abs(currentTime - blockStartTime) <= com.director.core.animation.AnimationConstants.TIME_EPSILON;

            // World and overlay never draw the same block simultaneously.

            if (!inAnimWindow && !shouldSampleForEffects) {
                // 静态块：如果视觉状态是 NORMAL 且不抑制世界渲染，完全不需要 overlay / snapshot
                // ✅ 关键修复：但如果处于交接窗口内，必须继续渲染 overlay 以遮盖区块重建延迟
                if (visualState.isNormal() && !visualState.shouldSuppressWorldRendering()) {
                    var expected = resolveBlockStateForRender(container, pos);
                    var world = MinecraftClient.getInstance().world;
                    if (expected == null || world != null && expected.equals(world.getBlockState(pos))) continue;
                    if (TimelineSamplingContext.current().isPreview()) worldHandoffPending = true;
                    visualState = visualState.withSuppressWorldRendering(true);
                }

                // 🔥 步骤 3：静态但需要 overlay，才解析方块状态（避免每帧对海量静态块做 world.getBlockState）
                blockState = resolveBlockStateForRender(container, pos);
                if (blockState == null) {
                    continue;
                }

                // Settled overlay: construct a lightweight identity snapshot without sampling.
                // Completion/start one-shots were already handled by the event ranges above.
                snapshot = new BlockAnimationSnapshot(pos, blockState);
                snapshot.markFullyVisible();
                snapshot.setSequenceNormalizedTime(seqNormalizedTime);
                snapshot.setTransform(null);
            } else {
                // 🔥 步骤 3：动画窗口内或需要特效时，解析方块状态 + 采样
                blockState = resolveBlockStateForRender(container, pos);
                if (blockState == null) {
                    continue;
                }
                long randomSeed = container.getId().getLeastSignificantBits() ^ pos.asLong();
                snapshot = previewMode
                        ? BlockAnimationEngine.samplePreview(pos, blockState, config, entry,
                                clipStart, clipEnd, currentTime, randomSeed)
                        : BlockAnimationEngine.sample(pos, blockState, config, entry,
                                clipStart, clipEnd, currentTime, randomSeed);
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

            // 动画模型独占渲染；真实网格接管时不再叠加缩放模型。
            boolean needsOverlay = !visualState.isNormal()
                    || snapshot.getAlpha() < 0.999f
                    || !isIdentityTransform(snapshot)
                    || (phase == ClipAnimationConfig.AnimationPhase.EXIT && inAnimWindow)
                    || visualState.shouldSuppressWorldRendering();

            if (!needsOverlay) {
                continue;
            }

            visualState = visualState.withSuppressWorldRendering(true);
            visualStateManager.setAnimatedBlockState(pos, visualState, container.getId(), level);
            if (staticWrites != null) staticWrites.add(new VisualWrite(pos, visualState));

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[TimelineClipBlockController] Adding render entry: pos={} alpha={}",
                        pos, snapshot.getAlpha());
            }

            // 🔥 步骤 7：创建 RenderEntry 2.0 并加入 BlockVisualStateManager
            // 关键：传入使用时间线时间采样好的 snapshot，避免渲染时用世界时间重采样
            var renderEntry = new BlockVisualStateManager.RenderEntry(
                            pos,
                            blockState,
                            visualState,
                            clip, // 🔥 timeline clip（动画需要）
                            entry, // 🔥 sequence entry（决定 normalizedTime）
                            snapshot // ✅ 传入使用时间线时间采样的 snapshot
                    );
            visualStateManager.addRenderEntry(renderEntry, container.getId(), level);
            if (staticEntries != null) staticEntries.add(renderEntry);
            registeredEntries++;
        }

        if (staticWrites != null) {
            perBlockRenderCacheByContainer.put(container.getId(), new PerBlockRenderCache(config.copy(),
                    geometryRevision, opacity, visibleBlocks, staticBucket, sectionRevision, level,
                    List.copyOf(staticWrites), List.copyOf(staticEntries)));
        }
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("[TimelineClipBlockController] registerRenderEntries completed: {} entries added",
                    registeredEntries);
        }
    }

    private void cleanupContainersNoLongerControlled(Set<UUID> activeContainerIds,
                                                     com.director.core.container.visibility.ContainerVisibilityService visibilityService,
                                                     BlockVisualStateManager visualStateManager,
                                                     Set<BlockPos> rerender,
                                                     Set<UUID> failedContainerIds) {
        Set<UUID> removed = new HashSet<>(lastFrameWorldBlocks.keySet());
        removed.addAll(lastFrameVisibleBlocks.keySet());
        removed.addAll(stableFramesByContainer.keySet());
        if (activeContainerIds != null) removed.removeAll(activeContainerIds);
        if (removed.isEmpty()) return;

        ContainerManager manager = DirectorServices.get().containers();
        for (UUID containerId : removed) {
            Container container = manager != null ? manager.getContainer(containerId) : null;
            List<BlockPos> historical = new ArrayList<>(lastFrameWorldBlocks.getOrDefault(containerId, Collections.emptySet()));
            historical.addAll(lastFrameVisibleBlocks.getOrDefault(containerId, Collections.emptySet()));
            ContainerBlocksCache cache = blocksCacheByContainer.get(container);
            if (cache != null) historical.addAll(cache.all);
            if (rerender != null) rerender.addAll(historical);

            boolean cleaned = runIsolatedContainer(containerId, () -> {
                if (visualStateManager != null) {
                    if (container != null) visualStateManager.clearOverridesFor(container);
                    else visualStateManager.clearOverridesFor(containerId); // registry-independent old-coordinate cleanup
                }
                if (TimelineSamplingContext.current().isPreview() && visibilityService != null && container != null) {
                    if (container.isHidden()) {
                        visibilityService.showContainerBlocksForTimeline(container, Collections.emptySet());
                    } else if (visibilityService.hasSnapshot(container)) {
                        visibilityService.showContainer(container);
                    }
                }
            });
            if (cleaned) {
                containersNeedingRetry.remove(containerId);
                stableFramesByContainer.remove(containerId);
                wholeRenderCacheByContainer.remove(containerId);
                perBlockRenderCacheByContainer.remove(containerId);
            } else {
                failedContainerIds.add(containerId);
                containersNeedingRetry.add(containerId);
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

    record VisibleBlocksResult(Set<BlockPos> worldBlocks, Set<BlockPos> renderBlocks,
                               List<BlockSequenceEntry> sequenceEntries) {
    }

    private void applyOcclusionOverrides(float currentTime,
                                         Map<UUID, Set<BlockPos>> currentFrameVisibleBlocks,
                                         Map<UUID, Float> currentFrameOpacity,
                                         Set<UUID> occludedContainers,
                                         Map<UUID, TimelineClip> containerToClipMap,
                                         Set<UUID> failedContainerIds) {
        TimelineModel model = resolveActiveModel();
        if (model == null) return;

        for (TimelineTrack track : model.getAllTracks()) {
            if (track == null || track.isLocked() || !track.isVisible() || !isBlockTrackType(track.getType())) {
                continue;
            }
            for (TimelineClip activeClip : track.getClips()) {
                if (activeClip == null || activeClip.getBoundContainerId() == null) continue;
                UUID activeContainerId = activeClip.getBoundContainerId();
                boolean applied = runIsolatedContainer(activeContainerId, () -> {
                    if (!activeClip.isAutoFadePreviousClips()
                            || currentTime < activeClip.getStartTime() || currentTime > activeClip.getEndTime()) {
                        return;
                    }
                    Container activeContainer = DirectorServices.get().containers().getContainer(activeContainerId);
                    if (activeContainer == null) return;
                    List<Container> candidates = collectPreviousContainers(activeClip, currentFrameVisibleBlocks);
                    if (candidates.isEmpty()) return;
                    Set<UUID> occludingBlocks = occlusionResolver.resolveOccludingContainers(activeContainer,
                            candidates);
                    if (occludingBlocks.isEmpty()) return;

                    float fadeOpacity = activeClip.getAutoFadeOpacity();
                    for (UUID containerId : occludingBlocks) {
                        boolean candidateApplied = runIsolatedContainer(containerId, () -> {
                            TimelineClip occludedClip = containerToClipMap != null ? containerToClipMap.get(containerId) : null;
                            boolean occlusionTransparencyEnabled = occludedClip == null
                                    || (occludedClip.getAnimationConfigForEditing() != null
                                    && occludedClip.getAnimationConfigForEditing().isOcclusionTransparencyEnabled());
                            if (!occlusionTransparencyEnabled) return;
                            Set<BlockPos> currentVisible = currentFrameVisibleBlocks.get(containerId);
                            if (currentVisible != null && !currentVisible.isEmpty()) {
                                currentFrameOpacity.put(containerId, fadeOpacity);
                                occludedContainers.add(containerId);
                                if (LOGGER.isTraceEnabled()) {
                                    LOGGER.trace("[Occlusion] clip={} 强制淡化容器 {} (透明度 {}), 标记为需要隐藏世界方块",
                                            activeClip.getLabel(), containerId, fadeOpacity);
                                }
                            }
                        });
                        if (!candidateApplied) {
                            failedContainerIds.add(containerId);
                            containersNeedingRetry.add(containerId);
                        }
                    }
                });
                if (!applied) {
                    failedContainerIds.add(activeContainerId);
                    containersNeedingRetry.add(activeContainerId);
                }
            }
        }
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
        if (!TimelineSamplingContext.current().isPreview()) {
            throw new IllegalStateException("非预览采样要求容器已绑定世界：" + container.getName());
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
        if (container == null || visibilityService == null
                || !TimelineSamplingContext.current().allowsWorldMutation()) {
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

            // Preparation is server-owned and bounded; never copy a large snapshot on the render thread.
            visibilityService.prepareContainerForTimeline(container);

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
