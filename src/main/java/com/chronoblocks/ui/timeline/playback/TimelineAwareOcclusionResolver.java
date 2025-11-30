package com.chronoblocks.ui.timeline.playback;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;
import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * 时间线感知的遮挡解析器 v2.0
 * 
 * <p>与静态 BlockOcclusionResolver 不同，此解析器考虑时间线状态：
 * <ul>
 *   <li>只处理在当前时间点处于 active 或 post-active 状态的片段</li>
 *   <li>支持基于片段 Z-order 的遮挡优先级</li>
 *   <li>支持计算遮挡所需的透明度</li>
 *   <li>支持片段之间的空间遮挡关系</li>
 * </ul>
 * 
 * <p>核心逻辑：
 * <pre>
 * 对于当前片段 B：
 *   1. 找出所有 startTime < B.startTime 的片段 A
 *   2. 检查 A 是否仍在 post-animation 状态（endTime < currentTime < restoreTime）
 *   3. 检测 A 与 B 的空间重叠
 *   4. 计算 A 需要的透明度（基于 B 的 autoFadeOpacity）
 * </pre>
 * 
 * @author ChronoBlocks Team
 * @version 2.0
 */
public class TimelineAwareOcclusionResolver {
    
    private final BlockOcclusionResolver spatialResolver;
    private TimelineModel timelineModel;
    
    public TimelineAwareOcclusionResolver() {
        this.spatialResolver = new BlockOcclusionResolver();
    }
    
    /**
     * 设置时间线模型（由 TimelineClipBlockController 调用）
     */
    public void setTimelineModel(TimelineModel model) {
        this.timelineModel = model;
    }
    
    /**
     * 获取会遮挡指定片段的所有方块位置及所需的透明度
     * 
     * <p>返回 Map 结构：
     * <ul>
     *   <li>Key: 遮挡方块的 BlockPos</li>
     *   <li>Value: 该方块需要的透明度 [0..1]</li>
     * </ul>
     * 
     * @param currentClip 当前正在播放的片段
     * @param timelineTime 当前时间线时间（秒）
     * @return 遮挡方块位置到透明度的映射
     */
    @NotNull
    public Map<BlockPos, Float> getOccludingBlocksWithTransparency(
            @NotNull TimelineClip currentClip,
            double timelineTime
    ) {
        Map<BlockPos, Float> result = new HashMap<>();
        
        if (timelineModel == null || currentClip == null) {
            return result;
        }
        
        UUID currentContainerId = currentClip.getBoundContainerId();
        if (currentContainerId == null) {
            return result;
        }
        
        Container currentContainer = ContainerManager.getInstance().getContainer(currentContainerId);
        if (currentContainer == null) {
            return result;
        }
        
        // 如果当前片段没有启用自动淡化前序片段，直接返回空
        if (!currentClip.isAutoFadePreviousClips()) {
            return result;
        }
        
        // 找出所有可能遮挡的前序片段
        List<OcclusionCandidate> candidates = collectOcclusionCandidates(currentClip, timelineTime);
        
        // 对每个候选片段，检测空间遮挡
        for (OcclusionCandidate candidate : candidates) {
            if (candidate.clip == null || candidate.container == null) {
                continue;
            }
            
            // 使用空间解析器检测重叠
            Map<UUID, Set<BlockPos>> occluding = spatialResolver.resolveOccludingBlocks(
                    currentContainer,
                    Collections.singletonList(candidate.container)
            );
            
            // 获取该容器中遮挡的方块
            Set<BlockPos> occludingBlocks = occluding.get(candidate.container.getId());
            if (occludingBlocks != null && !occludingBlocks.isEmpty()) {
                // 计算该片段需要的透明度（可能被多个后续片段影响，取最小值）
                float requiredAlpha = computeRequiredTransparency(candidate, currentClip, timelineTime);
                
                // 将每个遮挡方块加入结果，如果已存在则取较小的透明度
                for (BlockPos pos : occludingBlocks) {
                    float existingAlpha = result.getOrDefault(pos, 1.0f);
                    result.put(pos, Math.min(existingAlpha, requiredAlpha));
                }
            }
        }
        
        return result;
    }
    
    /**
     * 获取指定片段需要的透明度（考虑多个后续片段的遮挡）
     * 
     * @param clipA 被遮挡的片段
     * @param clipB 遮挡它的片段
     * @param timelineTime 当前时间
     * @return 需要的透明度 [0..1]
     */
    public float getRequiredTransparency(
            @NotNull TimelineClip clipA,
            @NotNull TimelineClip clipB,
            double timelineTime
    ) {
        // 如果 clipB 没有启用自动淡化，不遮挡
        if (!clipB.isAutoFadePreviousClips()) {
            return 1.0f;
        }
        
        // 如果 clipB 还没开始，不遮挡
        if (timelineTime < clipB.getStartTime()) {
            return 1.0f;
        }
        
        // 如果 clipB 已经结束并且超过了恢复时间，不遮挡
        // TODO: 这里需要 restoreTime，暂时使用 clipB.getEndTime() + fadeDuration
        double clipBEnd = clipB.getEndTime();
        double fadeDuration = clipB.getAnimationConfig() != null ? 
                clipB.getAnimationConfig().getDuration() : 2.0;
        double restoreTime = clipBEnd + fadeDuration;
        
        if (timelineTime > restoreTime) {
            return 1.0f;
        }
        
        // 在 clipB 播放期间，clipA 应该半透明
        return clipB.getAutoFadeOpacity();
    }
    
    /**
     * 遮挡候选：包含片段、容器和状态信息
     */
    private static class OcclusionCandidate {
        final TimelineClip clip;
        final Container container;
        @SuppressWarnings("unused") // 保留用于未来扩展
        final double restoreTime; // 片段恢复为完全不透明的时间
        
        OcclusionCandidate(TimelineClip clip, Container container, double restoreTime) {
            this.clip = clip;
            this.container = container;
            this.restoreTime = restoreTime;
        }
    }
    
    /**
     * 收集可能遮挡当前片段的所有前序片段
     * 
     * @param currentClip 当前片段
     * @param timelineTime 当前时间
     * @return 候选片段列表
     */
    @NotNull
    private List<OcclusionCandidate> collectOcclusionCandidates(
            @NotNull TimelineClip currentClip,
            double timelineTime
    ) {
        List<OcclusionCandidate> candidates = new ArrayList<>();
        double currentStart = currentClip.getStartTime();
        
        // 遍历所有轨道和片段
        for (TimelineTrack track : timelineModel.getAllTracks()) {
            if (track == null || track.isLocked() || !track.isVisible()) {
                continue;
            }
            
            for (TimelineClip clip : track.getClips()) {
                if (clip == null || clip == currentClip) {
                    continue;
                }
                
                UUID containerId = clip.getBoundContainerId();
                if (containerId == null) {
                    continue;
                }
                
                // 只处理比当前片段更早的片段
                if (clip.getStartTime() >= currentStart) {
                    continue;
                }
                
                Container container = ContainerManager.getInstance().getContainer(containerId);
                if (container == null) {
                    continue;
                }
                
                // 检查片段是否处于 post-animation 状态（已完成但可能需要淡化）
                double clipEnd = clip.getEndTime();
                
                // 如果片段已经结束，计算恢复时间
                // TODO: 这里应该使用 clip 的 postAnimationFadeEnd，暂时用 clipEnd + duration
                double fadeDuration = clip.getAnimationConfig() != null ? 
                        clip.getAnimationConfig().getDuration() : 2.0;
                double restoreTime = clipEnd + fadeDuration;
                
                // 片段必须已经完成动画，且未超过恢复时间
                if (timelineTime >= clipEnd && timelineTime <= restoreTime) {
                    candidates.add(new OcclusionCandidate(clip, container, restoreTime));
                }
                // 或者片段仍在动画中（也在遮挡范围内）
                else if (timelineTime >= clip.getStartTime() && timelineTime < clipEnd) {
                    candidates.add(new OcclusionCandidate(clip, container, restoreTime));
                }
            }
        }
        
        return candidates;
    }
    
    /**
     * 计算候选片段需要的透明度（考虑多个后续片段的影响）
     */
    private float computeRequiredTransparency(
            @NotNull OcclusionCandidate candidate,
            @NotNull TimelineClip currentClip,
            double timelineTime
    ) {
        float minAlpha = 1.0f;
        
        // 检查所有可能遮挡它的后续片段
        double candidateStart = candidate.clip.getStartTime();
        
        for (TimelineTrack track : timelineModel.getAllTracks()) {
            if (track == null || track.isLocked() || !track.isVisible()) {
                continue;
            }
            
            for (TimelineClip clip : track.getClips()) {
                if (clip == null || clip == candidate.clip) {
                    continue;
                }
                
                // 只处理比候选片段更晚的片段
                if (clip.getStartTime() <= candidateStart) {
                    continue;
                }
                
                // 检查该片段是否在播放中
                if (timelineTime < clip.getStartTime() || timelineTime > clip.getEndTime()) {
                    continue;
                }
                
                // 如果该片段启用了自动淡化，计算需要的透明度
                if (clip.isAutoFadePreviousClips()) {
                    float requiredAlpha = getRequiredTransparency(candidate.clip, clip, timelineTime);
                    minAlpha = Math.min(minAlpha, requiredAlpha);
                }
            }
        }
        
        return minAlpha;
    }
}

