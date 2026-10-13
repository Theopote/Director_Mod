package com.director.ui.timeline.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineModel;

/**
 * 添加关键帧命令。
 * 
 * <p>优化：继承 BaseTimelineCommand 以确保模型更新和一致性。
 */
public class AddKeyframeCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(AddKeyframeCommand.class);
    private static final float KEYFRAME_TOLERANCE = 0.0001f;

    private final TimelineClip clip;
    private final float time;
    private boolean added = false;
    private com.director.lighting.LightKeyframe lightKey;
    public com.director.lighting.LightKeyframe getLightKeyframe() { return lightKey; }

    public AddKeyframeCommand(TimelineModel model, TimelineClip clip, float time) {
        super(model, "keyframe_added");
        this.clip = clip;
        this.time = time;
    }

    @Override
    protected void doExecute() {
        if (clip == null) {
            LOGGER.warn("[Timeline] 添加关键帧失败：clip 为 null");
            return;
        }
        if(clip instanceof com.director.lighting.LightTimelineClip light) {
            added=false;
            if(!com.director.lighting.LightLifecycle.containsClip(model,light) || light.getParentTrack().isLocked()
                || !Float.isFinite(time) || time<clip.getStartTime() || time>clip.getEndTime()
                || clip.findKeyframeAt(time,KEYFRAME_TOLERANCE)!=null) return;
            var base=model.getPointLight(light.lightId());if(base==null) return;
            if(lightKey==null) lightKey=new com.director.lighting.LightKeyframe(time,light.sample(base,time));
            clip.addKeyframe(lightKey);added=true;return;
        }
        if (clip.hasKeyframeAt(time, KEYFRAME_TOLERANCE)) {
            added = false;
            LOGGER.debug("[Timeline] 关键帧已存在，跳过添加: {:.2f}", time);
            return;
        }
        clip.addKeyframe(time);
        added = true;
        LOGGER.debug("[Timeline] 添加关键帧 {:.2f}", time);
    }

    @Override
    protected void doUndo() {
        if (clip == null) {
            LOGGER.warn("[Timeline] 撤销添加关键帧失败：clip 为 null");
            return;
        }
        if (!added) {
            return;
        }
        if(lightKey!=null) clip.removeKeyframe(lightKey.getId());else clip.removeKeyframe(time);
        LOGGER.debug("[Timeline] 撤销关键帧 {:.2f}", time);
    }

    @Override
    public String getDescription() {
        return String.format("添加关键帧(%.2fs)", time);
    }

    @Override
    public boolean canUndo() {
        return added;
    }
}


