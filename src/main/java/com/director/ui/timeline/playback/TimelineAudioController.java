package com.director.ui.timeline.playback;

import com.director.ui.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.ui.timeline.model.audio.AudioTrack;
import com.director.ui.timeline.playback.audio.AudioTrackSampler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 时间线音频控制器 v2.0
 * 
 * <p>负责管理所有音频轨道的采样器，并在时间线播放时协调它们。
 * 
 * <p>核心功能：
 * <ul>
 *   <li>管理所有 AudioTrack 的采样器实例</li>
 *   <li>在播放时调用采样器进行采样</li>
 *   <li>处理轨道添加/删除时的采样器生命周期</li>
 *   <li>处理静音/独奏轨道</li>
 *   <li>支持播放状态变化（播放/暂停/重置）</li>
 * </ul>
 * 
 * <p>设计特点：
 * <ul>
 *   <li>每个 AudioTrack 对应一个 AudioTrackSampler</li>
 *   <li>采样器负责具体的声音触发逻辑</li>
 *   <li>控制器只负责协调和管理</li>
 * </ul>
 * 
 * @author Director Team
 * @version 2.0
 */
public class TimelineAudioController {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineAudioController.class);
    
    private static TimelineAudioController instance;
    
    private TimelineModel timelineModel;
    
    /** 音频轨道采样器映射（轨道对象 -> 采样器） */
    private final Map<AudioTrack, AudioTrackSampler> samplers = new HashMap<>();
    
    /** 是否正在播放 */
    private boolean playing = false;
    
    private TimelineAudioController() {
    }
    
    /**
     * 获取单例实例
     */
    public static TimelineAudioController getInstance() {
        if (instance == null) {
            instance = new TimelineAudioController();
        }
        return instance;
    }
    
    /**
     * 设置时间线模型
     */
    public void setTimelineModel(TimelineModel model) {
        this.timelineModel = model;
        
        // 重新构建采样器映射
        rebuildSamplers();
    }
    
    /**
     * 更新音频播放（每帧调用）
     * 
     * <p>检查当前时间是否有需要播放的声音事件，并播放它们。
     */
    public void update() {
        if (timelineModel == null) {
            return;
        }
        
        float currentTime = timelineModel.getCurrentTime();
        boolean isPlaying = timelineModel.isPlaying();
        
        // 更新播放状态
        if (isPlaying != playing) {
            setPlaying(isPlaying);
        }
        
        // 如果未播放，不处理声音
        if (!isPlaying) {
            return;
        }
        
        // 检查是否有轨道处于独奏模式
        boolean hasSolo = hasSoloTrack();
        
        // 遍历所有采样器并采样
        for (AudioTrackSampler sampler : samplers.values()) {
            AudioTrack track = sampler.getTrack();
            
            // 检查轨道是否被锁定或不可见
            if (track.isLocked() || !track.isVisible()) {
                continue;
            }
            
            // 检查独奏模式
            if (hasSolo && !track.isSolo()) {
                continue;
            }
            
            // 采样当前时间点
            sampler.sample(currentTime);
        }
    }
    
    /**
     * 设置播放状态
     * 
     * @param playing 是否正在播放
     */
    private void setPlaying(boolean playing) {
        this.playing = playing;
        
        // 通知所有采样器播放状态变化
        for (AudioTrackSampler sampler : samplers.values()) {
            sampler.setPlaying(playing);
        }
    }
    
    /**
     * 重建采样器映射
     * 
     * <p>当轨道添加/删除时调用，确保采样器与轨道同步。
     */
    private void rebuildSamplers() {
        if (timelineModel == null) {
            samplers.clear();
            return;
        }
        
        // 收集当前所有音频轨道
        Set<AudioTrack> currentTracks = new HashSet<>();
        for (TimelineTrack track : timelineModel.getAllTracks()) {
            if (track != null && track.getType() == TrackType.AUDIO && track instanceof AudioTrack) {
                currentTracks.add((AudioTrack) track);
            }
        }
        
        // 移除已删除轨道的采样器
        samplers.entrySet().removeIf(entry -> !currentTracks.contains(entry.getKey()));
        
        // 添加新轨道的采样器
        for (AudioTrack audioTrack : currentTracks) {
            if (!samplers.containsKey(audioTrack)) {
                AudioTrackSampler sampler = new AudioTrackSampler(audioTrack);
                sampler.setPlaying(playing);
                samplers.put(audioTrack, sampler);
                
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[TimelineAudioController] 为音频轨道 {} 创建采样器", audioTrack.getName());
                }
            }
        }
    }
    
    /**
     * 检查是否有轨道处于独奏模式
     */
    private boolean hasSoloTrack() {
        if (timelineModel == null) {
            return false;
        }
        
        for (TimelineTrack track : timelineModel.getAllTracks()) {
            if (track != null && track.isSolo()) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 重置播放状态（当时间线停止或重置时调用）
     */
    public void reset() {
        // 重置所有采样器
        for (AudioTrackSampler sampler : samplers.values()) {
            sampler.reset();
        }
        
        playing = false;
    }
    
    /**
     * 通知轨道已添加（用于更新采样器映射）
     */
    public void onTrackAdded(TimelineTrack track) {
        if (track != null && track.getType() == TrackType.AUDIO && track instanceof AudioTrack) {
            rebuildSamplers();
        }
    }
    
    /**
     * 通知轨道已删除（用于更新采样器映射）
     */
    public void onTrackRemoved(TimelineTrack track) {
        if (track != null && track.getType() == TrackType.AUDIO) {
            rebuildSamplers();
        }
    }
}
