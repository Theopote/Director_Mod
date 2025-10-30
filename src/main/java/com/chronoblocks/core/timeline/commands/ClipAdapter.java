package com.chronoblocks.core.timeline.commands;

import com.chronoblocks.ui.timeline.TimelineClip;

/**
 * TimelineClip 到 MoveClipCommand.Clip 接口的适配器
 * 
 * 简化命令创建，避免冗长的匿名内部类
 * 
 * @author ChronoBlocks Team
 * @version 7.4
 */
public class ClipAdapter implements MoveClipCommand.Clip {
    private final TimelineClip clip;

    public ClipAdapter(TimelineClip clip) {
        this.clip = clip;
    }
    
    @Override
    public float getStartTime() {
        return clip.getStartTime();
    }
    
    @Override
    public void setStartTime(float time) {
        clip.setStartTime(time);
    }
    
    @Override
    public float getEndTime() {
        return clip.getEndTime();
    }
    
    @Override
    public void setEndTime(float time) {
        clip.setEndTime(time);
    }
    
    @Override
    public float getLength() {
        return clip.getLength();
    }
}

