package com.director.ui.timeline.commands;

import com.director.ui.timeline.model.TimelineModel;
import com.director.ui.timeline.model.TimelineTrack;
import com.director.ui.timeline.model.camera.CameraKeyframe;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 修改 Bezier 控制柄命令（支持撤销/重做）
 * 
 * <p>用于修改摄像机关键帧的 Bezier 控制柄（进入和离开控制柄）。
 * 此命令确保所有控制柄修改都可以被撤销，保持操作的完整性。
 * 
 * @author Director Team
 * @version 1.0
 */
public class ChangeBezierHandleCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChangeBezierHandleCommand.class);

    private final TimelineTrack track;
    private final CameraKeyframe keyframe;
    private final Vector3f oldInHandle;
    private final Vector3f oldOutHandle;
    private final Vector3f newInHandle;
    private final Vector3f newOutHandle;

    /**
     * 创建修改控制柄命令
     * 
     * @param model Timeline 模型
     * @param track 关键帧所在的轨道
     * @param keyframe 要修改的关键帧
     * @param newInHandle 新的进入控制柄
     * @param newOutHandle 新的离开控制柄
     */
    public ChangeBezierHandleCommand(
            TimelineModel model,
            TimelineTrack track,
            CameraKeyframe keyframe,
            Vector3f newInHandle,
            Vector3f newOutHandle) {
        super(model, "bezier_handle_changed");
        this.track = Objects.requireNonNull(track, "Track cannot be null");
        this.keyframe = Objects.requireNonNull(keyframe, "Keyframe cannot be null");
        
        // 保存旧的控制柄（深拷贝）
        this.oldInHandle = new Vector3f(keyframe.getBezierInHandle());
        this.oldOutHandle = new Vector3f(keyframe.getBezierOutHandle());
        
        // 保存新的控制柄（深拷贝）
        this.newInHandle = newInHandle != null ? new Vector3f(newInHandle) : new Vector3f();
        this.newOutHandle = newOutHandle != null ? new Vector3f(newOutHandle) : new Vector3f();
    }

    @Override
    protected void doExecute() {
        if (keyframe == null) {
            LOGGER.warn("[Timeline] 修改控制柄失败：keyframe 为 null");
            return;
        }

        // 设置新的控制柄
        keyframe.setBezierInHandle(newInHandle);
        keyframe.setBezierOutHandle(newOutHandle);

        LOGGER.debug("[Timeline] 修改控制柄: 关键帧 @ {}s", keyframe.getTime());
    }

    @Override
    protected void doUndo() {
        if (keyframe == null) {
            return;
        }

        // 恢复旧的控制柄
        keyframe.setBezierInHandle(oldInHandle);
        keyframe.setBezierOutHandle(oldOutHandle);

        LOGGER.debug("[Timeline] 撤销修改控制柄: 关键帧 @ {}s", keyframe.getTime());
    }

    @Override
    public String getDescription() {
        return String.format("修改控制柄: %s @ %.2fs", 
                keyframe != null ? keyframe.getName() : "未知", 
                keyframe != null ? keyframe.getTime() : 0f);
    }
}

