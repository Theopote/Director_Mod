package com.chronoblocks.ui.timeline.menu;

import com.chronoblocks.ui.panels.TimelinePanel;
import com.chronoblocks.ui.timeline.commands.AddAutomationKeyframeCommand;
import com.chronoblocks.ui.timeline.commands.BindClipToContainerCommand;
import com.chronoblocks.ui.timeline.commands.DeleteClipCommand;
import com.chronoblocks.ui.timeline.commands.DeleteTrackCommand;
import com.chronoblocks.ui.timeline.commands.DuplicateClipCommand;
import com.chronoblocks.ui.timeline.commands.MoveTrackCommand;
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
import com.chronoblocks.ui.timeline.model.audio.AudioEvent;
import com.chronoblocks.ui.timeline.model.entity.EntityActionClip;
import com.chronoblocks.ui.timeline.model.entity.EntityAnimationClip;
import com.chronoblocks.ui.timeline.model.entity.EntityClip;
import com.chronoblocks.ui.timeline.model.entity.EntityDespawnClip;
import com.chronoblocks.ui.timeline.model.entity.EntityLookAtClip;
import com.chronoblocks.ui.timeline.model.entity.EntitySpawnClip;
import com.chronoblocks.ui.timeline.model.entity.EntityStateClip;
import com.chronoblocks.ui.timeline.model.entity.EntityTimelineClip;
import com.chronoblocks.ui.timeline.model.entity.EntityTrack;
import com.chronoblocks.ui.timeline.model.entity.EntityTransformClip;
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
            // ⭐⭐ 修复 B、D：优先处理 KEYFRAME，使用 ClipKeyframe 对象
            case KEYFRAME -> {
                com.chronoblocks.ui.timeline.model.clip.ClipKeyframe clipKeyframe = hit.clipKeyframe();
                if (clipKeyframe != null) {
                    yield createClipKeyframeMenu(clipKeyframe, hit.clip(), hit.track());
                }
                // 回退：如果没有 ClipKeyframe 对象，尝试通过时间查找
                TimelineClip clip = hit.clip();
                TimelineTrack track = hit.track() != null ? hit.track() : model.findTrackForClip(clip);
                if (clip != null) {
                    final float EPS = 0.001f;
                    com.chronoblocks.ui.timeline.model.clip.ClipKeyframe kf = clip.findKeyframeAt(hit.time(), EPS);
                    if (kf != null) {
                        yield createClipKeyframeMenu(kf, clip, track);
                    }
                    // 如果仍然找不到，返回错误菜单
                    yield TimelineContextMenuBuilder
                            .forKeyframe(commandHistory)
                            .disabledItem(String.format("关键帧 @ %.3fs (未找到对象)", hit.time()))
                            .build();
                }
                // 如果 clip 也为 null，返回空菜单
                yield TimelineContextMenuBuilder
                        .forKeyframe(commandHistory)
                        .disabledItem("无关键帧")
                        .build();
            }
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

        return builder.build();  // ⭐⭐ 修复：使用 build() 而不是 open()，由 openContextMenuForHit 统一打开
    }

    // =========================================================
    // Keyframe 菜单
    // =========================================================
    
    /**
     * ⭐⭐ 修复 D：创建 ClipKeyframe 菜单（使用对象而不是时间）
     * 
     * @param clipKeyframe ClipKeyframe 对象（不能为 null）
     * @param clip 片段
     * @param track 轨道
     * @return 上下文菜单
     */
    public TimelineContextMenu createClipKeyframeMenu(
            com.chronoblocks.ui.timeline.model.clip.ClipKeyframe clipKeyframe,
            TimelineClip clip,
            TimelineTrack track) {
        if (clipKeyframe == null || clip == null) {
            LOGGER.warn("[Timeline] 创建 ClipKeyframe 菜单失败：clipKeyframe 或 clip 为 null");
            return TimelineContextMenuBuilder
                    .forKeyframe(commandHistory)
                    .disabledItem("无关键帧")
                    .build();
        }
        
        if (track == null) {
            track = model.findTrackForClip(clip);
        }
        
        if (track == null) {
            LOGGER.warn("[Timeline] 创建 ClipKeyframe 菜单失败：track 为 null");
            return TimelineContextMenuBuilder
                    .forKeyframe(commandHistory)
                    .disabledItem("无轨道")
                    .build();
        }

        TimelineInterpolation interp = clipKeyframe.getInterpolation();

        // 多选：收集当前选中的 ClipKeyframe（用于批量操作）
        java.util.List<com.chronoblocks.ui.timeline.model.clip.ClipKeyframe> selectedClipKeyframes =
                model.getSelectedItems().stream()
                        .filter(com.chronoblocks.ui.timeline.model.clip.ClipKeyframe.class::isInstance)
                        .map(com.chronoblocks.ui.timeline.model.clip.ClipKeyframe.class::cast)
                        .toList();

        boolean hasMultiClipKeyframes = selectedClipKeyframes.size() > 1;

        TimelineContextMenuBuilder builder = TimelineContextMenuBuilder
                .forKeyframe(commandHistory)
                .disabledItem(String.format("关键帧 @ %.3fs", clipKeyframe.getTime()))
                .separator()
                // 多选：删除选中（工具栏 Delete/Del 键同一入口）
                .item("删除选中",
                        () -> {
                            if (panel != null) {
                                panel.onDeleteSelectedClicked();
                            }
                        },
                        "Del",
                        panel != null && model.getSelectedCount() > 1)
                // 删除关键帧
                .command("删除关键帧",
                        new com.chronoblocks.ui.timeline.commands.RemoveClipKeyframeCommand(model, track, clip, clipKeyframe),
                        "Del")
                .separator()
                // 跳转到此关键帧
                .item("跳转到此时间", () -> {
                    model.setCurrentTime(clipKeyframe.getTime());
                    com.chronoblocks.ui.timeline.playback.TimelineClipBlockController
                            .getInstance()
                            .update();
                })
                .separator()
                // 插值方式子菜单
                .submenu("插值方式")
                    .command(
                        (interp == TimelineInterpolation.LINEAR ? "✓ " : "") + "线性（匀速）",
                        new com.chronoblocks.ui.timeline.commands.ChangeClipKeyframeInterpolationCommand(
                            model, track, clip, clipKeyframe, TimelineInterpolation.LINEAR
                        ))
                    .command(
                        (interp == TimelineInterpolation.SMOOTH ? "✓ " : "") + "平滑（缓入缓出）",
                        new com.chronoblocks.ui.timeline.commands.ChangeClipKeyframeInterpolationCommand(
                            model, track, clip, clipKeyframe, TimelineInterpolation.SMOOTH
                        ))
                    .command(
                        (interp == TimelineInterpolation.STEP ? "✓ " : "") + "阶梯（保持再跳变）",
                        new com.chronoblocks.ui.timeline.commands.ChangeClipKeyframeInterpolationCommand(
                            model, track, clip, clipKeyframe, TimelineInterpolation.STEP
                        ))
                .end()
                .separator()
                // 重置插值
                .command("重置插值为线性",
                        new com.chronoblocks.ui.timeline.commands.ChangeClipKeyframeInterpolationCommand(
                            model, track, clip, clipKeyframe, TimelineInterpolation.LINEAR
                        ))
                ;

        // 多选：批量插值（SubMenuBuilder 不支持 enabled 参数，这里用禁用项占位）
        TimelineContextMenuBuilder.SubMenuBuilder sub = builder.submenu("批量插值");
        if (hasMultiClipKeyframes) {
            sub.command("线性（Linear）",
                    new com.chronoblocks.ui.timeline.commands.BatchChangeClipKeyframeInterpolationCommand(
                            model, selectedClipKeyframes, TimelineInterpolation.LINEAR));
            sub.command("平滑（Smooth）",
                    new com.chronoblocks.ui.timeline.commands.BatchChangeClipKeyframeInterpolationCommand(
                            model, selectedClipKeyframes, TimelineInterpolation.SMOOTH));
            sub.command("阶梯（Step）",
                    new com.chronoblocks.ui.timeline.commands.BatchChangeClipKeyframeInterpolationCommand(
                            model, selectedClipKeyframes, TimelineInterpolation.STEP));
        } else {
            sub.disabledItem("（请先选中多个关键帧）");
        }
        sub.end();

        return builder.build();  // ⭐⭐ 修复：使用 build() 而不是 open()，由 openContextMenuForHit 统一打开
    }
    
    /**
     * @deprecated 使用 createClipKeyframeMenu(ClipKeyframe, Clip, Track) 代替
     */
    @Deprecated
    public TimelineContextMenu createKeyframeMenu(TimelineClip clip, float keyframeTime) {
        TimelineTrack track = model.findTrackForClip(clip);
        if (track == null || clip == null) {
            LOGGER.warn("[Timeline] 创建 Keyframe 菜单失败：track 或 clip 为 null");
            return TimelineContextMenuBuilder
                    .forKeyframe(commandHistory)
                    .disabledItem("无关键帧")
                    .build();
        }

        // ⭐ 从 Clip 内部查找真正的 ClipKeyframe 对象
        final float EPS = 0.001f;
        com.chronoblocks.ui.timeline.model.clip.ClipKeyframe kf =
                clip.findKeyframeAt(keyframeTime, EPS);

        if (kf == null) {
            LOGGER.warn("[Timeline] 未找到时间 {:.3f}s 对应的 ClipKeyframe", keyframeTime);
            return TimelineContextMenuBuilder
                    .forKeyframe(commandHistory)
                    .disabledItem(String.format("关键帧 @ %.3fs (未找到对象)", keyframeTime))
                    .build();
        }

        // 委托给新方法
        return createClipKeyframeMenu(kf, clip, track);
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

        // ⭐ 音频事件：提供“取消链接（不再跟随片段移动）”
        if (keyframe instanceof AudioEvent audioEvent) {
            var audioService = com.chronoblocks.ui.timeline.audio.TimelineAudioService.getInstance();
            boolean linked = audioService.isEventLinked(audioEvent);
            String linkedInfo = linked
                    ? audioService.getLinkedClip(audioEvent)
                        .map(c -> "已链接到片段: " + (c.getLabel() != null ? c.getLabel() : "(未命名)"))
                        .orElse("已链接到片段")
                    : "未链接（独立事件）";

            TimelineContextMenuBuilder builder = TimelineContextMenuBuilder
                    .custom(commandHistory, "AudioEventMenu")
                    .disabledItem(String.format("音频事件 @ %.3fs", audioEvent.getStartTime()))
                    .disabledItem(linkedInfo)
                    .separator();

            if (linked) {
                builder.item("取消与片段链接（不再跟随移动）", () -> audioService.unlinkEvent(audioEvent));
            } else {
                builder.disabledItem("取消与片段链接（当前已是独立事件）");
            }

            builder.separator()
                    .item("删除选中",
                            () -> {
                                if (panel != null) {
                                    panel.onDeleteSelectedClicked();
                                }
                            },
                            "Del",
                            panel != null && model.getSelectedCount() > 1)
                    .command("删除音频事件",
                            new com.chronoblocks.ui.timeline.commands.RemoveAutomationKeyframeCommand(model, track, audioEvent),
                            "Del");

            return builder.build();
        }

        // ⭐ 摄像机关键帧：使用专门的 Mine-imator / Blender 风格菜单
        if (keyframe instanceof com.chronoblocks.ui.timeline.model.camera.CameraKeyframe cameraKeyframe) {
            // ⭐ v5.0：检查轨道是否锁定或未激活
            if (track.isLocked()) {
                // 锁定的轨道：显示禁用菜单
                return TimelineContextMenuBuilder
                        .custom(commandHistory, "CameraKeyframeMenu")
                        .disabledItem("摄像机关键帧 @ " + String.format("%.3fs", cameraKeyframe.getTime()))
                        .disabledItem("轨道已锁定，无法操作")
                        .build();
            }
            
            // 检查是否是激活的摄像机轨道
            com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController controller =
                    com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController.getInstance();
            com.chronoblocks.ui.timeline.model.TimelineTrack activeTrack = controller.getActiveCameraTrack();
            
            if (activeTrack != track) {
                // 未激活的轨道：显示禁用菜单
                return TimelineContextMenuBuilder
                        .custom(commandHistory, "CameraKeyframeMenu")
                        .disabledItem("摄像机关键帧 @ " + String.format("%.3fs", cameraKeyframe.getTime()))
                        .disabledItem("轨道未激活，无法操作")
                        .build();
            }
            
            return new CameraKeyframeContextMenu(commandHistory, cameraKeyframe, track, model);
        }

        // 其他类型的自动化关键帧：使用通用菜单
        String menuLabel = keyframe.getName();
        switch (keyframe) {
            case WeatherKeyframe ignored -> menuLabel = "天气关键帧";
            case TimelineMarker ignored -> menuLabel = "标记";
            default -> {
            }
        }

        return TimelineContextMenuBuilder
                .custom(commandHistory, "AutomationKeyframeMenu")
                .disabledItem(String.format("%s @ %.3fs", menuLabel, keyframe.getStartTime()))
                .separator()
                .command("删除关键帧",
                        new com.chronoblocks.ui.timeline.commands.RemoveAutomationKeyframeCommand(model, track, keyframe),
                        "Del")
                .build();  // ⭐⭐ 修复：使用 build() 而不是 open()，由 openContextMenuForHit 统一打开
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
        return builder.build();  // ⭐⭐ 修复：使用 build() 而不是 open()，由 openContextMenuForHit 统一打开
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

        return builder.build();  // ⭐⭐ 修复：使用 build() 而不是 open()，由 openContextMenuForHit 统一打开
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
            case CAMERA -> {
                // ⭐ v5.0：检查是否是激活的摄像机轨道
                com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController controller =
                        com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController.getInstance();
                com.chronoblocks.ui.timeline.model.TimelineTrack activeTrack = controller.getActiveCameraTrack();
                boolean isActiveTrack = (activeTrack == track);
                
                if (isActiveTrack) {
                    // 激活轨道：可以添加关键帧
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
                } else {
                    // 非激活轨道：禁用添加关键帧选项
                    builder
                        .submenu("摄像机关键帧")
                            .disabledItem("添加摄像机关键帧（仅激活轨道可用）")
                        .end();
                }
            }
            case WEATHER -> builder
                .submenu("天气关键帧")
                    .command("添加天气关键帧",
                        new AddAutomationKeyframeCommand(
                            model,
                            track,
                            new WeatherKeyframe("Weather @" + formatTime(time), time)))
                .end();
            case ENTITY -> // 实体轨道：添加 EntityClip 创建菜单
                    builder
                        .submenu("创建实体片段")
                            .item("出现/绑定 (Spawn)", () -> {
                                float currentTime = model.getCurrentTime();
                                EntitySpawnClip clip =
                                    new EntitySpawnClip(
                                        "出现", currentTime, currentTime + 1.0f);
                                addEntityClip(track, clip);
                                model.markDirty();
                            })
                            .item("位移/旋转 (Transform)", () -> {
                                float currentTime = model.getCurrentTime();
                                EntityTransformClip clip =
                                    new EntityTransformClip(
                                        "移动", currentTime, currentTime + 2.0f);
                                addEntityClip(track, clip);
                                model.markDirty();
                            })
                            .item("动画 (Animation)", () -> {
                                float currentTime = model.getCurrentTime();
                                EntityAnimationClip clip =
                                    new EntityAnimationClip(
                                        "动画", currentTime, currentTime + 2.0f);
                                addEntityClip(track, clip);
                                model.markDirty();
                            })
                            .separator()
                            .item("行为 (Action)", () -> {
                                float currentTime = model.getCurrentTime();
                                EntityActionClip clip =
                                    new EntityActionClip(
                                        "行为", currentTime, currentTime + 0.5f);
                                addEntityClip(track, clip);
                                model.markDirty();
                            })
                            .item("状态 (State)", () -> {
                                float currentTime = model.getCurrentTime();
                                EntityStateClip clip =
                                    new EntityStateClip(
                                        "状态", currentTime, currentTime + 2.0f);
                                addEntityClip(track, clip);
                                model.markDirty();
                            })
                            .item("看向 (LookAt)", () -> {
                                float currentTime = model.getCurrentTime();
                                EntityLookAtClip clip =
                                    new EntityLookAtClip(
                                        "看向", currentTime, currentTime + 2.0f);
                                addEntityClip(track, clip);
                                model.markDirty();
                            })
                            .item("离场 (Despawn)", () -> {
                                float currentTime = model.getCurrentTime();
                                EntityDespawnClip clip =
                                    new EntityDespawnClip(
                                        "离场", currentTime, currentTime + 1.0f);
                                addEntityClip(track, clip);
                                model.markDirty();
                            })
                        .end();
            default -> { /* no-op */ }
        }
    }

    private void addEntityClip(TimelineTrack track, EntityClip clip) {
        if (track == null || clip == null) {
            return;
        }
        EntityTrack entityTrack = track.getOrCreateEntityTrackPayload();
        entityTrack.addClip(clip);
        track.addClip(new EntityTimelineClip(clip, entityTrack.getColorRgba()));
    }

    private String formatTime(float time) {
        return String.format("%.2f", time);
    }
}
