package com.chronoblocks.core.animation;

import java.util.ArrayList;
import java.util.List;

/**
 * ChronoBlocks 全局动画状态（v4.5 一体化交互系统）
 * 
 * 统一的动画状态管理中心，供所有 UI 面板共享：
 * - 播放状态（playing / paused / stopped）
 * - 时间轴状态（currentTime / duration）
 * - 选择状态（track / keyframe / container）
 * 
 * @author ChronoBlocks Team
 * @version 4.5
 */
public class AnimationState {
    private static final AnimationState INSTANCE = new AnimationState();

    // ========== 动画剪辑数据 ==========
    private AnimationClip clip;
    
    // ========== 播放状态 ==========
    private boolean playing = false;
    private float currentTime = 0f;
    private float duration = 10.0f; // 默认持续时间

    // ========== 选择状态 ==========
    private int selectedTrack = -1;
    private int selectedKeyframe = -1;

    // ========== 容器选择 ==========
    private String selectedContainerId = "";

    private AnimationState() {
        // Bootstrap a demo clip if none assigned
        AnimationClip demo = new AnimationClip("Demo");
        Track t1 = new Track("Track 1", "Block_001", false);
        t1.addKeyframe(new Keyframe(0f));
        t1.addKeyframe(new Keyframe(3f));
        t1.addKeyframe(new Keyframe(7f));
        demo.addTrack(t1);
        this.clip = demo;
        
        // 从剪辑计算持续时间
        if (clip != null) {
            this.duration = Math.max(clip.computeDuration(), 10.0f);
        }
    }

    public static AnimationState get() { return INSTANCE; }

    // ========== 动画剪辑管理 ==========
    
    public AnimationClip getClip() { return clip; }
    
    public void setClip(AnimationClip clip) { 
        this.clip = clip; 
        clearSelection();
        // 更新持续时间
        if (clip != null) {
            this.duration = Math.max(clip.computeDuration(), 10.0f);
        }
    }

    // ========== 播放状态管理 ==========
    
    public boolean isPlaying() { return playing; }
    
    public void setPlaying(boolean playing) { 
        this.playing = playing; 
    }

    public float getCurrentTime() { return currentTime; }
    
    public void setCurrentTime(float currentTime) { 
        this.currentTime = Math.max(0f, Math.min(currentTime, duration)); 
    }

    public float getDuration() { 
        // 动态计算：取剪辑持续时间和设置值的最大值
        if (clip != null) {
            float clipDuration = clip.computeDuration();
            return Math.max(clipDuration, duration);
        }
        return duration; 
    }
    
    public void setDuration(float duration) { 
        this.duration = Math.max(0.1f, duration); 
    }

    // ========== 选择状态管理 ==========

    public int getSelectedTrack() { return selectedTrack; }
    public void setSelectedTrack(int selectedTrack) { this.selectedTrack = selectedTrack; this.selectedKeyframe = -1; }

    public int getSelectedKeyframe() { return selectedKeyframe; }
    public void setSelectedKeyframe(int selectedKeyframe) { this.selectedKeyframe = selectedKeyframe; }

    public String getSelectedContainerId() { return selectedContainerId; }
    public void setSelectedContainerId(String id) { this.selectedContainerId = id != null ? id : ""; }

    public Track getSelectedTrackRef() {
        List<Track> tracks = clip != null ? new ArrayList<>(clip.getTracks()) : null;
        if (tracks == null) return null;
        if (selectedTrack < 0 || selectedTrack >= tracks.size()) return null;
        return tracks.get(selectedTrack);
    }

    public Keyframe getSelectedKeyframeRef() {
        Track t = getSelectedTrackRef();
        if (t == null) return null;
        List<Keyframe> kfs = t.getKeyframes();
        if (selectedKeyframe < 0 || selectedKeyframe >= kfs.size()) return null;
        return kfs.get(selectedKeyframe);
    }

    public void clearSelection() {
        selectedTrack = -1;
        selectedKeyframe = -1;
    }
}
