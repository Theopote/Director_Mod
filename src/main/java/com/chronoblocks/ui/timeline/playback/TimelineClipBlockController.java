package com.chronoblocks.ui.timeline.playback;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;
import com.chronoblocks.core.container.ContainerState;
import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
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
                
                // ★ 每个容器只有一个片段，直接判断这个片段是否应该显示
                boolean clipShouldShow = calculateClipVisibility(clip, track, currentTime, trackEnabled);
                containerShouldShowMap.put(containerId, clipShouldShow);
                
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
            
            // 只有状态变化时才更新
            Boolean lastState = containerVisibilityCache.get(containerId);
            if (lastState == null || !lastState.equals(shouldShow)) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[TimelineClipBlockController] 容器 {} 状态变化: {} -> {} (当前时间: {:.2f}s)",
                        container.getName(), lastState == null ? "null" : (lastState ? "显示" : "隐藏"), 
                        shouldShow ? "显示" : "隐藏", currentTime);
                }
                updateContainerVisibility(container, shouldShow);
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
                        updateContainerVisibility(container, false);
                        containerVisibilityCache.put(containerId, false);
                    }
                }
            }
        }
        
        // 3. 清理已删除容器的缓存
        cleanupCache();
    }
    
    /**
     * 计算片段是否应该显示
     * 
     * <p>规则：
     * <ul>
     *   <li>轨道锁定或隐藏 → 强制隐藏</li>
     *   <li>播放头在片段左侧且没有碰到片段左边缘（currentTime < clipStart）→ 隐藏</li>
     *   <li>播放头与片段相交（currentTime >= clipStart && currentTime <= clipEnd）→ 显示</li>
     *   <li>播放头在片段右侧（currentTime > clipEnd）→ 显示（即使没有相交）</li>
     * </ul>
     * 
     * @param clip 片段
     * @param track 轨道
     * @param currentTime 当前播放头时间
     * @param trackEnabled 轨道是否启用（未锁定且可见）
     * @return true表示应该显示，false表示应该隐藏
     */
    private boolean calculateClipVisibility(TimelineClip clip, TimelineTrack track, 
                                            float currentTime, boolean trackEnabled) {
        // ★ 规则1：如果轨道锁定或隐藏，强制隐藏所有片段的方块
        if (!trackEnabled) {
            return false;
        }
        
        float clipStart = clip.getStartTime();
        float clipEnd = clip.getEndTime();
        
        // ★ 规则2：播放头在片段左侧且没有碰到片段左边缘 → 隐藏
        if (currentTime < clipStart) {
            return false;
        }
        
        // ★ 规则3：播放头与片段相交（包含左边缘和右边缘）→ 显示
        // 注意：播放头刚好在左边缘（currentTime == clipStart）或右边缘（currentTime == clipEnd）也算相交
        if (currentTime >= clipStart && currentTime <= clipEnd) {
            return true;
        }
        
        // ★ 规则4：播放头在片段右侧 → 显示（即使没有相交）
        // 重要：只要播放头在片段右侧，不管有没有与片段相交，都应该显示
        return currentTime > clipEnd;
        
        // 理论上不应该到达这里，但为了安全起见
    }
    
    /**
     * 更新容器的可见性状态
     */
    private void updateContainerVisibility(Container container, boolean shouldShow) {
        if (container == null) {
            return;
        }
        
        ContainerState targetState = shouldShow ? ContainerState.VISIBLE : ContainerState.HIDDEN;
        
        // 只有当状态需要改变时才更新（避免不必要的状态切换）
        if (container.getState() != targetState) {
            container.setState(targetState);
            
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[TimelineClipBlockController] 容器 {} 可见性更新: {} -> {}", 
                    container.getName(), container.getState().getDisplayName(), targetState.getDisplayName());
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
    
    /**
     * 清除所有缓存（当时间线重置时调用）
     */
    public void clearCache() {
        containerVisibilityCache.clear();
    }
}

