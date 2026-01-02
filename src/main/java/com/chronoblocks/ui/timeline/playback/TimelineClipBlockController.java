package com.chronoblocks.ui.timeline.playback;

import com.chronoblocks.core.animation.BlockAnimationEngine;
import com.chronoblocks.core.animation.BlockAnimationSnapshot;
import com.chronoblocks.core.animation.ClipAnimationConfig;
import com.chronoblocks.core.animation.sequence.BlockSequenceEntry;
import com.chronoblocks.core.animation.sequence.BlockSequenceGenerator;
import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;
import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.TrackType;
import com.chronoblocks.core.container.visibility.BlockVisualStateManager;
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
 * <p>根据播放头位置和片段配置控制片段对应容器的方块在Minecraft中的显示/隐藏。
 * 
 * <p>◆ 核心设计原则：片段完全独立于容器状态
 * <ul>
 *   <li>片段拥有独立的动画配置（ClipAnimationConfig），不依赖容器的状态</li>
 *   <li>方块出现完全按照片段的配置（动画模式、序列顺序、缓动曲线等）</li>
 *   <li>容器的状态（VISIBLE/HIDDEN）不影响片段的动画控制</li>
 *   <li>片段通过 BlockVisualStateManager 控制方块显示，而不是直接修改容器状态</li>
 * </ul>
 * 
 * <p>重要约束：
 * <ul>
 *   <li>每个容器有且只有一个片段</li>
 *   <li>同一个容器只能在轨道上产生一个片段</li>
 * </ul>
 * 
 * <p>方块出现规则（完全基于片段配置）：
 * <ul>
 *   <li>播放头在片段左侧 → 隐藏所有方块</li>
 *   <li>播放头在片段范围内 → 根据片段的动画配置（AnimationMode、SequenceParameters）逐个显示方块</li>
 *   <li>播放头在片段右侧 → 显示所有方块（完全显示）</li>
 *   <li>轨道锁定或隐藏 → 强制隐藏方块（覆盖片段配置）</li>
 * </ul>
 * 
 * <p>动画配置来源：
 * <ul>
 *   <li>片段创建时：从容器的 {@link com.chronoblocks.core.container.ContainerAnimationPreset} 初始化</li>
 *   <li>片段编辑时：通过 {@link com.chronoblocks.ui.properties.adapters.TimelineClipPropertyAdapter} 修改</li>
 *   <li>运行时：完全基于片段的 {@link com.chronoblocks.core.animation.ClipAnimationConfig}</li>
 * </ul>
 */
public class TimelineClipBlockController {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineClipBlockController.class);
    
    private static TimelineClipBlockController instance;
    private TimelineModel timelineModel;
    
    // 不再需要旧的AnimationEngineFactory，改用BlockSequenceGenerator

    // 简化遮挡检测器
    private final BlockOcclusionResolver occlusionResolver = new BlockOcclusionResolver();

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

    private final Map<Container, ContainerBlocksCache> blocksCacheByContainer = new IdentityHashMap<>();
    private final Map<TimelineClip, ClipSequenceCache> sequenceCacheByClip = new IdentityHashMap<>();

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
            // container.getBlocks() 本身会复制一次 set；这里保证只在容器变更时做。
            cache.blocks = new ArrayList<>(container.getBlocks());
        }
        return cache.blocks != null ? cache.blocks : List.of();
    }

    private static int hashSequenceParameters(com.chronoblocks.core.animation.sequence.SequenceParameters p) {
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
                totalDelay
        );

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

    private static boolean isBlockTrackType(TrackType trackType) {
        return trackType == TrackType.CONTAINER
                || trackType == TrackType.ERASE;
    }

    /**
     * 记录每个容器上一帧“应当显示/保留”的方块集合。
     *
     * <p>用于推导本帧世界方块状态的变化量，并触发区块重建，
     * 避免在逐步清除时出现“黑洞/虚空（区块未及时重渲染）”的视觉问题。
     */
    private final Map<UUID, Set<BlockPos>> lastFrameVisibleBlocks = new HashMap<>();

    /**
     * 上一帧“世界中真实存在”的方块集合（用于驱动 showContainerBlocks 与区块重渲染）。
     *
     * <p>与 {@link #lastFrameVisibleBlocks} 的语义不同：
     * <ul>
     *   <li>lastFrameVisibleBlocks：用于渲染/视觉（包含 ENTER 动画窗口内的 overlay 方块）</li>
     *   <li>lastFrameWorldBlocks：用于世界落地（ENTER 仅在动画结束后才进入世界，避免与动画重叠）</li>
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
    }
    
    /**
     * 更新所有片段对应容器的方块显示状态（每帧调用）
     * 
     * <p>◆ 核心逻辑：完全基于片段配置，不依赖容器状态
     * <ul>
     *   <li>遍历所有片段，根据播放头位置和片段配置计算应该显示的方块</li>
     *   <li>使用 BlockVisualStateManager 控制方块显示，而不是直接修改容器状态</li>
     *   <li>容器的状态（VISIBLE/HIDDEN）不影响片段的动画控制</li>
     * </ul>
     * 
     * <p>注意：每个容器有且只有一个片段，所以逻辑可以简化
     */
    public void update() {
        if (timelineModel == null) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[TimelineClipBlockController] timelineModel 为空，跳过更新");
            }
            return;
        }

        final long t0 = System.nanoTime();
        
        float currentTime = timelineModel.getCurrentTime();
        BlockVisualStateManager visualStateManager = BlockVisualStateManager.getInstance();
        
        // 🔥 步骤 0：清空上一帧的 renderEntries（确保每帧都是全新的）
        visualStateManager.clearFrame(); // 这会清空 visualOverrides 和 renderEntries
        
        // currentFrameVisibleBlocks：保持为“渲染可见集合”（用于遮挡/overlay/视觉状态）
        Map<UUID, Set<BlockPos>> currentFrameVisibleBlocks = new HashMap<>();
        // currentFrameWorldBlocks：用于真实世界方块落地（showContainerBlocks）
        Map<UUID, Set<BlockPos>> currentFrameWorldBlocks = new HashMap<>();
        Map<UUID, Float> currentFrameOpacity = new HashMap<>();
        Map<UUID, List<BlockSequenceEntry>> currentFrameSequences = new HashMap<>();
        Map<UUID, ClipAnimationConfig> currentFrameConfigs = new HashMap<>();

        // 本帧世界方块（AIR/恢复）变化导致的重渲染位置（与视觉覆盖层无关）
        Set<BlockPos> worldStateChangedBlocksForRerender = new HashSet<>();
        
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("[TimelineClipBlockController] 开始更新 (当前时间: {:.2f}s)", currentTime);
        }
        
        final long tAfterClear = System.nanoTime();

        // ◆ 步骤1：遍历所有轨道和片段，计算每个容器在当前时间应该显示的方块和其默认透明度
        for (TimelineTrack track : timelineModel.getAllTracks()) {
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
                Container container = Objects.requireNonNull(ContainerManager.getInstance()).getContainer(containerId);
                if (container == null) {
                    continue;
                }

                ClipAnimationConfig config = clip.getAnimationConfig();
                if (config == null) {
                    config = ClipAnimationConfig.defaultConfig();
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("[TimelineClipBlockController] Clip {} animation config missing, using default", clip.getLabel());
                    }
                }
                // ✅ 兼容旧模式：把“随机/螺旋/切片/波浪/扩散/向下生长”等迁移到 SequenceParameters
                com.chronoblocks.core.animation.LegacyAnimationModeMigrator.migrateIfNeeded(config);
                // ✅ 兼容旧高级参数：例如 flashWhite -> EffectInstance
                com.chronoblocks.core.animation.LegacyAdvancedParamsMigrator.migrateIfNeeded(config);
                currentFrameConfigs.put(containerId, config);
                
                // ◆ 根据播放头位置和片段配置计算应该显示的方块
                // 完全基于片段的ClipAnimationConfig，不依赖容器状态
                VisibleBlocksResult result = calculateVisibleBlocks(clip, trackType, currentTime, trackEnabled, container, config);
                Set<BlockPos> renderBlocks = result.renderBlocks();
                Set<BlockPos> worldBlocks = result.worldBlocks();
                currentFrameVisibleBlocks.put(containerId, renderBlocks);
                currentFrameWorldBlocks.put(containerId, worldBlocks);
                currentFrameOpacity.put(containerId, clip.getOpacity()); // 使用片段的透明度
                currentFrameSequences.put(containerId, result.sequenceEntries());
                
                if (LOGGER.isTraceEnabled() && !renderBlocks.isEmpty()) {
                    LOGGER.trace("[TimelineClipBlockController] 片段 {} (容器: {}) render={} world={} (时间: {:.2f}s, 片段范围: [{:.2f}s, {:.2f}s])",
                        clip.getLabel(), container.getName(), renderBlocks.size(), worldBlocks.size(), currentTime, clip.getStartTime(), clip.getEndTime());
                }
            }
        }
        final long tAfterVisible = System.nanoTime();

        // ✅ 步骤1.5：根据“上一帧可见集 vs 当前帧可见集”推导世界方块变化量，提前为区块重建收集 pos
        // 说明：showContainerBlocks 会真实修改世界方块（置 AIR / 从快照恢复），但客户端区块重建可能滞后。
        // 这里通过 scheduleBlockRenders 主动触发重渲染，避免清除过程出现“黑洞/虚空”。
        // 世界落地集合变化（用于 showContainerBlocks 与区块重渲染）
        Map<UUID, Boolean> worldSetChangedByContainer = new HashMap<>();
        for (Map.Entry<UUID, Set<BlockPos>> entry : currentFrameWorldBlocks.entrySet()) {
            UUID containerId = entry.getKey();
            Set<BlockPos> now = entry.getValue() != null ? entry.getValue() : java.util.Collections.emptySet();
            Set<BlockPos> prev = lastFrameWorldBlocks.get(containerId);
            if (prev == null) {
                // 第一次看到该容器：保守起见，认为整组都可能变化
                worldSetChangedByContainer.put(containerId, true);
                Container container = Objects.requireNonNull(ContainerManager.getInstance()).getContainer(containerId);
                if (container != null) {
                    worldStateChangedBlocksForRerender.addAll(getCachedContainerBlocks(container));
                } else {
                    worldStateChangedBlocksForRerender.addAll(now);
                }
                continue;
            }
            // 计算对称差（发生显示/隐藏切换的 pos）
            boolean changed = false;
            for (BlockPos p : prev) {
                if (!now.contains(p)) {
                    changed = true;
                    worldStateChangedBlocksForRerender.add(p);
                    // 邻居面也会被暴露/遮挡，加入 6 邻居以提高稳定性
                    worldStateChangedBlocksForRerender.add(p.up());
                    worldStateChangedBlocksForRerender.add(p.down());
                    worldStateChangedBlocksForRerender.add(p.north());
                    worldStateChangedBlocksForRerender.add(p.south());
                    worldStateChangedBlocksForRerender.add(p.east());
                    worldStateChangedBlocksForRerender.add(p.west());
                }
            }
            for (BlockPos p : now) {
                if (!prev.contains(p)) {
                    changed = true;
                    worldStateChangedBlocksForRerender.add(p);
                    worldStateChangedBlocksForRerender.add(p.up());
                    worldStateChangedBlocksForRerender.add(p.down());
                    worldStateChangedBlocksForRerender.add(p.north());
                    worldStateChangedBlocksForRerender.add(p.south());
                    worldStateChangedBlocksForRerender.add(p.east());
                    worldStateChangedBlocksForRerender.add(p.west());
                }
            }
            worldSetChangedByContainer.put(containerId, changed);
        }
        // 渲染可见集合变化（用于 BlockVisualStateManager 的增量更新）
        Map<UUID, Boolean> renderSetChangedByContainer = new HashMap<>();
        for (Map.Entry<UUID, Set<BlockPos>> entry : currentFrameVisibleBlocks.entrySet()) {
            UUID containerId = entry.getKey();
            Set<BlockPos> now = entry.getValue() != null ? entry.getValue() : java.util.Collections.emptySet();
            Set<BlockPos> prev = lastFrameVisibleBlocks.get(containerId);
            if (prev == null) {
                renderSetChangedByContainer.put(containerId, true);
                continue;
            }

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
        
        // ◆ 步骤2：应用遮挡覆盖规则，调整透明度并标记需要隐藏世界方块的容器
        // 构建容器ID到片段的映射，便于后续查询
        Map<UUID, TimelineClip> containerToClipMap = new HashMap<>();
        for (TimelineTrack track : timelineModel.getAllTracks()) {
            if (track == null) continue;
            if (!isBlockTrackType(track.getType())) {
                continue;
            }
            for (TimelineClip clip : track.getClips()) {
                if (clip != null && clip.getBoundContainerId() != null) {
                    containerToClipMap.put(clip.getBoundContainerId(), clip);
                }
            }
        }

        // ✅ 步骤2.1：应用遮挡覆盖规则，找出被遮挡的容器
        Set<UUID> occludedContainers = new HashSet<>(); // 被遮挡容器的ID集合
        applyOcclusionOverrides(currentTime, currentFrameVisibleBlocks, currentFrameOpacity, occludedContainers, containerToClipMap);
        final long tAfterOcclusion = System.nanoTime();

        // ✅ 步骤3：应用方块可见性状态（两层系统）
        // 双重控制机制：
        // 1. 通过 ContainerVisibilityService 控制真实方块显示/隐藏
        // 2. 通过 BlockVisualStateManager 控制视觉状态（透明度、覆盖层等）
        var visibilityService = com.chronoblocks.core.container.visibility.ContainerServices.visibility();

        for (Map.Entry<UUID, Set<BlockPos>> entry : currentFrameVisibleBlocks.entrySet()) {
            UUID containerId = entry.getKey();
            // renderBlocks：用于视觉状态/overlay/动画采样
            Set<BlockPos> renderBlocks = entry.getValue();
            // worldBlocks：用于真实世界方块落地（ENTER 延后到动画结束）
            Set<BlockPos> worldBlocks = currentFrameWorldBlocks.getOrDefault(containerId, java.util.Collections.emptySet());
            float opacity = currentFrameOpacity.getOrDefault(containerId, 1.0f);
            
            Container container = Objects.requireNonNull(ContainerManager.getInstance()).getContainer(containerId);
            if (container == null) {
                continue;
            }

            boolean worldSetChanged = worldSetChangedByContainer.getOrDefault(containerId, true);
            boolean renderSetChanged = renderSetChangedByContainer.getOrDefault(containerId, true);
            long containerMod = container.getModifiedTime();
            LastVisualParams lastParams = lastVisualParamsByContainerId.get(containerId);
            boolean containerStructureChanged = (lastParams == null) || (lastParams.containerModifiedTime != containerMod);
            
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
                
                // 性能关键：只有“可见集合发生变化”时才去真正修改世界方块（置 AIR / 恢复）。
                // 否则每帧调用 showContainerBlocks 会导致大量区块重建与 CPU 开销。
                if (worldSetChanged || containerStructureChanged) {
                    if (worldBlocks.isEmpty()) {
                        // 如果没有方块可见：需要确保容器在世界中被清空（置 AIR）
                        // （清除轨道必须执行这一点；容器轨道也允许显式清空）
                        visibilityService.showContainerBlocks(container, java.util.Collections.emptySet());
                    } else {
                        // 有方块应该显示，使用 showContainerBlocks 恢复这些方块
                        // 即使容器状态是 HIDDEN，这个方法也能部分显示方块
                        // 🔹 注意：对于被遮挡的容器，方块会恢复存在，但通过 Mixin 阻止 Minecraft 渲染
                        visibilityService.showContainerBlocks(container, worldBlocks);

                        if (LOGGER.isTraceEnabled()) {
                            LOGGER.trace("[TimelineClipBlockController] 容器 {} 显示 {} 个方块(时间: {:.2f}s, 遮挡: {})",
                                    container.getName(), worldBlocks.size(), currentTime, isOccluded);
                        }
                    }
                }
            } else {
                LOGGER.warn("[TimelineClipBlockController] ContainerVisibilityService 未初始化，无法控制方块显示");
            }
            
            // ◆ 然后设置视觉状态（用于半透明效果和遮挡淡化）
            // 性能关键：避免每帧对全量 blocks/allBlocks 调 setBlocksState。
            // - 可见集合变化：只处理新增/移除的方块
            // - 视觉参数变化：对当前可见集做一次更新（通常很少发生）
            Set<BlockPos> prevVisible = lastFrameVisibleBlocks.getOrDefault(containerId, java.util.Collections.emptySet());

            if (containerStructureChanged) {
                // 容器本身被编辑过：做一次全量同步（但仍避免每帧做）
                List<BlockPos> all = getCachedContainerBlocks(container);
                if (renderBlocks == null || renderBlocks.isEmpty()) {
                    visualStateManager.setBlocksState(all, BlockVisualStateManager.BlockVisualState.hidden());
                } else {
                    // 先把所有设为隐藏，再把可见集覆盖为目标状态（减少漏网）
                    visualStateManager.setBlocksState(all, BlockVisualStateManager.BlockVisualState.hidden());
                    // 说明：当半透明且不写深度时，方块自身的前后面会叠加绘制，视觉上容易“更深/更暗”。
                    // 这里统一改为写深度（GHOST_WITH_DEPTH），让动画/遮挡期间的颜色更接近最终落地后的世界渲染。
                    BlockVisualStateManager.BlockVisualState visibleState =
                            (opacity < 1.0f)
                                    ? BlockVisualStateManager.BlockVisualState.ghost(opacity, true, true).withSuppressWorldRendering(true)
                                    : BlockVisualStateManager.BlockVisualState.normal();
                    visualStateManager.setBlocksState(renderBlocks, visibleState);
                }
            } else {
                if (renderSetChanged) {
                    // removed = prev - now
                    if (prevVisible != null && !prevVisible.isEmpty()) {
                        Set<BlockPos> removed = new HashSet<>(prevVisible);
                        if (renderBlocks != null && !renderBlocks.isEmpty()) {
                            removed.removeAll(renderBlocks);
                        }
                        if (!removed.isEmpty()) {
                            visualStateManager.setBlocksState(removed, BlockVisualStateManager.BlockVisualState.hidden());
                        }
                    }

                    // added = now - prev
                    if (renderBlocks != null && !renderBlocks.isEmpty()) {
                        Set<BlockPos> added = new HashSet<>(renderBlocks);
                        if (prevVisible != null && !prevVisible.isEmpty()) {
                            added.removeAll(prevVisible);
                        }
                        if (!added.isEmpty()) {
                            BlockVisualStateManager.BlockVisualState visibleState =
                                    (opacity < 1.0f)
                                            ? BlockVisualStateManager.BlockVisualState.ghost(opacity, true, true).withSuppressWorldRendering(true)
                                            : BlockVisualStateManager.BlockVisualState.normal();
                            visualStateManager.setBlocksState(added, visibleState);
                        }
                    }
                }

                if (visualParamsChanged) {
                    // 参数变化：把当前可见集整体更新一次
                    if (renderBlocks != null && !renderBlocks.isEmpty()) {
                        BlockVisualStateManager.BlockVisualState visibleState =
                                (opacity < 1.0f)
                                        ? BlockVisualStateManager.BlockVisualState.ghost(opacity, true, true).withSuppressWorldRendering(true)
                                        : BlockVisualStateManager.BlockVisualState.normal();
                        visualStateManager.setBlocksState(renderBlocks, visibleState);
                    }
                }
            }

            // 记录本帧视觉参数（用于下一帧增量）
            if (lastParams == null) {
                lastParams = new LastVisualParams();
                lastVisualParamsByContainerId.put(containerId, lastParams);
            }
            lastParams.opacity = opacity;
            lastParams.occluded = isOccluded;
            lastParams.containerModifiedTime = containerMod;

            TimelineClip playbackClip = containerToClipMap.get(containerId);
            ClipAnimationConfig playbackConfig = currentFrameConfigs.get(containerId);
            List<BlockSequenceEntry> sequenceEntries = currentFrameSequences.getOrDefault(containerId, Collections.emptyList());
            registerRenderEntriesForContainer(
                    visualStateManager,
                    container,
                    playbackClip,
                    playbackConfig,
                    sequenceEntries,
                    currentTime,
                    renderBlocks
            );
        }
        final long tAfterEntries = System.nanoTime();

        // 触发需要的区块重渲染：
        // 1) visualState 的 suppressWorldRendering/overlay 变化
        // 2) 世界方块真实变化（AIR/恢复）导致的面更新（防止“黑洞/虚空”）
        Set<BlockPos> rerender = new HashSet<>();
        try {
            Set<BlockPos> vsChanged = visualStateManager.getChangedBlocks();
            if (vsChanged != null && !vsChanged.isEmpty()) {
                rerender.addAll(vsChanged);
            }
        } catch (Exception ignored) {
        }
        if (!worldStateChangedBlocksForRerender.isEmpty()) {
            rerender.addAll(worldStateChangedBlocksForRerender);
        }
        scheduleBlockRerender(rerender);
        final long tAfterRerender = System.nanoTime();

        // 更新 lastFrameVisibleBlocks（渲染可见集）与 lastFrameWorldBlocks（世界落地集），并清理已不存在的容器
        lastFrameVisibleBlocks.keySet().retainAll(currentFrameVisibleBlocks.keySet());
        lastFrameWorldBlocks.keySet().retainAll(currentFrameWorldBlocks.keySet());
        lastVisualParamsByContainerId.keySet().retainAll(currentFrameVisibleBlocks.keySet());
        for (Map.Entry<UUID, Set<BlockPos>> e : currentFrameVisibleBlocks.entrySet()) {
            UUID id = e.getKey();
            Set<BlockPos> s = e.getValue();
            // 性能关键：避免每帧复制大 Set；该 Set 在本帧之后不再被修改，直接复用引用即可。
            lastFrameVisibleBlocks.put(id, s != null ? s : java.util.Collections.emptySet());
        }
        for (Map.Entry<UUID, Set<BlockPos>> e : currentFrameWorldBlocks.entrySet()) {
            UUID id = e.getKey();
            Set<BlockPos> s = e.getValue();
            lastFrameWorldBlocks.put(id, s != null ? s : java.util.Collections.emptySet());
        }
        visualStateManager.finishFrame();

        // ===== 节流性能日志（每 5 秒一次）=====
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastPerfLogMs > 5000L) {
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

            LOGGER.warn("[Perf][TimelineClipBlockController] total={}ms (clear={}ms, step1_visible={}ms, diff={}ms, occlusion={}ms, step3_apply+entries={}ms, rerender={}ms) containers={}, visibleTotal={}, seqTotal={}, renderEntries={}",
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
                    renderEntryCount
            );
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
                if (pos == null) continue;
                int cx = pos.getX() >> 4;
                int cz = pos.getZ() >> 4;
                long key = (((long) cx) << 32) ^ (cz & 0xffffffffL);
                ChunkBox box = byChunk.computeIfAbsent(key, k -> new ChunkBox());
                box.include(pos);
            }

            // 每个 chunk 单独调度（把范围扩到 chunk 边界，避免边缘面不刷新）
            for (Map.Entry<Long, ChunkBox> e : byChunk.entrySet()) {
                ChunkBox box = e.getValue();
                if (box == null || !box.valid()) continue;

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
     * <p>容器轨道（CONTAINER）规则：
     * <ul>
     *   <li>片段左侧：空（全部隐藏）</li>
     *   <li>片段范围：逐步显示</li>
     *   <li>片段右侧：全量显示</li>
     * </ul>
     *
     * <p>清除轨道（ERASE）规则（反向）：
     * <ul>
     *   <li>片段左侧：全量保留（不清除）</li>
     *   <li>片段范围：逐步清除（从全量到空）</li>
     *   <li>片段右侧：空（全部清除）</li>
     * </ul>
     *
     * <p>轨道锁定/隐藏处理：
     * <ul>
     *   <li>容器轨道：强制隐藏</li>
     *   <li>清除轨道：强制不清除（保留全量）</li>
     * </ul>
     */
    private VisibleBlocksResult calculateVisibleBlocks(TimelineClip clip,
                                                       TrackType trackType,
                                                       float currentTime,
                                                       boolean trackEnabled,
                                                       Container container,
                                                       ClipAnimationConfig config) {
        // renderBlocks：用于“视觉可见”（overlay/动画采样/遮挡）
        Set<BlockPos> renderBlocks = new HashSet<>();
        // worldBlocks：用于“真实落地到世界”（showContainerBlocks）
        Set<BlockPos> worldBlocks = new HashSet<>();
        List<BlockSequenceEntry> sequenceEntries = List.of();

        TrackType type = trackType != null ? trackType : TrackType.CONTAINER;

        List<BlockPos> allBlocks = getCachedContainerBlocks(container);
        if (allBlocks.isEmpty()) {
            return new VisibleBlocksResult(worldBlocks, renderBlocks, sequenceEntries);
        }

        // 轨道禁用：容器轨道 => 隐藏；清除轨道 => 不清除（保留全量）
        if (!trackEnabled) {
            if (type == TrackType.ERASE) {
                renderBlocks.addAll(allBlocks);
                worldBlocks.addAll(allBlocks);
            }
            return new VisibleBlocksResult(worldBlocks, renderBlocks, sequenceEntries);
        }

        float clipStart = clip.getStartTime();
        float clipEnd = clip.getEndTime();

        // 片段左侧
        if (currentTime < clipStart) {
            if (type == TrackType.ERASE) {
                renderBlocks.addAll(allBlocks);
                worldBlocks.addAll(allBlocks);
            }
            return new VisibleBlocksResult(worldBlocks, renderBlocks, sequenceEntries);
        }

        // 片段范围内：按序列推进
        if (currentTime >= clipStart && currentTime <= clipEnd) {
            float clipDuration = clipEnd - clipStart;
            float animDuration = clipDuration;
            float delay = 0.0f;
            if (config != null) {
                animDuration = Math.min(Math.max(0.05f, config.getDuration()), clipDuration);
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

            sequenceEntries = getOrGenerateSequenceEntries(
                    clip,
                    container,
                    config,
                    clipDuration,
                    animDuration,
                    delay
            );

            if (sequenceEntries == null || sequenceEntries.isEmpty()) {
                // 后备：序列为空时，直接视为全量
                renderBlocks.addAll(allBlocks);
                worldBlocks.addAll(allBlocks);
                return new VisibleBlocksResult(worldBlocks, renderBlocks, sequenceEntries);
            }

            if (type == TrackType.CONTAINER) {
                // 逐步显示（ENTER）：
                // - renderBlocks：从 blockStartTime 起可见（用于 overlay 动画）
                // - worldBlocks ：到 blockEndTime 才落地（避免真方块提前出现在目标格）
                boolean isInstant = false;
                try {
                    isInstant = config != null
                            && config.getMode() == com.chronoblocks.core.container.AnimationMode.INSTANT;
                } catch (Exception ignored) {
                }
                for (BlockSequenceEntry entry : sequenceEntries) {
                    float normalizedTime = entry.getNormalizedTime();
                    float blockStartTime = clipStart + delay + normalizedTime * sequenceSpan;
                    float blockEndTime = Math.min(clipEnd, blockStartTime + animDuration);

                    if (currentTime >= blockStartTime) {
                        renderBlocks.add(entry.getPos());
                    }
                    if (isInstant) {
                        if (currentTime >= blockStartTime) {
                            worldBlocks.add(entry.getPos());
                        }
                    } else {
                        if (currentTime >= blockEndTime) {
                            worldBlocks.add(entry.getPos());
                        }
                    }
                }
            } else if (type == TrackType.ERASE) {
                // 逐步清除（带退出动画窗口）：
                // - 每个方块在自己的 EXIT 动画结束时刻被移除（即从世界中消失/置 AIR）
                // - 在此之前一直保留在 visible 集合中，供 BlockAnimationEngine 采样退出动画
                renderBlocks.addAll(allBlocks); // 先全量保留，再按“结束时刻”移除
                worldBlocks.addAll(allBlocks);
                for (BlockSequenceEntry entry : sequenceEntries) {
                    float normalizedTime = entry.getNormalizedTime();
                    // 与 BlockAnimationEngine 的 EXIT 时间窗口对齐：结束时刻分布在 [clipStart+delay+animDuration, clipEnd]
                    float blockEndTime = clipStart + delay + (fullWindow ? animDuration : 0.0f) + normalizedTime * sequenceSpan;
                    if (currentTime >= blockEndTime) {
                        renderBlocks.remove(entry.getPos());
                        worldBlocks.remove(entry.getPos());
                    }
                }
            } else {
                // 其他类型（理论上不会到这里）：默认全量
                renderBlocks.addAll(allBlocks);
                worldBlocks.addAll(allBlocks);
            }

            return new VisibleBlocksResult(worldBlocks, renderBlocks, sequenceEntries);
        }

        // 片段右侧
        if (currentTime > clipEnd) {
            if (type == TrackType.CONTAINER) {
                renderBlocks.addAll(allBlocks);
                worldBlocks.addAll(allBlocks);
            } else if (type == TrackType.ERASE) {
                // 全清除 => 保持空集合
            }
        }

        return new VisibleBlocksResult(worldBlocks, renderBlocks, sequenceEntries);
    }

    /**
     * 🔥 为容器注册所有需要渲染的方块（RenderEntry 2.0）
     * 
     * <p>核心逻辑：
     * <ol>
     *   <li>遍历当前片段的所有 BlockSequenceEntry</li>
     *   <li>为每个可见的 block 生成 RenderEntry 2.0（包含 clip、seqEntry 和 snapshot）</li>
     *   <li>使用时间线时间采样动画 snapshot（不是世界时间）</li>
     *   <li>将 snapshot 作为 overrideSnapshot 传入 RenderEntry</li>
     *   <li>加入 BlockVisualStateManager</li>
     * </ol>
     * 
     * <p>关键修复：snapshot 在此处使用时间线时间（currentTime）生成，
     * 并作为 overrideSnapshot 传入 RenderEntry，避免渲染时用世界时间重采样导致动画错误。
     */
    private void registerRenderEntriesForContainer(
            BlockVisualStateManager visualStateManager,
            Container container,
            TimelineClip clip,
            ClipAnimationConfig config,
            List<BlockSequenceEntry> sequenceEntries,
            float currentTime,
            Set<BlockPos> visibleBlocks
    ) {
        if (visualStateManager == null || container == null || clip == null || config == null) {
            return;
        }
        if (visibleBlocks == null || visibleBlocks.isEmpty()) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[TimelineClipBlockController] registerRenderEntries: visibleBlocks is empty for container {}",
                        container.getName());
            }
            return;
        }
        if (sequenceEntries == null || sequenceEntries.isEmpty()) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[TimelineClipBlockController] registerRenderEntries: sequenceEntries is empty for container {}",
                        container.getName());
            }
            return;
        }

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("[TimelineClipBlockController] registerRenderEntries: container={} visibleBlocks={} entries={}",
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
                blockEndTime = clipStart + delay + (fullWindow ? animDuration : 0.0f) + seqNormalizedTime * sequenceSpan;
                blockStartTime = blockEndTime - animDuration;
            } else {
                blockStartTime = clipStart + delay + seqNormalizedTime * sequenceSpan;
                blockEndTime = blockStartTime + animDuration;
            }
            if (blockStartTime < clipStart) blockStartTime = clipStart;
            if (blockEndTime > clipEnd) blockEndTime = clipEnd;

            boolean inAnimWindow = currentTime >= blockStartTime && currentTime < blockEndTime;

            // ENTER：未到开始时间，理论上不应在 visibleBlocks 中；保险跳过
            if (phase != ClipAnimationConfig.AnimationPhase.EXIT && currentTime < blockStartTime) {
                continue;
            }
            // EXIT：超过 endTime 且非瞬时，视为已退出
            if (phase == ClipAnimationConfig.AnimationPhase.EXIT
                    && currentTime >= blockEndTime
                    && config.getMode() != com.chronoblocks.core.container.AnimationMode.INSTANT) {
                continue;
            }

            BlockState blockState = null;
            BlockAnimationSnapshot snapshot;
            if (!inAnimWindow) {
                // 静态块：如果视觉状态是 NORMAL 且不抑制世界渲染，完全不需要 overlay / snapshot
                if (visualState.isNormal() && !visualState.shouldSuppressWorldRendering()) {
                    continue;
                }

                // 🔥 步骤 3：静态但需要 overlay，才解析方块状态（避免每帧对海量静态块做 world.getBlockState）
                blockState = resolveBlockStateForRender(container, pos);
                if (blockState == null) {
                    continue;
                }

                // 视觉状态需要 overlay（遮挡/透明）：构造轻量 snapshot（不走 sample）
                snapshot = new BlockAnimationSnapshot(pos, blockState);
                snapshot.markFullyVisible();
                snapshot.setSequenceNormalizedTime(seqNormalizedTime);
                snapshot.setTransform(null); // null = identity
            } else {
                // 🔥 步骤 3：动画窗口内才解析方块状态 + 采样
                blockState = resolveBlockStateForRender(container, pos);
                if (blockState == null) {
                    continue;
                }
                // 动画窗口内：才进行真实采样
                long randomSeed = container.getId().getLeastSignificantBits() ^ pos.asLong();
                snapshot = BlockAnimationEngine.sample(
                        pos,
                        blockState,
                        config,
                        entry,
                        clipStart,
                        clipEnd,
                        currentTime,  // ✅ 使用时间线时间，不是世界时间
                        randomSeed
                );
            }
            
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[TimelineClipBlockController] temp snapshot: pos={} visible={} alpha={} hasTransform={}",
                        pos, snapshot.isVisible(),
                        snapshot.getAlpha(),
                        snapshot.getTransform() != null);
            }
            
            if (!snapshot.isVisible() || snapshot.getAlpha() <= 0.0f) {
                continue;
            }

            // 🔥 步骤 5：检查是否需要覆盖层渲染
            // 需要 overlay 的情况：
            // - 视觉状态不是 NORMAL（有透明度或遮挡）
            // - 动画 alpha < 0.999（有透明度变化）
            // - 有非单位变换（有动画变换）
            boolean needsOverlay = !visualState.isNormal()
                    || snapshot.getAlpha() < 0.999f
                    || !isIdentityTransform(snapshot);

            if (!needsOverlay) {
                continue;
            }

            // 🔥 步骤 6：确保视觉状态抑制世界渲染
            if (!visualState.shouldSuppressWorldRendering()) {
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
                            clip,      // 🔥 timeline clip（动画需要）
                            entry,     // 🔥 sequence entry（决定 normalizedTime）
                            snapshot   // ✅ 传入使用时间线时间采样的 snapshot
                    )
            );
        }
        
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("[TimelineClipBlockController] registerRenderEntries completed: {} entries added",
                    sequenceEntries.stream().filter(e -> e != null && visibleBlocks.contains(e.getPos())).count());
        }
    }

    /**
     * 检查 snapshot 的变换是否是单位变换（无动画）
     * 
     * <p>通过逐元素比较矩阵与单位矩阵，允许一定的浮点精度误差。
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

    private record VisibleBlocksResult(Set<BlockPos> worldBlocks, Set<BlockPos> renderBlocks, List<BlockSequenceEntry> sequenceEntries) { }
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
            Container activeContainer = Objects.requireNonNull(ContainerManager.getInstance()).getContainer(activeContainerId);
            if (activeContainer == null) {
                continue;
            }

            List<Container> candidates = collectPreviousContainers(activeClip, currentFrameVisibleBlocks);
            if (candidates.isEmpty()) {
                continue;
            }

            Map<UUID, Set<BlockPos>> occludingBlocks = occlusionResolver.resolveOccludingBlocks(activeContainer, candidates);
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
        List<TimelineClip> active = new ArrayList<>();
        for (TimelineTrack track : timelineModel.getAllTracks()) {
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

    private List<Container> collectPreviousContainers(TimelineClip activeClip, Map<UUID, Set<BlockPos>> currentFrameVisibleBlocks) {
        List<Container> result = new ArrayList<>();
        float activeStart = activeClip.getStartTime();

        for (TimelineTrack track : timelineModel.getAllTracks()) {
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
                Container container = Objects.requireNonNull(ContainerManager.getInstance()).getContainer(clip.getBoundContainerId());
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
     * <p>如果容器没有绑定世界，尝试从客户端获取当前世界并绑定
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
     * <p>如果容器在ContainerVisibilityService 中没有快照，但有方块数据，
     * 需要先创建快照。
     * 
     * <p>策略：
     * <ol>
     *   <li>优先使用容器的 blockStore（如果已有数据），因为它是创建容器时捕获的原始状态</li>
     *   <li>如果容器的 blockStore 为空，调用 hideContainer 从世界中捕获（但此时方块可能已经是 AIR）</li>
     * </ol>
     * 
     * @param container 容器对象
     * @param visibilityService 可见性服务
     */
    private void ensureContainerSnapshot(Container container,
                                         com.chronoblocks.core.container.visibility.ContainerVisibilityService visibilityService,
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
                // ◆ 策略2：容器的 blockStore 为空，调用 hideContainer 从世界中捕获
                // 注意：如果容器已经是 HIDDEN 状态，方块可能已经是 AIR，此时捕获的是 AIR 状态
                visibilityService.hideContainer(container);
                
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[TimelineClipBlockController] 容器 {} 已通过 hideContainer 创建快照 (方块数: {})",
                        container.getName(), container.getBlockCount());
                }
            }
            
            // 轨道差异：
            // - 容器轨道：容器应保持 HIDDEN（世界方块由 showContainerBlocks 控制出现）
            // - 清除轨道：容器不应被强制隐藏，否则会改变编辑预期
            TrackType type = trackType != null ? trackType : TrackType.CONTAINER;
            if (type == TrackType.CONTAINER) {
                if (container.getState() != com.chronoblocks.core.container.ContainerState.HIDDEN) {
                    container.setState(com.chronoblocks.core.container.ContainerState.HIDDEN);
                }
            }
        }
    }
    

}
