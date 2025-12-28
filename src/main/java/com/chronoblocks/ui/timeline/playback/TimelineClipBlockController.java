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

    /**
     * 记录每个容器上一帧“应当显示/保留”的方块集合。
     *
     * <p>用于推导本帧世界方块状态的变化量，并触发区块重建，
     * 避免在逐步清除时出现“黑洞/虚空（区块未及时重渲染）”的视觉问题。
     */
    private final Map<UUID, Set<BlockPos>> lastFrameVisibleBlocks = new HashMap<>();
    
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
        
        float currentTime = timelineModel.getCurrentTime();
        BlockVisualStateManager visualStateManager = BlockVisualStateManager.getInstance();
        
        // 🔥 步骤 0：清空上一帧的 renderEntries（确保每帧都是全新的）
        visualStateManager.clearFrame(); // 这会清空 visualOverrides 和 renderEntries
        
        Map<UUID, Set<BlockPos>> currentFrameVisibleBlocks = new HashMap<>();
        Map<UUID, Float> currentFrameOpacity = new HashMap<>();
        Map<UUID, List<BlockSequenceEntry>> currentFrameSequences = new HashMap<>();
        Map<UUID, ClipAnimationConfig> currentFrameConfigs = new HashMap<>();

        // 本帧世界方块（AIR/恢复）变化导致的重渲染位置（与视觉覆盖层无关）
        Set<BlockPos> worldStateChangedBlocksForRerender = new HashSet<>();
        
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("[TimelineClipBlockController] 开始更新 (当前时间: {:.2f}s)", currentTime);
        }
        
        // ◆ 步骤1：遍历所有轨道和片段，计算每个容器在当前时间应该显示的方块和其默认透明度
        for (TimelineTrack track : timelineModel.getAllTracks()) {
            if (track == null) {
                continue;
            }
            
            boolean trackEnabled = !track.isLocked() && track.isVisible();
            TrackType trackType = track.getType();
            
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
                currentFrameConfigs.put(containerId, config);
                
                // ◆ 根据播放头位置和片段配置计算应该显示的方块
                // 完全基于片段的ClipAnimationConfig，不依赖容器状态
                VisibleBlocksResult result = calculateVisibleBlocks(clip, trackType, currentTime, trackEnabled, container, config);
                Set<BlockPos> visibleBlocks = result.visibleBlocks();
                currentFrameVisibleBlocks.put(containerId, visibleBlocks);
                currentFrameOpacity.put(containerId, clip.getOpacity()); // 使用片段的透明度
                currentFrameSequences.put(containerId, result.sequenceEntries());
                
                if (LOGGER.isTraceEnabled() && !visibleBlocks.isEmpty()) {
                    LOGGER.trace("[TimelineClipBlockController] 片段 {} (容器: {}) 计算得到 {} 个可见方块(时间: {:.2f}s, 片段范围: [{:.2f}s, {:.2f}s])",
                        clip.getLabel(), container.getName(), visibleBlocks.size(), currentTime, clip.getStartTime(), clip.getEndTime());
                }
            }
        }

        // ✅ 步骤1.5：根据“上一帧可见集 vs 当前帧可见集”推导世界方块变化量，提前为区块重建收集 pos
        // 说明：showContainerBlocks 会真实修改世界方块（置 AIR / 从快照恢复），但客户端区块重建可能滞后。
        // 这里通过 scheduleBlockRenders 主动触发重渲染，避免清除过程出现“黑洞/虚空”。
        for (Map.Entry<UUID, Set<BlockPos>> entry : currentFrameVisibleBlocks.entrySet()) {
            UUID containerId = entry.getKey();
            Set<BlockPos> now = entry.getValue() != null ? entry.getValue() : java.util.Collections.emptySet();
            Set<BlockPos> prev = lastFrameVisibleBlocks.get(containerId);
            if (prev == null) {
                // 第一次看到该容器：保守起见，认为整组都可能变化
                Container container = Objects.requireNonNull(ContainerManager.getInstance()).getContainer(containerId);
                if (container != null) {
                    worldStateChangedBlocksForRerender.addAll(container.getBlocks());
                } else {
                    worldStateChangedBlocksForRerender.addAll(now);
                }
                continue;
            }
            // 计算对称差（发生显示/隐藏切换的 pos）
            for (BlockPos p : prev) {
                if (!now.contains(p)) {
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
                    worldStateChangedBlocksForRerender.add(p);
                    worldStateChangedBlocksForRerender.add(p.up());
                    worldStateChangedBlocksForRerender.add(p.down());
                    worldStateChangedBlocksForRerender.add(p.north());
                    worldStateChangedBlocksForRerender.add(p.south());
                    worldStateChangedBlocksForRerender.add(p.east());
                    worldStateChangedBlocksForRerender.add(p.west());
                }
            }
        }
        
        // ◆ 步骤2：应用遮挡覆盖规则，调整透明度并标记需要隐藏世界方块的容器
        // 构建容器ID到片段的映射，便于后续查询
        Map<UUID, TimelineClip> containerToClipMap = new HashMap<>();
        for (TimelineTrack track : timelineModel.getAllTracks()) {
            if (track == null) continue;
            for (TimelineClip clip : track.getClips()) {
                if (clip != null && clip.getBoundContainerId() != null) {
                    containerToClipMap.put(clip.getBoundContainerId(), clip);
                }
            }
        }

        // ✅ 步骤2.1：应用遮挡覆盖规则，找出被遮挡的容器
        Set<UUID> occludedContainers = new HashSet<>(); // 被遮挡容器的ID集合
        applyOcclusionOverrides(currentTime, currentFrameVisibleBlocks, currentFrameOpacity, occludedContainers, containerToClipMap);

        // ✅ 步骤3：应用方块可见性状态（两层系统）
        // 双重控制机制：
        // 1. 通过 ContainerVisibilityService 控制真实方块显示/隐藏
        // 2. 通过 BlockVisualStateManager 控制视觉状态（透明度、覆盖层等）
        var visibilityService = com.chronoblocks.core.container.visibility.ContainerServices.visibility();

        for (Map.Entry<UUID, Set<BlockPos>> entry : currentFrameVisibleBlocks.entrySet()) {
            UUID containerId = entry.getKey();
            Set<BlockPos> blocks = entry.getValue();
            float opacity = currentFrameOpacity.getOrDefault(containerId, 1.0f);
            
            Container container = Objects.requireNonNull(ContainerManager.getInstance()).getContainer(containerId);
            if (container == null) {
                continue;
            }
            
            // 🔹 关键改动：判断容器是否被遮挡
            // 如果被遮挡，方块仍然需要在世界中存在，但通过 Mixin 阻止渲染
            TimelineClip occludedClip = containerToClipMap.get(containerId);
            boolean occlusionTransparencyEnabled = occludedClip == null
                    || (occludedClip.getAnimationConfig() != null
                        && occludedClip.getAnimationConfig().isOcclusionTransparencyEnabled());
            boolean isOccluded = occlusionTransparencyEnabled && occludedContainers.contains(containerId);
            
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
                
                if (blocks.isEmpty()) {
                    // 如果没有方块可见：需要确保容器在世界中被清空（置 AIR）
                    // （清除轨道必须执行这一点；容器轨道也允许显式清空）
                    visibilityService.showContainerBlocks(container, java.util.Collections.emptySet());
                } else {
                    // 有方块应该显示，使用 showContainerBlocks 恢复这些方块
                    // 即使容器状态是 HIDDEN，这个方法也能部分显示方块
                    // 🔹 注意：对于被遮挡的容器，方块会恢复存在，但通过 Mixin 阻止渲染
                    visibilityService.showContainerBlocks(container, blocks);
                    
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("[TimelineClipBlockController] 容器 {} 显示 {} 个方块(时间: {:.2f}s, 遮挡: {})",
                            container.getName(), blocks.size(), currentTime, isOccluded);
                    }
                }
            } else {
                LOGGER.warn("[TimelineClipBlockController] ContainerVisibilityService 未初始化，无法控制方块显示");
            }
            
            // ◆ 然后设置视觉状态（用于半透明效果和遮挡淡化）
            if (blocks.isEmpty()) {
                // 如果没有方块可见，则完全隐藏
                visualStateManager.setBlocksState(container.getBlocks(), BlockVisualStateManager.BlockVisualState.hidden());
            } else {
                // 设置可见方块的视觉状态
                // 🔹 关键逻辑：如果容器被遮挡，使用ghost模式并隐藏世界方块
                if (isOccluded && opacity < 1.0f) {
                    // 🔹 遮挡淡化：隐藏世界方块，只渲染透明 ghost 版本（不写深度，避免遮挡）
                    visualStateManager.setBlocksState(
                            blocks,
                            BlockVisualStateManager.BlockVisualState.ghost(opacity, false, true).withSuppressWorldRendering(true)
                    );
                } else if (opacity < 1.0f) {
                    // 普通半透明（片段自身的透明度设置）：不隐藏世界方块，使用覆盖层
                    visualStateManager.setBlocksState(
                            blocks,
                            BlockVisualStateManager.BlockVisualState.translucent(opacity).withSuppressWorldRendering(true)
                    );
                } else {
                    // 完全不透明，使用正常状态（不使用覆盖层）
                    visualStateManager.setBlocksState(blocks, BlockVisualStateManager.BlockVisualState.normal());
                }
                
                // 对于容器中不属于当前可见集的方块，也设置为隐藏
                Set<BlockPos> hiddenBlocks = new HashSet<>(container.getBlocks());
                hiddenBlocks.removeAll(blocks);
                if (!hiddenBlocks.isEmpty()) {
                    visualStateManager.setBlocksState(hiddenBlocks, BlockVisualStateManager.BlockVisualState.hidden());
                }
            }

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
                    blocks
            );
        }

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

        // 更新 lastFrameVisibleBlocks（并清理已不存在的容器）
        lastFrameVisibleBlocks.keySet().retainAll(currentFrameVisibleBlocks.keySet());
        for (Map.Entry<UUID, Set<BlockPos>> e : currentFrameVisibleBlocks.entrySet()) {
            UUID id = e.getKey();
            Set<BlockPos> s = e.getValue();
            lastFrameVisibleBlocks.put(id, s != null ? new HashSet<>(s) : java.util.Collections.emptySet());
        }
        visualStateManager.finishFrame();
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
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (BlockPos pos : changedBlocks) {
                if (pos == null) continue;
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX());
                maxY = Math.max(maxY, pos.getY());
                maxZ = Math.max(maxZ, pos.getZ());
            }
            if (minX <= maxX && minY <= maxY && minZ <= maxZ) {
                client.worldRenderer.scheduleBlockRenders(minX, minY, minZ, maxX, maxY, maxZ);
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
        Set<BlockPos> visibleBlocks = new HashSet<>();
        List<BlockSequenceEntry> sequenceEntries = new ArrayList<>();

        TrackType type = trackType != null ? trackType : TrackType.CONTAINER;

        List<BlockPos> allBlocks = new ArrayList<>(container.getBlocks());
        if (allBlocks.isEmpty()) {
            return new VisibleBlocksResult(visibleBlocks, sequenceEntries);
        }

        // 轨道禁用：容器轨道 => 隐藏；清除轨道 => 不清除（保留全量）
        if (!trackEnabled) {
            if (type == TrackType.ERASE) {
                visibleBlocks.addAll(allBlocks);
            }
            return new VisibleBlocksResult(visibleBlocks, sequenceEntries);
        }

        float clipStart = clip.getStartTime();
        float clipEnd = clip.getEndTime();

        // 片段左侧
        if (currentTime < clipStart) {
            if (type == TrackType.ERASE) {
                visibleBlocks.addAll(allBlocks);
            }
            return new VisibleBlocksResult(visibleBlocks, sequenceEntries);
        }

        // 片段范围内：按序列推进
        if (currentTime >= clipStart && currentTime <= clipEnd) {
            float clipDuration = clipEnd - clipStart;
            List<BlockSequenceEntry> generated = BlockSequenceGenerator.generate(
                allBlocks, container, config != null ? config.getSequenceParameters() : null
            );
            if (generated != null) {
                sequenceEntries.addAll(generated);
            }
            if (sequenceEntries.isEmpty()) {
                // 后备：序列为空时，直接视为全量
                if (type == TrackType.ERASE) {
                    visibleBlocks.addAll(allBlocks);
                } else {
                    visibleBlocks.addAll(allBlocks);
                }
                return new VisibleBlocksResult(visibleBlocks, sequenceEntries);
            }

            if (type == TrackType.CONTAINER) {
                // 逐步显示
                for (BlockSequenceEntry entry : sequenceEntries) {
                    float normalizedTime = entry.getNormalizedTime();
                    float blockAppearTime = clipStart + normalizedTime * clipDuration;
                    if (currentTime >= blockAppearTime) {
                        visibleBlocks.add(entry.getPos());
                    }
                }
            } else if (type == TrackType.ERASE) {
                // 逐步清除：开始时全部可见，达到该方块“清除时刻”后移除
                visibleBlocks.addAll(allBlocks);
                for (BlockSequenceEntry entry : sequenceEntries) {
                    float normalizedTime = entry.getNormalizedTime();
                    float blockRemoveTime = clipStart + normalizedTime * clipDuration;
                    if (currentTime >= blockRemoveTime) {
                        visibleBlocks.remove(entry.getPos());
                    }
                }
            } else {
                // 其他类型（理论上不会到这里）：默认全量
                visibleBlocks.addAll(allBlocks);
            }

            return new VisibleBlocksResult(visibleBlocks, sequenceEntries);
        }

        // 片段右侧
        if (currentTime > clipEnd) {
            if (type == TrackType.CONTAINER) {
                visibleBlocks.addAll(allBlocks);
            } else if (type == TrackType.ERASE) {
                // 全清除 => 保持空集合
            }
        }

        return new VisibleBlocksResult(visibleBlocks, sequenceEntries);
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
            LOGGER.info("[TimelineClipBlockController] registerRenderEntries: visibleBlocks is empty for container {}", 
                container.getName());
            return;
        }
        if (sequenceEntries == null || sequenceEntries.isEmpty()) {
            LOGGER.info("[TimelineClipBlockController] registerRenderEntries: sequenceEntries is empty for container {}", 
                container.getName());
            return;
        }

        LOGGER.info("[TimelineClipBlockController] registerRenderEntries: container={} visibleBlocks={} entries={}", 
            container.getName(), visibleBlocks.size(), sequenceEntries.size());

        float clipStart = clip.getStartTime();
        float clipLength = clip.getLength();

        // 🔥 步骤 1：遍历当前片段的所有 BlockSequenceEntry
        for (BlockSequenceEntry entry : sequenceEntries) {
            if (entry == null) {
                continue;
            }
            BlockPos pos = entry.getPos();
            if (pos == null || !visibleBlocks.contains(pos)) {
                continue;
            }

            // 🔥 步骤 2：获取方块状态
            BlockState blockState = resolveBlockStateForRender(container, pos);
            if (blockState == null) {
                continue;
            }

            // 🔥 步骤 3：获取视觉状态（可能已经被遮挡逻辑修改过）
            BlockVisualStateManager.BlockVisualState visualState = visualStateManager.getBlockState(pos);
            if (visualState.isHidden()) {
                continue;
            }

            // 🔥 步骤 4：生成 snapshot 用于动画渲染
            // 关键：使用时间线时间（currentTime）采样，确保动画正确执行
            long randomSeed = container.getId().getLeastSignificantBits() ^ pos.asLong();
            BlockAnimationSnapshot snapshot = BlockAnimationEngine.sample(
                    pos,
                    blockState,
                    config,
                    entry,
                    clipStart,
                    clipStart + clipLength,
                    currentTime,  // ✅ 使用时间线时间，不是世界时间
                    randomSeed
            );
            
            LOGGER.info("[TimelineClipBlockController] temp snapshot: pos={} visible={} alpha={} hasTransform={}", 
                pos, snapshot.isVisible(),
                    snapshot.getAlpha(),
                    snapshot.getTransform() != null);
            
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

            LOGGER.info("[TimelineClipBlockController] Adding render entry 2.0: pos={} alpha={}",
                pos, snapshot.getAlpha());

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
        
        LOGGER.info("[TimelineClipBlockController] registerRenderEntries completed: {} entries added", 
            sequenceEntries.stream().filter(e -> e != null && visibleBlocks.contains(e.getPos())).count());
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
        if (snapshot == null || snapshot.getTransform() == null) {
            return true;
        }
        
        // 获取 snapshot 的变换矩阵
        org.joml.Matrix4f mat = new org.joml.Matrix4f(snapshot.getTransform().peek().getPositionMatrix());
        org.joml.Matrix4f identity = new org.joml.Matrix4f().identity();
        
        // 逐元素比较，允许浮点精度误差
        final float EPSILON = 1e-6f;
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                float a = mat.get(row, col);
                float b = identity.get(row, col);
                if (Math.abs(a - b) > EPSILON) {
                    return false; // 发现非单位元素，有动画变换
                }
            }
        }
        
        return true; // 所有元素都接近单位矩阵，无动画变换
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

    private record VisibleBlocksResult(Set<BlockPos> visibleBlocks, List<BlockSequenceEntry> sequenceEntries) { }
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
        if (!container.getBlocks().isEmpty()) {
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
