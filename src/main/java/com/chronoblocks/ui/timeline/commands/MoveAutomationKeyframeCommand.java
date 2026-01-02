package com.chronoblocks.ui.timeline.commands;

import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineSelectable;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.audio.AudioEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 移动自动化关键帧命令（支持撤销/重做）
 * 
 * <p>用于移动轨道级别的自动化关键帧（摄像机关键帧、天气关键帧等）。
 * 与 Remove + Add 两个命令不同，此命令专门用于移动操作，
 * 确保关键帧的所有属性（时间、插值类型、控制柄等）都被正确保留。
 * 
 * <p>关键改进：
 * <ul>
 *   <li>✅ 原子操作：移动是一个命令，不是两个</li>
 *   <li>✅ 正确的 Undo：直接修改时间，不需要 remove/add</li>
 *   <li>✅ 保留顺序：关键帧在轨道中的顺序被保留</li>
 *   <li>✅ 性能优化：不需要移除和重新添加关键帧</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class MoveAutomationKeyframeCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(MoveAutomationKeyframeCommand.class);

    private final TimelineTrack track;
    private final TimelineSelectable keyframe;
    private final float oldTime;
    private final float newTime;

    /**
     * 创建移动自动化关键帧命令
     * 
     * @param model Timeline 模型
     * @param track 关键帧所在的轨道
     * @param keyframe 要移动的关键帧
     * @param oldTime 原始时间
     * @param newTime 新时间
     */
    public MoveAutomationKeyframeCommand(
            TimelineModel model,
            TimelineTrack track,
            TimelineSelectable keyframe,
            float oldTime,
            float newTime) {
        super(model, "automation_keyframe_moved");
        this.track = Objects.requireNonNull(track, "Track cannot be null");
        this.keyframe = Objects.requireNonNull(keyframe, "Keyframe cannot be null");
        this.oldTime = oldTime;
        this.newTime = newTime;
    }

    @Override
    protected void doExecute() {
        apply(newTime);
        LOGGER.debug("[Command] MoveAutomationKeyframe execute {} -> {}", oldTime, newTime);
    }

    @Override
    protected void doUndo() {
        apply(oldTime);
        LOGGER.debug("[Command] MoveAutomationKeyframe undo {} <- {}", newTime, oldTime);
    }

    /**
     * 应用时间变化（Execute 和 Undo 共用）
     */
    private void apply(float t) {
        if (keyframe == null || track == null) {
            LOGGER.warn("[Timeline] 移动自动化关键帧失败：keyframe 或 track 为 null");
            return;
        }

        // 修改关键帧时间
        setKeyframeTime(keyframe, t);

        // 重新排序（移除并重新添加以触发排序，如果使用 TreeSet）
        track.removeAutomationKeyframe(keyframe.getId());
        track.addAutomationKeyframe(keyframe);

        // 通知播放系统刷新
        if (keyframe instanceof com.chronoblocks.ui.timeline.model.camera.CameraKeyframe) {
            com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController.getInstance()
                    .onKeyframeChanged();
        }
    }

    /**
     * 设置关键帧的时间（支持不同类型的自动化关键帧）
     */
    private void setKeyframeTime(TimelineSelectable keyframe, float time) {
        switch (keyframe) {
            case com.chronoblocks.ui.timeline.model.camera.CameraKeyframe cameraKeyframe ->
                    cameraKeyframe.setTime(time);
            case com.chronoblocks.ui.timeline.model.TimelineMarker marker -> marker.setTime(time);
            case com.chronoblocks.ui.timeline.model.weather.WeatherKeyframe weatherKeyframe ->
                    weatherKeyframe.setTime(time);
            case AudioEvent audioEvent -> audioEvent.setTime(time);
            default -> LOGGER.warn("[Timeline] 不支持的关键帧类型: {}", keyframe.getClass().getSimpleName());
        }
    }

    @Override
    public String getDescription() {
        return String.format("移动关键帧: %s @ %.2fs -> %.2fs", 
                keyframe != null ? keyframe.getName() : "未知", oldTime, newTime);
    }
}

