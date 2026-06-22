package com.director.ui.timeline.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineClip;

/**
 * 移动片段命令。
 * 
 * <p>优化：继承 BaseTimelineCommand 以确保模型更新和一致性。
 */
public class MoveClipCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(MoveClipCommand.class);

    public interface Clip {
        float getStartTime();
        void setStartTime(float time);
        float getEndTime();
        void setEndTime(float time);
        float getLength();
    }

    private final Clip clip;
    private final float oldStartTime;
    private final float oldEndTime;
    private final float newStartTime;
    private final float newEndTime;

    /**
     * 构造函数（自动从 clip 获取 old 值）
     * 
     * <p>注意：此构造函数假设 clip 的当前时间就是 old 值。
     * 如果 clip 已经被修改，请使用另一个构造函数显式传递 old 值。
     * 
     * @param model Timeline 模型实例（用于自动模型更新）
     * @param clip 片段
     * @param newStartTime 新的开始时间
     * @param newEndTime 新的结束时间
     */
    public MoveClipCommand(TimelineModel model, Clip clip, float newStartTime, float newEndTime) {
        super(model, "clip_moved");
        this.clip = clip;
        this.oldStartTime = clip.getStartTime();
        this.oldEndTime = clip.getEndTime();
        this.newStartTime = newStartTime;
        this.newEndTime = newEndTime;
    }
    
    /**
     * 构造函数（显式传递 old 值）
     * 
     * <p>★ 推荐使用：当 clip 已经被修改时，使用此构造函数显式传递拖动开始时的值
     * 
     * @param model Timeline 模型实例（用于自动模型更新）
     * @param clip 片段
     * @param oldStartTime 拖动开始时的开始时间
     * @param oldEndTime 拖动开始时的结束时间
     * @param newStartTime 拖动结束时的开始时间（当前值）
     * @param newEndTime 拖动结束时的结束时间（当前值）
     */
    public MoveClipCommand(TimelineModel model, Clip clip, float oldStartTime, float oldEndTime, float newStartTime, float newEndTime) {
        super(model, "clip_moved");
        this.clip = clip;
        this.oldStartTime = oldStartTime;
        this.oldEndTime = oldEndTime;
        this.newStartTime = newStartTime;
        this.newEndTime = newEndTime;
    }

    @Override
    protected void doExecute() {
        if (clip == null) {
            LOGGER.warn("[Timeline] 移动片段失败：clip 为 null");
            return;
        }
        TimelineClip timelineClip = (clip instanceof ClipAdapter adapter) ? adapter.getTimelineClip() : null;
        clip.setStartTime(newStartTime);
        clip.setEndTime(newEndTime);
        // 同步：自动生成的音频事件默认跟随片段移动（可对单个事件取消链接）
        if (timelineClip != null) {
            com.director.ui.timeline.audio.TimelineAudioService.getInstance()
                    .onClipTimeRangeChanged(timelineClip, oldStartTime, oldEndTime, newStartTime, newEndTime);
        }
        LOGGER.debug("[Timeline] 移动片段: {:.2f}-{:.2f} -> {:.2f}-{:.2f}",
                oldStartTime, oldEndTime, newStartTime, newEndTime);
    }

    @Override
    protected void doUndo() {
        if (clip == null) {
            LOGGER.warn("[Timeline] 撤销移动片段失败：clip 为 null");
            return;
        }
        TimelineClip timelineClip = (clip instanceof ClipAdapter adapter) ? adapter.getTimelineClip() : null;
        clip.setStartTime(oldStartTime);
        clip.setEndTime(oldEndTime);
        if (timelineClip != null) {
            com.director.ui.timeline.audio.TimelineAudioService.getInstance()
                    .onClipTimeRangeChanged(timelineClip, newStartTime, newEndTime, oldStartTime, oldEndTime);
        }
        LOGGER.debug("[Timeline] 撤销移动片段");
    }

    @Override
    public String getDescription() {
        return String.format("移动片段 (%.2fs -> %.2fs)", oldStartTime, newStartTime);
    }

    @Override
    public boolean canUndo() {
        return true;
    }
}


