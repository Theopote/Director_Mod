package com.chronoblocks.ui.timeline.state;

import com.chronoblocks.ui.timeline.commands.DeleteClipCommand;
import com.chronoblocks.ui.timeline.commands.TimelineCommandHistory;
import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;

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
        this.clip = new TimelineClip(clip);
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
        if (clip == null) {
            return;
        }
        this.clip = new TimelineClip(clip);
        this.isCut = false;
        LOGGER.info("[Timeline] 复制片段: {}", clip.getLabel());
    }

    /**
     * 粘贴片段到指定轨道
     */
    public void pasteToTrack(TimelineTrack track, TimelineModel model) {
        if (hasClip() || track == null) {
            return;
        }
        
        // 创建副本
        TimelineClip newClip = new TimelineClip(clip);
        
        // 调整时间以避免重叠
        float pasteTime = model.getCurrentTime();
        newClip.setStartTime(pasteTime);
        newClip.setEndTime(pasteTime + newClip.getLength());
        
        // 添加到轨道
        track.addClip(newClip);
        model.setSelectedClip(newClip);
        
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
        if (hasClip()) {
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
        clip = null;
        isCut = false;
    }

    public boolean hasClip() {
        return clip == null;
    }
}

