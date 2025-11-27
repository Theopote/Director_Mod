package com.chronoblocks.ui.timeline.playback;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;
import com.chronoblocks.core.container.ContainerState;
import com.chronoblocks.core.animation.BlockAnimationEngine;
import com.chronoblocks.core.animation.AnimationEngineFactory;
import com.chronoblocks.core.animation.ClipAnimationConfig;
import com.chronoblocks.core.animation.sequence.SequenceStrategy;
import com.chronoblocks.core.animation.sequence.SequenceStrategyFactory;
import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 时间线片段方块控制器
 * 
 * <p>根据播放头位置和轨道状态控制片段对应容器的方块在Minecraft中的显示/隐藏
 * 
 * <p>重要约束：
 * <ul>
 *   <li>每个容器有且只有一个片段</li>
 *   <li>同一个容器只能在轨道上产生一个片段</li>
 * </ul>
 * 
 * <p>规则：
 * <ul>
 *   <li>播放头在片段左侧且没有碰到片段左边缘 → 隐藏方块</li>
 *   <li>播放头与片段相交 → 显示方块（前提：轨道未锁定且轨道可见）</li>
 *   <li>播放头在片段右侧 → 显示方块（即使没有相交，前提：轨道未锁定且轨道可见）</li>
 *   <li>轨道锁定或隐藏 → 强制隐藏方块</li>
 * </ul>
 */
public class TimelineClipBlockController {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineClipBlockController.class);
    
    private static TimelineClipBlockController instance;
    private TimelineModel timelineModel;
    
    // 记录每个容器的上次状态，避免频繁切换
    private final Map<UUID, Boolean> containerVisibilityCache = new HashMap<>();
    
    // 记录每个容器在动画中应该显示的方块集合（用于动画效果）
    private final Map<UUID, Set<BlockPos>> containerVisibleBlocksCache = new HashMap<>();
    
    // 动画引擎工厂
    private final AnimationEngineFactory animationEngineFactory = AnimationEngineFactory.getInstance();
    
    // 序列策略工厂
    private final SequenceStrategyFactory sequenceStrategyFactory = SequenceStrategyFactory.getInstance();
    
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
     * <p>注意：每个容器有且只有一个片段，所以逻辑可以简化
     */
    public void update() {
        if (timelineModel == null) {
            return;
        }
        
        float currentTime = timelineModel.getCurrentTime();
        
        // ★ 简化：每个容器只有一个片段，直接遍历所有片段即可
        Map<UUID, Boolean> containerShouldShowMap = new HashMap<>();
        
        // 遍历所有轨道和片段，收集每个容器的显示状态
        for (TimelineTrack track : timelineModel.getAllTracks()) {
            if (track == null) {
                continue;
            }
            
            // 检查轨道状态：锁定或隐藏时，所有片段都隐藏
            boolean trackEnabled = !track.isLocked() && track.isVisible();
            
            for (TimelineClip clip : track.getClips()) {
                if (clip == null || clip.getBoundContainerId() == null) {
                    continue;
                }
                
                UUID containerId = clip.getBoundContainerId();
                Container container = ContainerManager.getInstance().getContainer(containerId);
                if (container == null) {
                    continue;
                }
                
                // ★ 每个容器只有一个片段，根据播放头位置和动画配置判断应该显示的方块
                Set<BlockPos> visibleBlocks = calculateVisibleBlocks(clip, track, currentTime, trackEnabled, container);
                
                // 判断容器是否应该显示（至少有一个方块应该显示）
                boolean clipShouldShow = !visibleBlocks.isEmpty();
                containerShouldShowMap.put(containerId, clipShouldShow);
                containerVisibleBlocksCache.put(containerId, visibleBlocks);
                
                // 调试日志（仅在调试模式启用）
                if (LOGGER.isTraceEnabled() && clipShouldShow) {
                    LOGGER.trace("[TimelineClipBlockController] 片段 {} (容器: {}) 应该显示，时间: {:.2f}s, 片段范围: [{:.2f}s, {:.2f}s]",
                        clip.getLabel(), container.getName(), currentTime, clip.getStartTime(), clip.getEndTime());
                }
            }
        }
        
        // ★ 处理所有相关容器
        // 1. 更新在 containerShouldShowMap 中的容器
        Set<UUID> processedContainerIds = new HashSet<>();
        for (Map.Entry<UUID, Boolean> entry : containerShouldShowMap.entrySet()) {
            UUID containerId = entry.getKey();
            boolean shouldShow = entry.getValue();
            processedContainerIds.add(containerId);
            
            Container container = ContainerManager.getInstance().getContainer(containerId);
            if (container == null) {
                continue;
            }
            
            // 获取应该显示的方块集合
            Set<BlockPos> visibleBlocks = containerVisibleBlocksCache.getOrDefault(containerId, Collections.emptySet());
            
            // 检查状态是否变化
            Boolean lastState = containerVisibilityCache.get(containerId);
            Set<BlockPos> lastVisibleBlocks = containerVisibleBlocksCache.getOrDefault(containerId, Collections.emptySet());
            
            boolean stateChanged = lastState == null || !lastState.equals(shouldShow);
            boolean blocksChanged = !visibleBlocks.equals(lastVisibleBlocks);
            
            // 只有状态或方块集合变化时才更新
            if (stateChanged || blocksChanged) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[TimelineClipBlockController] 容器 {} 状态变化: {} -> {} (当前时间: {:.2f}s, 可见方块: {})",
                        container.getName(), lastState == null ? "null" : (lastState ? "显示" : "隐藏"), 
                        shouldShow ? "显示" : "隐藏", currentTime, visibleBlocks.size());
                }
                updateContainerVisibilityWithAnimation(container, shouldShow, visibleBlocks);
                containerVisibilityCache.put(containerId, shouldShow);
            }
        }
        
        // 2. ★ 处理在缓存中但不在当前片段映射中的容器
        // 这些容器之前在我们的控制下，但现在片段不存在或不应该显示，应该隐藏它们
        Set<UUID> cachedContainerIds = new HashSet<>(containerVisibilityCache.keySet());
        for (UUID containerId : cachedContainerIds) {
            if (!processedContainerIds.contains(containerId)) {
                // 这个容器在缓存中，但不在当前片段的映射中
                // 这意味着片段不存在或不应该显示，应该隐藏容器
                Container container = ContainerManager.getInstance().getContainer(containerId);
                if (container != null) {
                    Boolean lastState = containerVisibilityCache.get(containerId);
                    if (lastState != null && lastState) {
                        // 之前是显示状态，现在应该隐藏
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("[TimelineClipBlockController] 容器 {} 不再在任何片段范围内，隐藏 (当前时间: {:.2f}s)",
                                container.getName(), currentTime);
                        }
                        updateContainerVisibilityWithAnimation(container, false, Collections.emptySet());
                        containerVisibilityCache.put(containerId, false);
                    }
                }
            }
        }
        
        // 3. 清理已删除容器的缓存
        cleanupCache();
    }
    
    /**
     * 计算片段中在当前时间应该显示的方块集合
     * 
     * <p>规则：
     * <ul>
     *   <li>轨道锁定或隐藏 → 返回空集合（所有方块隐藏）</li>
     *   <li>播放头在片段左侧 → 返回空集合（所有方块隐藏）</li>
     *   <li>播放头在片段范围内 → 根据动画引擎计算应该显示的方块</li>
     *   <li>播放头在片段右侧 → 返回所有方块（完全显示）</li>
     * </ul>
     * 
     * @param clip 片段
     * @param track 轨道
     * @param currentTime 当前播放头时间
     * @param trackEnabled 轨道是否启用（未锁定且可见）
     * @param container 容器对象
     * @return 应该显示的方块位置集合
     */
    private Set<BlockPos> calculateVisibleBlocks(TimelineClip clip, TimelineTrack track, 
                                                  float currentTime, boolean trackEnabled,
                                                  Container container) {
        Set<BlockPos> visibleBlocks = new HashSet<>();
        
        // ★ 规则1：如果轨道锁定或隐藏，强制隐藏所有片段的方块
        if (!trackEnabled) {
            return visibleBlocks; // 返回空集合
        }
        
        float clipStart = clip.getStartTime();
        float clipEnd = clip.getEndTime();
        
        // ★ 规则2：播放头在片段左侧且没有碰到片段左边缘 → 隐藏所有方块
        if (currentTime < clipStart) {
            return visibleBlocks; // 返回空集合
        }
        
        // ★ 规则3：播放头在片段范围内 → 根据动画引擎计算应该显示的方块
        if (currentTime >= clipStart && currentTime <= clipEnd) {
            // 获取片段的动画配置
            ClipAnimationConfig config = clip.getAnimationConfig();
            if (config == null) {
                config = ClipAnimationConfig.defaultConfig();
            }
            
            // 获取动画模式
            com.chronoblocks.core.container.AnimationMode mode = config.getMode();
            if (mode == null) {
                mode = com.chronoblocks.core.container.AnimationMode.INSTANT;
            }
            
            // 获取动画时长（限制在片段时长内）
            float animationDuration = config.getDuration();
            float clipDuration = clipEnd - clipStart;
            animationDuration = Math.min(animationDuration, clipDuration);
            
            // ★ 步骤1：获取序列策略，对方块进行排序
            SequenceStrategy sequenceStrategy = sequenceStrategyFactory.getStrategy(config.getSequenceMode());
            List<BlockPos> allBlocks = new ArrayList<>(container.getBlocks());
            List<BlockPos> orderedBlocks = sequenceStrategy.order(allBlocks, container, config.getSequenceParameters());
            
            // ★ 步骤2：获取动画引擎
            BlockAnimationEngine engine = animationEngineFactory.getEngine(mode);
            
            // ★ 步骤3：遍历排序后的方块列表，计算哪些应该显示
            for (BlockPos blockPos : orderedBlocks) {
                // 计算该方块的出现时间
                float blockAppearTime = engine.calculateBlockAppearTime(
                    container, blockPos, clipStart, clipDuration, animationDuration, config.getParams()
                );
                
                // 如果当前时间已经达到或超过方块出现时间，则显示该方块
                if (currentTime >= blockAppearTime) {
                    visibleBlocks.add(blockPos);
                }
            }
            
            return visibleBlocks;
        }
        
        // ★ 规则4：播放头在片段右侧 → 显示所有方块（完全显示）
        // 重要：只要播放头在片段右侧，不管有没有与片段相交，都应该显示所有方块
        if (currentTime > clipEnd) {
            // 返回容器的所有方块
            visibleBlocks.addAll(container.getBlocks());
            return visibleBlocks;
        }
        
        // 理论上不应该到达这里，但为了安全起见返回空集合
        return visibleBlocks;
    }
    
    /**
     * 更新容器的可见性状态（支持动画效果）
     * 
     * <p>根据应该显示的方块集合，更新容器的可见性状态
     * 如果使用动画效果，会部分显示方块；否则，整体显示/隐藏
     */
    private void updateContainerVisibilityWithAnimation(Container container, boolean shouldShow, Set<BlockPos> visibleBlocks) {
        if (container == null) {
            return;
        }
        
        if (!shouldShow || visibleBlocks == null || visibleBlocks.isEmpty()) {
            // 隐藏容器（所有方块）
            ContainerState targetState = ContainerState.HIDDEN;
            if (container.getState() != targetState) {
                container.setState(targetState);
                
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[TimelineClipBlockController] 容器 {} 隐藏 (当前时间: {:.2f}s)", 
                        container.getName(), timelineModel != null ? timelineModel.getCurrentTime() : 0f);
                }
            }
            return;
        }
        
        // 应该显示，根据方块集合决定显示方式
        java.util.Set<BlockPos> allBlocks = new java.util.HashSet<>(container.getBlocks());
        boolean allBlocksVisible = visibleBlocks.size() == allBlocks.size() && visibleBlocks.containsAll(allBlocks);
        
        if (allBlocksVisible) {
            // 所有方块都应该显示，整体显示容器
            ContainerState targetState = ContainerState.VISIBLE;
            if (container.getState() != targetState) {
                container.setState(targetState);
                
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[TimelineClipBlockController] 容器 {} 完全显示 (当前时间: {:.2f}s)", 
                        container.getName(), timelineModel != null ? timelineModel.getCurrentTime() : 0f);
                }
            }
        } else {
            // 部分方块应该显示，使用动画效果显示部分方块
            // 注意：在动画期间，容器保持在 HIDDEN 状态，但通过 showContainerBlocks 部分显示方块
            var visibilityService = com.chronoblocks.core.container.visibility.ContainerServices.visibility();
            if (visibilityService != null) {
                // 如果容器还没有快照，先创建快照（隐藏所有方块）
                // 使用一个标记来避免重复创建快照
                if (!visibilityService.hasSnapshot(container)) {
                    // 如果容器当前是 VISIBLE，先切换到 HIDDEN 来创建快照
                    // 然后在动画期间保持在 HIDDEN 状态，但通过 showContainerBlocks 部分显示
                    if (container.getState() == ContainerState.VISIBLE) {
                        container.setState(ContainerState.HIDDEN);
                    }
                    // 如果已经是 HIDDEN，快照应该已经创建了
                }
                
                // 显示应该显示的方块（即使在 HIDDEN 状态下也能部分显示）
                visibilityService.showContainerBlocks(container, visibleBlocks);
                
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[TimelineClipBlockController] 容器 {} 部分显示 (当前时间: {:.2f}s, 可见方块: {}/{})", 
                        container.getName(), 
                        timelineModel != null ? timelineModel.getCurrentTime() : 0f,
                        visibleBlocks.size(), allBlocks.size());
                }
            }
        }
    }
    
    /**
     * 清理已删除容器的缓存
     */
    private void cleanupCache() {
        containerVisibilityCache.entrySet().removeIf(entry -> {
            UUID containerId = entry.getKey();
            Container container = ContainerManager.getInstance().getContainer(containerId);
            return container == null;
        });
    }

}