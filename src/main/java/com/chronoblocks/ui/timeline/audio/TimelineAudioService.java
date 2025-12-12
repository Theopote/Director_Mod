package com.chronoblocks.ui.timeline.audio;

import com.chronoblocks.core.animation.AnimationSoundCollector;
import com.chronoblocks.core.animation.sequence.BlockSequenceEntry;
import com.chronoblocks.core.animation.sequence.BlockSequenceGenerator;
import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;
import com.chronoblocks.core.animation.ClipAnimationConfig;
import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.TrackType;
import com.chronoblocks.ui.timeline.model.audio.AudioEvent;
import com.chronoblocks.ui.timeline.model.audio.AudioTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 时间线音频服务
 * 
 * <p>负责自动收集容器片段的声音事件，并将它们添加到音频轨道中。
 * 
 * <p>核心功能：
 * <ul>
 *   <li>当容器片段添加到时间线时，自动收集声音事件</li>
 *   <li>将声音事件添加到音频轨道（如果存在）</li>
 *   <li>当片段移动或调整时，自动更新声音事件的时间</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class TimelineAudioService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineAudioService.class);
    
    private static TimelineAudioService instance;
    private TimelineModel timelineModel;
    
    private TimelineAudioService() {
    }
    
    /**
     * 获取单例实例
     */
    public static TimelineAudioService getInstance() {
        if (instance == null) {
            instance = new TimelineAudioService();
        }
        return instance;
    }
    
    /**
     * 设置时间线模型
     */
    public void setTimelineModel(TimelineModel model) {
        this.timelineModel = model;
    }
    
    /**
     * 为容器片段收集并添加声音事件到音频轨道
     * 
     * <p>当片段添加到时间线时调用此方法，自动收集该片段的声音事件。
     * 
     * @param clip 时间线片段
     */
    public void collectAndAddSoundEvents(TimelineClip clip) {
        if (clip == null || timelineModel == null) {
            return;
        }
        
        UUID containerId = clip.getBoundContainerId();
        if (containerId == null) {
            return;
        }
        
        Container container = Objects.requireNonNull(ContainerManager.getInstance()).getContainer(containerId);
        if (container == null) {
            return;
        }
        
        // 获取或创建音频轨道
        AudioTrack audioTrack = getOrCreateAudioTrack();
        if (audioTrack == null) {
            return;
        }
        
        // 生成方块序列
        ClipAnimationConfig config = clip.getAnimationConfig();
        if (config == null) {
            config = ClipAnimationConfig.defaultConfig();
        }
        
        // 将 Set<BlockPos> 转换为 List<BlockPos>
        List<BlockSequenceEntry> sequenceEntries = BlockSequenceGenerator.generate(
                new ArrayList<>(container.getBlocks()),
                container,
                config.getSequenceParameters()
        );
        
        if (sequenceEntries == null || sequenceEntries.isEmpty()) {
            return;
        }
        
        // 收集声音事件
        AnimationSoundCollector collector = AnimationSoundCollector.getInstance();
        List<AudioEvent> events = collector.collectFromClip(clip, container, sequenceEntries);
        
        // 添加到音频轨道
        for (AudioEvent event : events) {
            audioTrack.addEvent(event);
        }
        
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[TimelineAudioService] 为片段 {} 收集并添加了 {} 个声音事件",
                    clip.getLabel(), events.size());
        }
    }
    
    /**
     * 获取或创建音频轨道
     * 
     * @return 音频轨道，如果无法创建则返回 null
     */
    private AudioTrack getOrCreateAudioTrack() {
        if (timelineModel == null) {
            return null;
        }
        
        // 查找现有的音频轨道
        for (TimelineTrack track : timelineModel.getAllTracks()) {
            if (track != null && track.getType() == TrackType.AUDIO) {
                if (track instanceof AudioTrack) {
                    return (AudioTrack) track;
                }
                // 如果是旧的 TimelineTrack，转换为 AudioTrack
                return convertToAudioTrack(track);
            }
        }
        
        // 如果没有找到，创建新的音频轨道
        AudioTrack audioTrack = new AudioTrack("音频轨道");
        timelineModel.addTrack(audioTrack);
        
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[TimelineAudioService] 创建了新的音频轨道");
        }
        
        return audioTrack;
    }
    
    /**
     * 将旧的 TimelineTrack 转换为 AudioTrack
     * 
     * @param track 旧的轨道
     * @return 新的 AudioTrack
     */
    private AudioTrack convertToAudioTrack(TimelineTrack track) {
        AudioTrack audioTrack = new AudioTrack(track.getName());
        
        // 复制属性
        audioTrack.setLocked(track.isLocked());
        audioTrack.setVisible(track.isVisible());
        audioTrack.setMuted(track.isMuted());
        audioTrack.setSolo(track.isSolo());
        audioTrack.setCollapsed(track.isCollapsed());
        audioTrack.setZOrder(track.getZOrder());
        
        // 迁移事件：先转换为 AudioTrack，然后使用新方法
        // 如果 track 不是 AudioTrack，先转换为 AudioTrack 以访问新方法
        if (track instanceof AudioTrack sourceTrack) {
            for (AudioEvent event : sourceTrack.getEvents()) {
                audioTrack.addEvent(event);
            }
        } else {
            // 对于旧的 TimelineTrack，使用已弃用方法进行迁移（这是最后一次使用）
            @SuppressWarnings("deprecation")
            List<AudioEvent> oldEvents = track.getAudioEvents();
            for (AudioEvent event : oldEvents) {
                audioTrack.addEvent(event);
            }
        }
        
        // 替换轨道
        int index = timelineModel.getAllTracks().indexOf(track);
        if (index >= 0) {
            timelineModel.removeTrack(track);
            timelineModel.getAllTracks().add(index, audioTrack);
        }
        
        return audioTrack;
    }
    
    /**
     * 移除片段关联的声音事件
     * 
     * <p>当片段从时间线移除时调用此方法，清理该片段的声音事件。
     * 
     * @param clip 时间线片段
     */
    public void removeSoundEventsForClip(TimelineClip clip) {
        if (clip == null || timelineModel == null) {
            return;
        }
        
        // 查找音频轨道
        for (TimelineTrack track : timelineModel.getAllTracks()) {
            if (track != null && track.getType() == TrackType.AUDIO) {
                AudioTrack audioTrack;
                if (track instanceof AudioTrack) {
                    audioTrack = (AudioTrack) track;
                } else {
                    // 向后兼容：先转换为 AudioTrack，然后使用新方法
                    audioTrack = convertToAudioTrack(track);
                }
                
                // 移除该片段时间范围内的所有事件
                List<AudioEvent> eventsToRemove = audioTrack.getEventsInRange(
                        clip.getStartTime(),
                        clip.getEndTime()
                );
                
                for (AudioEvent event : eventsToRemove) {
                    audioTrack.removeEvent(event);
                }
                
                if (LOGGER.isDebugEnabled() && !eventsToRemove.isEmpty()) {
                    LOGGER.debug("[TimelineAudioService] 移除了片段 {} 的 {} 个声音事件",
                            clip.getLabel(), eventsToRemove.size());
                }
            }
        }
    }
}
