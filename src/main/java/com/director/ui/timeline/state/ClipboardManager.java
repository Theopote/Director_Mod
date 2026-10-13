package com.director.ui.timeline.state;

import com.director.core.DirectorServices;
import com.director.ui.timeline.commands.DeleteClipCommand;
import com.director.ui.timeline.commands.TimelineCommandHistory;
import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 时间线剪贴板管理器（单例）
 */
public final class ClipboardManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClipboardManager.class);
    private static final ClipboardManager INSTANCE = new ClipboardManager();
    
    public static ClipboardManager getInstance() {
        return INSTANCE;
    }
    
    private TimelineClip clip;
    private boolean isCut;
    private com.director.lighting.PointLight lightAsset;
    private void captureLight(TimelineClip clip,TimelineModel model) {
        lightAsset=clip instanceof com.director.lighting.LightTimelineClip light && model!=null
            ? model.getPointLight(light.lightId()) : null;
    }
    private java.util.function.Consumer<TimelineClip> initializeAudio = ignored -> {};

    private ClipboardManager() {
    }

    /**
     * 剪切片段
     */
    public void cut(TimelineClip clip, TimelineTrack track, TimelineModel model, TimelineCommandHistory history) {
        if (clip == null || track == null) {
            return;
        }
        
        // ★ 轨道锁定功能：如果轨道锁定，禁止剪切
        if (track.isLocked()) {
            LOGGER.info("[Timeline] 轨道已锁定，禁止剪切片段 (轨道: {})", track.getName());
            return;
        }
        
        // 复制到剪贴板
        captureLight(clip,model);
        this.clip = com.director.timeline.model.TimelineClipCopies.copy(clip);
        this.initializeAudio = com.director.ui.timeline.audio.TimelineAudioService.getInstance().captureAudioCopy(clip);
        this.isCut = true;
        
        // 删除原片段（使用命令支持撤销）
        DeleteClipCommand cmd = new DeleteClipCommand(model, clip, track.getClips());
        history.executeCommand(cmd);
        
        model.setSelectedClip(null);
        LOGGER.info("[Timeline] 剪切片段: {}", clip.getLabel());
    }

    /**
     * 复制片段
     */
    public void copy(TimelineClip clip) {
        copy(clip,clip instanceof com.director.lighting.LightTimelineClip?com.director.ui.lighting.PointLightEditor.model():null);
    }
    public void copy(TimelineClip clip,TimelineModel model) {
        if (clip == null) {
            return;
        }
        captureLight(clip,model);
        this.clip = com.director.timeline.model.TimelineClipCopies.copy(clip);
        this.initializeAudio = com.director.ui.timeline.audio.TimelineAudioService.getInstance().captureAudioCopy(clip);
        this.isCut = false;
        LOGGER.info("[Timeline] 复制片段: {}", clip.getLabel());
    }

    /**
     * 粘贴片段到指定轨道
     */
    public void pasteToTrack(TimelineTrack track, TimelineModel model) {
        if (!hasClip() || track == null || track.isLocked()) {
            return;
        }

        if (!canPasteToTrack(track)) {
            LOGGER.info("[Timeline] 剪贴板片段不能粘贴到轨道: clip={}, track={}",
                clip.getLabel(), track.getName());
            return;
        }
        
        // 创建副本
        TimelineClip newClip = com.director.timeline.model.TimelineClipCopies.copy(clip);
        
        // 调整时间以避免重叠
        float pasteTime = model.getCurrentTime();
        float delta = pasteTime - newClip.getStartTime();
        newClip.offsetTime(delta);
        
        // 添加到轨道
        var insertion = new com.director.ui.timeline.commands.InsertClipCommand(model, track, newClip, initializeAudio, lightAsset);
        com.director.ui.timeline.commands.TimelineCommandBridge.execute(insertion);
        if (!insertion.canUndo()) {
            LOGGER.info("[Timeline] 粘贴片段失败，轨道拒绝接收: {} -> {}",
                newClip.getLabel(), track.getName());
            return;
        }
        model.setSelectedClip(newClip);
        model.markDirty();
        model.markDurationDirty();
        
        LOGGER.info("[Timeline] 粘贴片段: {} 到轨道 {}", newClip.getLabel(), track.getName());
        
        // 如果是剪切操作，清空剪贴板
        if (isCut) {
            clear();
        }
    }

    /**
     * 粘贴片段（自动选择目标轨道）
     */
    public void paste(TimelineModel model) {
        if (!hasClip()) {
            return;
        }
        
        TimelineTrack targetTrack = null;
        
        // 优先粘贴到选中片段所在的轨道
        if (model.getSelectedClip() != null) {
            targetTrack = model.findTrackForClip(model.getSelectedClip());
        }
        
        // 如果没有选中片段，粘贴到第一个轨道
        if (targetTrack == null && !model.getTracks().isEmpty()) {
            targetTrack = model.getTracks().getFirst();
        }
        
        if (targetTrack != null) {
            pasteToTrack(targetTrack, model);
        }
    }

    /**
     * 删除选中的片段
     */
    public void deleteSelected(TimelineModel model, TimelineCommandHistory history) {
        TimelineClip selectedClip = model.getSelectedClip();
        if (selectedClip == null) {
            return;
        }
        
        TimelineTrack track = model.findTrackForClip(selectedClip);
        if (track != null) {
            // ★ 轨道锁定功能：如果轨道锁定，禁止删除
            if (track.isLocked()) {
                LOGGER.info("[Timeline] 轨道已锁定，禁止删除片段 (轨道: {})", track.getName());
                return;
            }
            
            DeleteClipCommand cmd = new DeleteClipCommand(model, selectedClip, track.getClips());
            history.executeCommand(cmd);
            model.setSelectedClip(null);
            LOGGER.info("[Timeline] 删除片段");
        }
    }

    public void clear() {
        clip = null;lightAsset=null;
        initializeAudio = ignored -> {};
        isCut = false;
    }

    public boolean hasClip() {
        return clip != null;
    }

    private boolean canPasteToTrack(TimelineTrack track) {
        if (clip == null || track == null) {
            return false;
        }
        if (clip instanceof com.director.lighting.LightTimelineClip) return track.getType()==TrackType.LIGHT;
        if (track.getType()==TrackType.LIGHT) return false;
        if (clip instanceof com.director.timeline.model.entity.EntityTimelineClip) return track.getType() == TrackType.ACTOR;
        if (clip instanceof com.director.timeline.model.audio.AudioTimelineClip) return track.getType() == TrackType.AUDIO;
        if (clip.getBoundContainerId() == null) {
            return true;
        }
        if (clip instanceof com.director.timeline.model.audio.AudioTimelineClip) {
            return track.getType() == TrackType.AUDIO;
        }
        if (track.getType() != TrackType.CONTAINER) {
            return false;
        }
        return isCut || !DirectorServices.get().containerTimeline()
            .isContainerOnTimeline(clip.getBoundContainerId());
    }
}

