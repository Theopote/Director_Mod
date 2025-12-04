package com.chronoblocks.ui.timeline.menu;

import com.chronoblocks.ui.panels.TimelinePanel;
import com.chronoblocks.ui.timeline.commands.AddAutomationKeyframeCommand;
import com.chronoblocks.ui.timeline.commands.BindClipToContainerCommand;
import com.chronoblocks.ui.timeline.commands.ChangeKeyframeInterpolationCommand;
import com.chronoblocks.ui.timeline.commands.DeleteClipCommand;
import com.chronoblocks.ui.timeline.commands.DeleteTrackCommand;
import com.chronoblocks.ui.timeline.commands.DuplicateClipCommand;
import com.chronoblocks.ui.timeline.commands.MoveTrackCommand;
import com.chronoblocks.ui.timeline.commands.RemoveAutomationKeyframeCommand;
import com.chronoblocks.ui.timeline.commands.RemoveKeyframeCommand;
import com.chronoblocks.ui.timeline.commands.SetClipLengthCommand;
import com.chronoblocks.ui.timeline.commands.TimelineCommandHistory;
import com.chronoblocks.ui.timeline.commands.ToggleTrackLockCommand;
import com.chronoblocks.ui.timeline.commands.ToggleTrackMuteCommand;
import com.chronoblocks.ui.timeline.commands.ToggleTrackSoloCommand;
import com.chronoblocks.ui.timeline.dialog.TrackRenameDialog;
import com.chronoblocks.ui.timeline.interaction.TimelineHitResult;
import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.timeline.model.TimelineInterpolation;
import com.chronoblocks.ui.timeline.model.TimelineMarker;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.TrackType;
import com.chronoblocks.ui.timeline.model.camera.CameraKeyframe;
import com.chronoblocks.ui.timeline.model.weather.WeatherKeyframe;
import com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Timeline 右键菜单工厂。
 */
public class TimelineContextMenuFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineContextMenuFactory.class);

    private final TimelineCommandHistory commandHistory;
    private final TimelineModel model;
    private final TimelinePanel panel;  // 可选引用，用于调用面板方法

    private TimelineContextMenuFactory(
            TimelineCommandHistory commandHistory,
            TimelineModel model,
            TimelinePanel panel) {
        this.commandHistory = Objects.requireNonNull(commandHistory, "commandHistory");
        this.model = Objects.requireNonNull(model, "model");
        this.panel = panel;
    }

    public static TimelineContextMenuFactory forPanel(
            TimelinePanel panel,
            TimelineModel model,
            TimelineCommandHistory commandHistory) {
        return new TimelineContextMenuFactory(commandHistory, model, panel);
    }

    /**
     * 根据命中结果构建上下文菜单。
     */
    public TimelineContextMenu buildContextMenu(TimelineHitResult hit) {
        if (hit == null) {
            LOGGER.warn("[Timeline] buildContextMenu: hit 为空");
            return null;
        }

        return switch (hit.type()) {
            case TRACK_HEADER -> createTrackHeaderMenu(hit.track(), hit.trackIndex());
            case CLIP -> createClipMenu(hit.clip(), hit.track() != null ? hit.track() : model.findTrackForClip(hit.clip()));
            case KEYFRAME -> createKeyframeMenu(hit.clip(), hit.time());
            case AUTOMATION_KEYFRAME -> createAutomationKeyframeMenu(hit.automationKeyframe(), hit.track());
            case EMPTY -> createEmptyAreaMenu(hit.time());
            default -> null;
        };
    }

    // =========================================================
    // Clip 菜单
    // =========================================================
    public TimelineContextMenu createClipMenu(TimelineClip clip, TimelineTrack track) {
        if (clip == null || track == null) {
            return TimelineContextMenuBuilder
                    .forClip(commandHistory)
                    .disabledItem("无片段")
                    .build();
        }

        String clipName = clip.getLabel();
        UUID containerId = clip.getBoundContainerId();
        DeleteClipCommand deleteCmd = new DeleteClipCommand(model, clip, track.getClips());

        TimelineContextMenuBuilder builder = TimelineContextMenuBuilder
                .forClip(commandHistory)
                .disabledItem(String.format("片段: %s", clipName))
                .disabledItem(String.format("@ %.2fs - %.2fs", clip.getStartTime(), clip.getEndTime()))
                .separator()
                .command("复制片段", new DuplicateClipCommand(model, track, clip), "Ctrl+D")
                .command("删除片段", deleteCmd, "Del")
                .separator()
                .command("设置时长 1s", new SetClipLengthCommand(model, clip, 1.0f))
                .command("设置时长 2s", new SetClipLengthCommand(model, clip, 2.0f))
                .command("设置时长 5s", new SetClipLengthCommand(model, clip, 5.0f))
                .separator();

        if (panel != null) {
            builder.item("重命名片段...", () -> panel.requestClipRename(clip));
            builder.item("绑定到容器...", () -> panel.requestClipContainerBinding(clip));
        }

        builder.command("取消容器绑定",
                new BindClipToContainerCommand(model, clip, null, null),
                null,
                containerId != null);

        return builder.open();
    }

    // =========================================================
    // Keyframe 菜单
    // =========================================================
    public TimelineContextMenu createKeyframeMenu(TimelineClip clip, float keyframeTime) {
        TimelineTrack track = model.findTrackForClip(clip);
        if (track == null || clip == null) {
            LOGGER.warn("[Timeline] 创建 Keyframe 菜单失败：track 或 clip 为 null");
            return TimelineContextMenuBuilder
                    .forKeyframe(commandHistory)
                    .disabledItem("无关键帧")
                    .build();
        }

        return TimelineContextMenuBuilder
                .forKeyframe(commandHistory)
                .disabledItem(String.format("关键帧 @ %.3fs", keyframeTime))
                .separator()
                .command("删除关键帧",
                        new RemoveKeyframeCommand(model, clip, keyframeTime),
                        "Del")
                .separator()
                .submenu("插值方式")
                    .command("线性（匀速）",
                        new ChangeKeyframeInterpolationCommand(model, track, clip, keyframeTime, TimelineInterpolation.LINEAR))
                    .command("平滑（缓入缓出）",
                        new ChangeKeyframeInterpolationCommand(model, track, clip, keyframeTime, TimelineInterpolation.SMOOTH))
                    .command("阶梯（保持再跳变）",
                        new ChangeKeyframeInterpolationCommand(model, track, clip, keyframeTime, TimelineInterpolation.STEP))
                .end()
                .open();
    }

    /**
     * 创建自动化关键帧的右键菜单（摄像机关键帧、天气关键帧等）
     */
    public TimelineContextMenu createAutomationKeyframeMenu(
            com.chronoblocks.ui.timeline.model.TimelineSelectable keyframe,
            com.chronoblocks.ui.timeline.model.TimelineTrack track) {
        if (keyframe == null || track == null) {
            LOGGER.warn("[Timeline] 创建自动化关键帧菜单失败：keyframe 或 track 为 null");
            return TimelineContextMenuBuilder
                    .custom(commandHistory, "AutomationKeyframeMenu")
                    .disabledItem("无关键帧")
                    .build();
        }

        String menuLabel = keyframe.getName();
        switch (keyframe) {
            case CameraKeyframe cameraKeyframe -> menuLabel = "摄像机关键帧";
            case WeatherKeyframe weatherKeyframe -> menuLabel = "天气关键帧";
            case TimelineMarker timelineMarker -> menuLabel = "标记";
            default -> {
            }
        }

        return TimelineContextMenuBuilder
                .custom(commandHistory, "AutomationKeyframeMenu")
                .disabledItem(String.format("%s @ %.3fs", menuLabel, keyframe.getStartTime()))
                .separator()
                .command("删除关键帧",
                        new RemoveAutomationKeyframeCommand(model, track, keyframe),
                        "Del")
                .open();
    }

    // =========================================================
    // Track 菜单
    // =========================================================
    public TimelineContextMenu createTrackHeaderMenu(TimelineTrack track) {
        int trackIndex = model.getTracks().indexOf(track);
        return createTrackHeaderMenu(track, trackIndex);
    }

    private TimelineContextMenu createTrackHeaderMenu(TimelineTrack track, int trackIndex) {
        if (track == null) {
            return TimelineContextMenuBuilder
                    .forTrack(commandHistory)
                    .disabledItem("无轨道")
                    .build();
        }

        List<TimelineTrack> tracks = model.getTracks();
        boolean canMoveUp = trackIndex > 0;
        boolean canMoveDown = trackIndex >= 0 && trackIndex < tracks.size() - 1;

        String trackName = track.getName();
        DeleteTrackCommand deleteTrackCmd = new DeleteTrackCommand(model, track, tracks);

        TimelineContextMenuBuilder builder = TimelineContextMenuBuilder
                .forTrack(commandHistory)
                .disabledItem(String.format("轨道: %s", trackName))
                .disabledItem(String.format("类型: %s", track.getType().getDisplayName()))
                .separator()
                .command(track.isMuted() ? "取消静音" : "静音",
                    new ToggleTrackMuteCommand(model, track),
                    "M")
                .command(track.isSolo() ? "取消独奏" : "独奏",
                    new ToggleTrackSoloCommand(model, track),
                    "S")
                .command(track.isLocked() ? "解锁轨道" : "锁定轨道",
                    new ToggleTrackLockCommand(model, track))
                .separator()
                .item("重命名轨道...", () -> TrackRenameDialog.open(track))
                .separator()
                .command("上移轨道",
                    new MoveTrackCommand(model, trackIndex, trackIndex - 1),
                    "Alt+Up",
                    canMoveUp)
                .command("下移轨道",
                    new MoveTrackCommand(model, trackIndex, trackIndex + 1),
                    "Alt+Down",
                    canMoveDown)
                .separator()
                .command("删除轨道", deleteTrackCmd, "Del")
                .separator();

        addTrackTypeSpecificMenu(builder, track);
        return builder.open();
    }

    // =========================================================
    // 空白区域菜单
    // =========================================================
    public TimelineContextMenu createEmptyAreaMenu(float time) {
        TimelineContextMenuBuilder builder = TimelineContextMenuBuilder
                .custom(commandHistory, "TimelineEmptyAreaMenu")
                .disabledItem(String.format("时间: %.3fs", time))
                .separator();

        builder.item("新增轨道", () -> {
            if (panel != null) {
                panel.onAddTrackClicked();
            }
        }, "Ctrl+T");

        return builder.open();
    }

    // =========================================================
    // Track Type Specific
    // =========================================================
    private void addTrackTypeSpecificMenu(TimelineContextMenuBuilder builder, TimelineTrack track) {
        TrackType type = track.getType();
        if (type == null) {
            return;
        }

        float time = model.getCurrentTime();
        switch (type) {
            case CONTAINER -> {
                if (type.supportsMarkers()) {
                    builder.submenu("标记")
                        .command("在当前位置添加标记",
                            new AddAutomationKeyframeCommand(
                                model,
                                track,
                                new TimelineMarker("Marker @" + formatTime(time), time)))
                    .end();
                }
            }
            case CAMERA -> // ⭐ 修复：在用户点击菜单项时才创建关键帧并捕获状态，而不是在菜单构建时
                // 这样可以确保捕获的是用户点击时的当前视角，而不是菜单打开时的视角
                    builder
                        .submenu("摄像机关键帧")
                            .item("添加摄像机关键帧", () -> {
                                // 在执行时获取当前时间（可能是菜单打开后播放头移动了）
                                float currentTime = model.getCurrentTime();
                                String timeLabel = formatTime(currentTime);
                                CameraKeyframe keyframe = new CameraKeyframe("Camera @" + timeLabel, currentTime);
                                // 在执行时捕获当前相机状态
                                TimelineCameraController.getInstance().captureFromCurrentCamera(keyframe);
                                // 执行命令
                                commandHistory.executeCommand(new AddAutomationKeyframeCommand(model, track, keyframe));
                            })
                        .end();
            case WEATHER -> builder
                .submenu("天气关键帧")
                    .command("添加天气关键帧",
                        new AddAutomationKeyframeCommand(
                            model,
                            track,
                            new WeatherKeyframe("Weather @" + formatTime(time), time)))
                .end();
            case MARKER -> builder
                .submenu("标记")
                    .command("添加标记",
                        new AddAutomationKeyframeCommand(
                            model,
                            track,
                            new TimelineMarker("Marker @" + formatTime(time), time)))
                .end();
            default -> { /* no-op */ }
        }
    }

    private String formatTime(float time) {
        return String.format("%.2f", time);
    }
}
