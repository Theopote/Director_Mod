package com.director.ui.timeline.menu;

import com.director.core.DirectorServices;
import com.director.i18n.DirectorI18n;
import com.director.i18n.DirectorLang;
import com.director.i18n.TrackI18n;
import com.director.ui.panels.TimelinePanel;
import com.director.ui.timeline.commands.AddEntityClipCommand;
import com.director.ui.timeline.commands.AddAutomationKeyframeCommand;
import com.director.ui.timeline.commands.BindClipToContainerCommand;
import com.director.ui.timeline.commands.DeleteClipCommand;
import com.director.ui.timeline.commands.DeleteTrackCommand;
import com.director.ui.timeline.commands.DuplicateClipCommand;
import com.director.ui.timeline.commands.MoveTrackCommand;
import com.director.ui.timeline.commands.SetClipLengthCommand;
import com.director.ui.timeline.commands.TimelineCommandHistory;
import com.director.ui.timeline.commands.ToggleTrackLockCommand;
// ⚠️ 独奏和静音选项已删除，导入已注释
// import com.director.ui.timeline.commands.ToggleTrackMuteCommand;
// import com.director.ui.timeline.commands.ToggleTrackSoloCommand;
import com.director.ui.timeline.dialog.TrackRenameDialog;
import com.director.ui.timeline.interaction.TimelineHitResult;
import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineInterpolation;
import com.director.timeline.model.TimelineMarker;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.camera.CameraKeyframe;
import com.director.timeline.model.audio.AudioEvent;
import com.director.timeline.model.camera.path.CranePathClip;
import com.director.timeline.model.camera.path.CustomPathClip;
import com.director.timeline.model.camera.path.DollyPathClip;
import com.director.timeline.model.camera.path.OrbitPathClip;
import com.director.timeline.model.entity.EntityActionClip;
import com.director.timeline.model.entity.ActorRelationClip;
import com.director.timeline.model.entity.EntityAnimationClip;
import com.director.timeline.model.entity.EntityClip;
import com.director.timeline.model.entity.EntityDespawnClip;
import com.director.timeline.model.entity.EntityLookAtClip;
import com.director.timeline.model.entity.EntitySpawnClip;
import com.director.timeline.model.entity.EntityStateClip;
import com.director.timeline.model.entity.EntityTransformClip;
import com.director.timeline.model.weather.WeatherKeyframe;
import com.director.timeline.playback.camera.TimelineCameraController;
import org.jetbrains.annotations.NotNull;
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

    private static String tr(String key) {
        return DirectorI18n.translate(key);
    }

    private static String tr(String key, Object... args) {
        return DirectorI18n.translate(key, args);
    }

    private static String checkedLabel(boolean selected, String key) {
        return (selected ? "✓ " : "") + tr(key);
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
                com.director.timeline.model.clip.ClipKeyframe clipKeyframe = hit.clipKeyframe();
                if (clipKeyframe != null) {
                    yield createClipKeyframeMenu(clipKeyframe, hit.clip(), hit.track());
                }
                // 回退：如果没有 ClipKeyframe 对象，尝试通过时间查找
                TimelineClip clip = hit.clip();
                TimelineTrack track = hit.track() != null ? hit.track() : model.findTrackForClip(clip);
                if (clip != null) {
                    final float EPS = 0.001f;
                    com.director.timeline.model.clip.ClipKeyframe kf = clip.findKeyframeAt(hit.time(), EPS);
                    if (kf != null) {
                        yield createClipKeyframeMenu(kf, clip, track);
                    }
                    // 如果仍然找不到，返回错误菜单
                    yield TimelineContextMenuBuilder
                            .forKeyframe(commandHistory)
                            .disabledItem(tr(DirectorLang.Timeline.ContextMenu.KEYFRAME_AT_NOT_FOUND, hit.time()))
                            .build();
                }
                yield TimelineContextMenuBuilder
                        .forKeyframe(commandHistory)
                        .disabledItem(tr(DirectorLang.Timeline.ContextMenu.NO_KEYFRAME))
                        .build();
            }
            case AUTOMATION_KEYFRAME -> createAutomationKeyframeMenu(hit.automationKeyframe(), hit.track());
            case PATH_CLIP -> createPathClipMenu(hit.pathClipHit()); // ⭐ Path Clip 菜单
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
                    .disabledItem(tr(DirectorLang.Timeline.ContextMenu.NO_CLIP))
                    .build();
        }

        String clipName = clip.getLabel();
        UUID containerId = clip.getBoundContainerId();
        DeleteClipCommand deleteCmd = new DeleteClipCommand(model, clip, track.getClips());

        TimelineContextMenuBuilder builder = TimelineContextMenuBuilder
                .forClip(commandHistory)
                .disabledItem(tr(DirectorLang.Timeline.ContextMenu.CLIP_LABEL, clipName))
                .disabledItem(tr(DirectorLang.Timeline.ContextMenu.TIME_RANGE, clip.getStartTime(), clip.getEndTime()))
                .separator()
                .command(tr(DirectorLang.Timeline.ContextMenu.DUPLICATE_CLIP), new DuplicateClipCommand(model, track, clip), "Ctrl+D")
                .command(tr(DirectorLang.Timeline.ContextMenu.DELETE_CLIP), deleteCmd, "Del")
                .separator()
                .command(tr(DirectorLang.Timeline.ContextMenu.SET_DURATION_1S), new SetClipLengthCommand(model, clip, 1.0f))
                .command(tr(DirectorLang.Timeline.ContextMenu.SET_DURATION_2S), new SetClipLengthCommand(model, clip, 2.0f))
                .command(tr(DirectorLang.Timeline.ContextMenu.SET_DURATION_5S), new SetClipLengthCommand(model, clip, 5.0f))
                .separator();

        if (panel != null) {
            builder.item(tr(DirectorLang.Timeline.ContextMenu.RENAME_CLIP), () -> panel.requestClipRename(clip));
            builder.item(tr(DirectorLang.Timeline.ContextMenu.BIND_CONTAINER), () -> panel.requestClipContainerBinding(clip));
        }

        builder.command(tr(DirectorLang.Timeline.ContextMenu.UNBIND_CONTAINER),
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
            com.director.timeline.model.clip.ClipKeyframe clipKeyframe,
            TimelineClip clip,
            TimelineTrack track) {
        if (clipKeyframe == null || clip == null) {
            LOGGER.warn("[Timeline] 创建 ClipKeyframe 菜单失败：clipKeyframe 或 clip 为 null");
            return TimelineContextMenuBuilder
                    .forKeyframe(commandHistory)
                    .disabledItem(tr(DirectorLang.Timeline.ContextMenu.NO_KEYFRAME))
                    .build();
        }
        
        if (track == null) {
            track = model.findTrackForClip(clip);
        }
        
        if (track == null) {
            LOGGER.warn("[Timeline] 创建 ClipKeyframe 菜单失败：track 为 null");
            return TimelineContextMenuBuilder
                    .forKeyframe(commandHistory)
                    .disabledItem(tr(DirectorLang.Timeline.ContextMenu.NO_TRACK))
                    .build();
        }

        TimelineInterpolation interp = clipKeyframe.getInterpolation();

        // 多选：收集当前选中的 ClipKeyframe（用于批量操作）
        java.util.List<com.director.timeline.model.clip.ClipKeyframe> selectedClipKeyframes =
                model.getSelectedItems().stream()
                        .filter(com.director.timeline.model.clip.ClipKeyframe.class::isInstance)
                        .map(com.director.timeline.model.clip.ClipKeyframe.class::cast)
                        .toList();

        boolean hasMultiClipKeyframes = selectedClipKeyframes.size() > 1;

        TimelineContextMenuBuilder builder = TimelineContextMenuBuilder
                .forKeyframe(commandHistory)
                .disabledItem(tr(DirectorLang.Timeline.ContextMenu.KEYFRAME_AT, clipKeyframe.getTime()))
                .separator()
                .item(tr(DirectorLang.Timeline.ContextMenu.DELETE_SELECTED),
                        () -> {
                            if (panel != null) {
                                panel.onDeleteSelectedClicked();
                            }
                        },
                        "Del",
                        panel != null && model.getSelectedCount() > 1)
                .command(tr(DirectorLang.Timeline.ContextMenu.DELETE_KEYFRAME),
                        new com.director.ui.timeline.commands.RemoveClipKeyframeCommand(model, track, clip, clipKeyframe),
                        "Del")
                .separator()
                .item(tr(DirectorLang.Timeline.ContextMenu.JUMP_TO_TIME), () -> {
                    model.setCurrentTime(clipKeyframe.getTime());
                    com.director.timeline.playback.block.TimelineClipBlockController
                            .getInstance()
                            .update();
                })
                .separator()
                .submenu(tr(DirectorLang.Timeline.ContextMenu.INTERPOLATION_SUBMENU))
                    .command(
                        checkedLabel(interp == TimelineInterpolation.LINEAR, DirectorLang.Timeline.ContextMenu.INTERP_LINEAR),
                        new com.director.ui.timeline.commands.ChangeClipKeyframeInterpolationCommand(
                            model, track, clip, clipKeyframe, TimelineInterpolation.LINEAR
                        ))
                    .command(
                        checkedLabel(interp == TimelineInterpolation.SMOOTH, DirectorLang.Timeline.ContextMenu.INTERP_SMOOTH),
                        new com.director.ui.timeline.commands.ChangeClipKeyframeInterpolationCommand(
                            model, track, clip, clipKeyframe, TimelineInterpolation.SMOOTH
                        ))
                    .command(
                        checkedLabel(interp == TimelineInterpolation.STEP, DirectorLang.Timeline.ContextMenu.INTERP_STEP),
                        new com.director.ui.timeline.commands.ChangeClipKeyframeInterpolationCommand(
                            model, track, clip, clipKeyframe, TimelineInterpolation.STEP
                        ))
                .end()
                .separator()
                .command(tr(DirectorLang.Timeline.ContextMenu.RESET_INTERP_LINEAR),
                        new com.director.ui.timeline.commands.ChangeClipKeyframeInterpolationCommand(
                            model, track, clip, clipKeyframe, TimelineInterpolation.LINEAR
                        ))
                ;

        TimelineContextMenuBuilder.SubMenuBuilder sub = builder.submenu(tr(DirectorLang.Timeline.ContextMenu.BATCH_INTERP_SUBMENU));
        if (hasMultiClipKeyframes) {
            sub.command(tr(DirectorLang.Timeline.ContextMenu.BATCH_INTERP_LINEAR),
                    new com.director.ui.timeline.commands.BatchChangeClipKeyframeInterpolationCommand(
                            model, selectedClipKeyframes, TimelineInterpolation.LINEAR));
            sub.command(tr(DirectorLang.Timeline.ContextMenu.BATCH_INTERP_SMOOTH),
                    new com.director.ui.timeline.commands.BatchChangeClipKeyframeInterpolationCommand(
                            model, selectedClipKeyframes, TimelineInterpolation.SMOOTH));
            sub.command(tr(DirectorLang.Timeline.ContextMenu.BATCH_INTERP_STEP),
                    new com.director.ui.timeline.commands.BatchChangeClipKeyframeInterpolationCommand(
                            model, selectedClipKeyframes, TimelineInterpolation.STEP));
        } else {
            sub.disabledItem(tr(DirectorLang.Timeline.ContextMenu.BATCH_INTERP_HINT));
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
                    .disabledItem(tr(DirectorLang.Timeline.ContextMenu.NO_KEYFRAME))
                    .build();
        }

        final float EPS = 0.001f;
        com.director.timeline.model.clip.ClipKeyframe kf =
                clip.findKeyframeAt(keyframeTime, EPS);

        if (kf == null) {
            LOGGER.warn("[Timeline] 未找到时间 {:.3f}s 对应的 ClipKeyframe", keyframeTime);
            return TimelineContextMenuBuilder
                    .forKeyframe(commandHistory)
                    .disabledItem(tr(DirectorLang.Timeline.ContextMenu.KEYFRAME_AT_NOT_FOUND, keyframeTime))
                    .build();
        }

        // 委托给新方法
        return createClipKeyframeMenu(kf, clip, track);
    }

    /**
     * 创建自动化关键帧的右键菜单（摄像机关键帧、天气关键帧等）
     */
    public TimelineContextMenu createAutomationKeyframeMenu(
            com.director.timeline.model.TimelineSelectable keyframe,
            com.director.timeline.model.TimelineTrack track) {
        if (keyframe == null || track == null) {
            LOGGER.warn("[Timeline] 创建自动化关键帧菜单失败：keyframe 或 track 为 null");
            return TimelineContextMenuBuilder
                    .custom(commandHistory, "AutomationKeyframeMenu")
                    .disabledItem(tr(DirectorLang.Timeline.ContextMenu.NO_KEYFRAME))
                    .build();
        }

        if (keyframe instanceof AudioEvent audioEvent) {
            var audioService = com.director.ui.timeline.audio.TimelineAudioService.getInstance();
            boolean linked = audioService.isEventLinked(audioEvent);
            String linkedInfo = linked
                    ? audioService.getLinkedClip(audioEvent)
                        .map(c -> tr(DirectorLang.Timeline.ContextMenu.LINKED_TO_CLIP,
                                c.getLabel() != null ? c.getLabel() : tr(DirectorLang.Timeline.ContextMenu.UNNAMED)))
                        .orElse(tr(DirectorLang.Timeline.ContextMenu.LINKED_TO_CLIP_GENERIC))
                    : tr(DirectorLang.Timeline.ContextMenu.UNLINKED_EVENT);

            TimelineContextMenuBuilder builder = TimelineContextMenuBuilder
                    .custom(commandHistory, "AudioEventMenu")
                    .disabledItem(tr(DirectorLang.Timeline.ContextMenu.AUDIO_EVENT_AT, audioEvent.getStartTime()))
                    .disabledItem(linkedInfo)
                    .separator();

            if (linked) {
                builder.item(tr(DirectorLang.Timeline.ContextMenu.UNLINK_FROM_CLIP), () -> audioService.unlinkEvent(audioEvent));
            } else {
                builder.disabledItem(tr(DirectorLang.Timeline.ContextMenu.UNLINK_ALREADY_INDEPENDENT));
            }

            builder.separator()
                    .item(tr(DirectorLang.Timeline.ContextMenu.DELETE_SELECTED),
                            () -> {
                                if (panel != null) {
                                    panel.onDeleteSelectedClicked();
                                }
                            },
                            "Del",
                            panel != null && model.getSelectedCount() > 1)
                    .command(tr(DirectorLang.Timeline.ContextMenu.DELETE_AUDIO_EVENT),
                            new com.director.ui.timeline.commands.RemoveAutomationKeyframeCommand(model, track, audioEvent),
                            "Del");

            return builder.build();
        }

        // ⭐ 摄像机关键帧：使用专门的 Mine-imator / Blender 风格菜单
        if (keyframe instanceof com.director.timeline.model.camera.CameraKeyframe cameraKeyframe) {
            // ⭐ v5.0：检查轨道是否锁定或未激活
            if (track.isLocked()) {
                // 锁定的轨道：显示禁用菜单
                return TimelineContextMenuBuilder
                        .custom(commandHistory, "CameraKeyframeMenu")
                        .disabledItem(tr(DirectorLang.Timeline.ContextMenu.CAMERA_KEYFRAME_AT, cameraKeyframe.getTime()))
                        .disabledItem(tr(DirectorLang.Timeline.ContextMenu.TRACK_LOCKED))
                        .build();
            }
            
            com.director.timeline.playback.camera.TimelineCameraController controller =
                    com.director.timeline.playback.camera.TimelineCameraController.getInstance();
            com.director.timeline.model.TimelineTrack activeTrack = controller.getActiveCameraTrack();
            
            if (activeTrack != track) {
                return TimelineContextMenuBuilder
                        .custom(commandHistory, "CameraKeyframeMenu")
                        .disabledItem(tr(DirectorLang.Timeline.ContextMenu.CAMERA_KEYFRAME_AT, cameraKeyframe.getTime()))
                        .disabledItem(tr(DirectorLang.Timeline.ContextMenu.TRACK_INACTIVE))
                        .build();
            }
            
            return new CameraKeyframeContextMenu(commandHistory, cameraKeyframe, track, model);
        }

        String menuLabel = keyframe.getName();
        switch (keyframe) {
            case WeatherKeyframe ignored -> menuLabel = tr(DirectorLang.Timeline.ContextMenu.WEATHER_KEYFRAME);
            case TimelineMarker ignored -> menuLabel = tr(DirectorLang.Timeline.ContextMenu.MARKER);
            default -> {
            }
        }

        return TimelineContextMenuBuilder
                .custom(commandHistory, "AutomationKeyframeMenu")
                .disabledItem(String.format("%s @ %.3fs", menuLabel, keyframe.getStartTime()))
                .separator()
                .command(tr(DirectorLang.Timeline.ContextMenu.DELETE_KEYFRAME),
                        new com.director.ui.timeline.commands.RemoveAutomationKeyframeCommand(model, track, keyframe),
                        "Del")
                .build();  // ⭐⭐ 修复：使用 build() 而不是 open()，由 openContextMenuForHit 统一打开
    }

    private TimelineContextMenu createTrackHeaderMenu(TimelineTrack track, int trackIndex) {
        if (track == null) {
            return TimelineContextMenuBuilder
                    .forTrack(commandHistory)
                    .disabledItem(tr(DirectorLang.Timeline.ContextMenu.NO_TRACK))
                    .build();
        }

        List<TimelineTrack> tracks = model.getTracks();
        boolean canMoveUp = trackIndex > 0;
        boolean canMoveDown = trackIndex >= 0 && trackIndex < tracks.size() - 1;

        String trackName = track.getName();
        DeleteTrackCommand deleteTrackCmd = new DeleteTrackCommand(model, track, tracks);

        TimelineContextMenuBuilder builder = TimelineContextMenuBuilder
                .forTrack(commandHistory)
                .disabledItem(tr(DirectorLang.Timeline.ContextMenu.TRACK_LABEL, trackName))
                .disabledItem(tr(DirectorLang.Timeline.ContextMenu.TRACK_TYPE, track.getType().getDisplayName()))
                .separator()
                .command(track.isLocked()
                        ? tr(DirectorLang.Timeline.ContextMenu.UNLOCK_TRACK)
                        : tr(DirectorLang.Timeline.ContextMenu.LOCK_TRACK),
                    new ToggleTrackLockCommand(model, track))
                .separator()
                .item(tr(DirectorLang.Timeline.ContextMenu.RENAME_TRACK), () -> TrackRenameDialog.open(track))
                .separator()
                .command(tr(DirectorLang.Timeline.ContextMenu.MOVE_TRACK_UP),
                    new MoveTrackCommand(model, trackIndex, trackIndex - 1),
                    "Alt+Up",
                    canMoveUp)
                .command(tr(DirectorLang.Timeline.ContextMenu.MOVE_TRACK_DOWN),
                    new MoveTrackCommand(model, trackIndex, trackIndex + 1),
                    "Alt+Down",
                    canMoveDown)
                .separator()
                .command(tr(DirectorLang.Timeline.ContextMenu.DELETE_TRACK), deleteTrackCmd, "Del")
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
                .disabledItem(tr(DirectorLang.Timeline.ContextMenu.TIME_AT, time))
                .separator();

        TimelineContextMenuBuilder.SubMenuBuilder addTrackSubmenu = builder.submenu(tr(DirectorLang.Timeline.ContextMenu.NEW_TRACK_SUBMENU));
        for (com.director.timeline.model.TrackType type : com.director.timeline.model.TrackType.values()) {
            addTrackSubmenu.item(TrackI18n.iconAndName(type), () -> createNewTrack(type));
        }
        addTrackSubmenu.end();
        
        builder.separator();
        
        builder.command(tr(DirectorLang.Timeline.ContextMenu.ADD_MARKER_AT_PLAYHEAD),
                new com.director.ui.timeline.commands.AddMarkerCommand(
                        model,
                        new TimelineMarker(tr(DirectorLang.Timeline.DefaultNames.MARKER_AT, formatTime(time)), time)));

        return builder.build();  // ⭐⭐ 修复：使用 build() 而不是 open()，由 openContextMenuForHit 统一打开
    }
    
    /**
     * ⭐ 创建新轨道的辅助方法（支持撤销/重做）
     * 
     * <p>自动激活和撤销恢复由 AddTrackCommand 统一处理。
     */
    private void createNewTrack(com.director.timeline.model.TrackType type) {

        // 计算同类型轨道数量
        long sameTypeCount = model.getTracks().stream()
                .filter(t -> t.getType() == type)
                .count();
        
        String trackName = type.getDisplayName() + " " + (sameTypeCount + 1);
        com.director.timeline.model.TimelineTrack newTrack;
        
        // 如果是音频轨道，使用 AudioTrack 类
        if (type == com.director.timeline.model.TrackType.AUDIO) {
            newTrack = new com.director.timeline.model.audio.AudioTrack(trackName);
        } else {
            newTrack = new com.director.timeline.model.TimelineTrack(trackName, type);
            if (type == com.director.timeline.model.TrackType.ACTOR) {
                newTrack.setActorTrackPayload(new com.director.timeline.model.entity.ActorTrack(trackName));
            }
        }
        
        // 使用 AddTrackCommand 添加轨道（支持撤销/重做）
        com.director.ui.timeline.commands.AddTrackCommand cmd = 
            new com.director.ui.timeline.commands.AddTrackCommand(model, newTrack);
        commandHistory.executeCommand(cmd);
        
        // 通知面板数据变化（如果有面板引用）
        if (panel != null) {
            // 面板可能有 onDataChanged 回调
            // 这里我们依赖命令历史触发模型更新
        }
        
        LOGGER.info("[TimelineContextMenuFactory] 创建新轨道 {} (类型: {}, 同类型第 {} 个)",
                trackName, type.getDisplayName(), sameTypeCount + 1);
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
                // ⚠️ 标记已移至时间标尺右键菜单，不再在轨道右键菜单中显示
                // if (type.supportsMarkers()) {
                //     builder.submenu("标记")
                //         .command("在当前位置添加标记",
                //             new AddAutomationKeyframeCommand(
                //                 model,
                //                 track,
                //                 new TimelineMarker("Marker @" + formatTime(time), time)))
                //     .end();
                // }
            }
            case CAMERA -> {
                // ⭐ v5.0：检查是否是激活的摄像机轨道
                com.director.timeline.playback.camera.TimelineCameraController controller =
                        com.director.timeline.playback.camera.TimelineCameraController.getInstance();
                com.director.timeline.model.TimelineTrack activeTrack = controller.getActiveCameraTrack();
                boolean isActiveTrack = (activeTrack == track);
                
                builder
                    .item(tr(DirectorLang.Timeline.ContextMenu.CAMERA_PROPERTIES), () -> {
                        DirectorServices.get().uiSelection().selectTrack(track);
                        LOGGER.info("[TimelineContextMenuFactory] 打开摄像机属性: {}", track.getName());
                    })
                    .separator();
                
                if (isActiveTrack) {
                    builder
                        .submenu(tr(DirectorLang.Timeline.ContextMenu.CAMERA_PATH_SUBMENU))
                            .item(tr(DirectorLang.Timeline.ContextMenu.CREATE_CUSTOM_PATH), () -> {
                                float currentTime = model.getCurrentTime();
                                CustomPathClip customClip = getCustomPathClip(currentTime);
                                commandHistory.executeCommand(
                                    new com.director.ui.timeline.commands.AddPathClipCommand(model, track, customClip)
                                );
                                model.selectItem(customClip, false);
                                DirectorServices.get().uiSelection().setCurrentSelection(customClip);
                                LOGGER.info("[TimelineContextMenuFactory] 创建自定义路径: {} @ {:.2f}s",
                                    customClip.getName(), currentTime);
                            })
                            .item(tr(DirectorLang.Timeline.ContextMenu.CREATE_ORBIT_PATH), () -> {
                                float currentTime = model.getCurrentTime();
                                float duration = 5.0f;
                                OrbitPathClip orbitClip = getOrbitPathClip(currentTime, duration);
                                com.director.ui.timeline.commands.AddPathClipCommand cmd = 
                                    new com.director.ui.timeline.commands.AddPathClipCommand(model, track, orbitClip);
                                commandHistory.executeCommand(cmd);
                                DirectorServices.get().uiSelection().selectTrack(track);
                                LOGGER.info("[TimelineContextMenuFactory] 创建围绕路径: {} @ {:.2f}s", 
                                    orbitClip.getName(), currentTime);
                            })
                            .item(tr(DirectorLang.Timeline.ContextMenu.CREATE_DOLLY_PATH), () -> {
                                float currentTime = model.getCurrentTime();
                                DollyPathClip dollyClip = getDollyPathClip(currentTime);
                                com.director.ui.timeline.commands.AddPathClipCommand cmd = 
                                    new com.director.ui.timeline.commands.AddPathClipCommand(model, track, dollyClip);
                                commandHistory.executeCommand(cmd);
                                DirectorServices.get().uiSelection().selectTrack(track);
                                LOGGER.info("[TimelineContextMenuFactory] 创建推拉路径: {} @ {:.2f}s", 
                                    dollyClip.getName(), currentTime);
                            })
                            .item(tr(DirectorLang.Timeline.ContextMenu.CREATE_CRANE_PATH), () -> {
                                float currentTime = model.getCurrentTime();
                                CranePathClip craneClip = getCranePathClip(currentTime);
                                com.director.ui.timeline.commands.AddPathClipCommand cmd = 
                                    new com.director.ui.timeline.commands.AddPathClipCommand(model, track, craneClip);
                                commandHistory.executeCommand(cmd);
                                DirectorServices.get().uiSelection().selectTrack(track);
                                LOGGER.info("[TimelineContextMenuFactory] 创建升降路径: {} @ {:.2f}s", 
                                    craneClip.getName(), currentTime);
                            })
                        .end();
                } else {
                    builder
                        .submenu(tr(DirectorLang.Timeline.ContextMenu.CAMERA_PATH_SUBMENU))
                            .disabledItem(tr(DirectorLang.Timeline.ContextMenu.CREATE_PATH_INACTIVE_HINT))
                        .end();
                }
            }
            case WEATHER -> builder
                .submenu(tr(DirectorLang.Timeline.ContextMenu.WEATHER_KEYFRAME_SUBMENU))
                    .command(tr(DirectorLang.Timeline.ContextMenu.ADD_WEATHER_KEYFRAME),
                        new AddAutomationKeyframeCommand(
                            model,
                            track,
                            new WeatherKeyframe(tr(DirectorLang.Timeline.DefaultNames.WEATHER_AT, formatTime(time)), time)))
                .end();
            case ACTOR -> builder
                        .submenu(tr(DirectorLang.Timeline.ContextMenu.CREATE_ENTITY_CLIP_SUBMENU))
                            .item(tr(DirectorLang.Timeline.ContextMenu.ENTITY_SPAWN), () -> {
                                float currentTime = model.getCurrentTime();
                                EntitySpawnClip clip =
                                    new EntitySpawnClip(
                                        tr(DirectorLang.Timeline.DefaultNames.ENTITY_SPAWN), currentTime, currentTime + 1.0f);
                                addEntityClip(track, clip);
                            })
                            .item(tr(DirectorLang.Timeline.ContextMenu.ENTITY_TRANSFORM), () -> {
                                float currentTime = model.getCurrentTime();
                                EntityTransformClip clip =
                                    new EntityTransformClip(
                                        tr(DirectorLang.Timeline.DefaultNames.ENTITY_TRANSFORM), currentTime, currentTime + 2.0f);
                                addEntityClip(track, clip);
                            })
                            .item(tr(DirectorLang.Timeline.ContextMenu.ENTITY_ANIMATION), () -> {
                                float currentTime = model.getCurrentTime();
                                EntityAnimationClip clip =
                                    new EntityAnimationClip(
                                        tr(DirectorLang.Timeline.DefaultNames.ENTITY_ANIMATION), currentTime, currentTime + 2.0f);
                                addEntityClip(track, clip);
                            })
                            .separator()
                            .item(tr(DirectorLang.Timeline.ContextMenu.ENTITY_ACTION), () -> {
                                float currentTime = model.getCurrentTime();
                                EntityActionClip clip =
                                    new EntityActionClip(
                                        tr(DirectorLang.Timeline.DefaultNames.ENTITY_ACTION), currentTime, currentTime + 0.5f);
                                addEntityClip(track, clip);
                            })
                            .item(tr(DirectorLang.Timeline.ContextMenu.ENTITY_STATE), () -> {
                                float currentTime = model.getCurrentTime();
                                EntityStateClip clip =
                                    new EntityStateClip(
                                        tr(DirectorLang.Timeline.DefaultNames.ENTITY_STATE), currentTime, currentTime + 2.0f);
                                addEntityClip(track, clip);
                            })
                            .item(tr(DirectorLang.Timeline.ContextMenu.ENTITY_LOOK_AT), () -> {
                                float currentTime = model.getCurrentTime();
                                EntityLookAtClip clip =
                                    new EntityLookAtClip(
                                        tr(DirectorLang.Timeline.DefaultNames.ENTITY_LOOK_AT), currentTime, currentTime + 2.0f);
                                addEntityClip(track, clip);
                            })
                            .item(tr(DirectorLang.Timeline.ContextMenu.ENTITY_DESPAWN), () -> {
                                float currentTime = model.getCurrentTime();
                                EntityDespawnClip clip =
                                    new EntityDespawnClip(
                                        tr(DirectorLang.Timeline.DefaultNames.ENTITY_DESPAWN), currentTime, currentTime + 1.0f);
                                addEntityClip(track, clip);
                            })
                            .separator()
                            .item(tr(DirectorLang.Timeline.ContextMenu.ACTOR_RELATION), () -> {
                                float currentTime = model.getCurrentTime();
                                ActorRelationClip clip =
                                    new ActorRelationClip(
                                        tr(DirectorLang.Timeline.DefaultNames.ACTOR_RELATION), currentTime, currentTime + 2.0f);
                                addEntityClip(track, clip);
                            })
                        .end();
            default -> { /* no-op */ }
        }
    }

    private @NotNull CranePathClip getCranePathClip(float currentTime) {
        float duration = 5.0f; // 默认 5 秒

        // 创建 Crane Path Clip
        CranePathClip craneClip =
            new CranePathClip(
                tr(DirectorLang.Timeline.DefaultNames.CRANE_PATH_AT, formatTime(currentTime)),
                    currentTime,
                currentTime + duration
            );

        // 设置默认参数
        craneClip.setHeightDelta(10.0f); // 默认高度变化 10 方块（上升）

        // ⭐ 新建时固化“起始位置 = 当前相机位置”，避免播放时进入片段再捕获导致大跳变
        try {
            var sample = com.director.timeline.playback.camera.CameraRuntime.getInstance().getCurrentSample();
            if (sample != null && sample != com.director.timeline.playback.camera.CameraSample.EMPTY) {
                craneClip.alignFromContext(new com.director.timeline.model.camera.path.CameraPathEnterContext(
                        sample.position(), sample.yaw(), sample.pitch()
                ));
                craneClip.setAlignStartToCurrentPosition(false);
            }
        } catch (Exception ignored) {
        }
        return craneClip;
    }

    private @NotNull CustomPathClip getCustomPathClip(float currentTime) {
        float duration = 5.0f;
        return new CustomPathClip(
            tr(DirectorLang.Timeline.DefaultNames.CUSTOM_PATH_AT, formatTime(currentTime)),
            currentTime,
            currentTime + duration
        );
    }

    private @NotNull DollyPathClip getDollyPathClip(float currentTime) {
        float duration = 5.0f; // 默认 5 秒

        // 创建 Dolly Path Clip
        DollyPathClip dollyClip =
            new DollyPathClip(
                tr(DirectorLang.Timeline.DefaultNames.DOLLY_PATH_AT, formatTime(currentTime)),
                    currentTime,
                currentTime + duration
            );

        // 设置默认参数
        dollyClip.setDistance(5.0f); // 默认推拉距离 5 方块
        dollyClip.setDirectionMode(
            DollyPathClip.DirectionMode.CAMERA_FORWARD
        );

        // ⭐ 新建时固化“起始位置/方向 = 当前相机状态”，避免播放进入片段时重新捕获导致参数失真
        try {
            var sample = com.director.timeline.playback.camera.CameraRuntime.getInstance().getCurrentSample();
            if (sample != null && sample != com.director.timeline.playback.camera.CameraSample.EMPTY) {
                dollyClip.alignFromContext(new com.director.timeline.model.camera.path.CameraPathEnterContext(
                        sample.position(), sample.yaw(), sample.pitch()
                ));
                dollyClip.setAlignStartToCurrentPosition(false);
            }
        } catch (Exception ignored) {
        }
        return dollyClip;
    }

    private @NotNull OrbitPathClip getOrbitPathClip(float currentTime, float duration) {
        // 新建时以当前玩家视角为环绕起点：镜头在环上，注视环绕中心（方块中心或视线前方默认距离）
        OrbitPathClip clip = new OrbitPathClip(
                tr(DirectorLang.Timeline.DefaultNames.ORBIT_PATH_AT, formatTime(currentTime)),
                currentTime,
                currentTime + duration,
                net.minecraft.util.math.Vec3d.ZERO,
                10.0f,
                64.0f
        );

        try {
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            com.director.timeline.playback.camera.CameraSample sample =
                    com.director.timeline.playback.camera.CameraAccessor.sampleFromGameCamera(client);
            if (sample == null) {
                sample = com.director.timeline.playback.camera.CameraRuntime.getInstance().getCurrentSample();
            }
            if (sample != null && sample != com.director.timeline.playback.camera.CameraSample.EMPTY) {
                var ctx = new com.director.timeline.model.camera.path.CameraPathEnterContext(
                        sample.position(), sample.yaw(), sample.pitch()
                );
                var center = com.director.timeline.model.camera.path.OrbitCenterResolver.resolve(client, ctx);
                clip.alignFromCurrentCamera(ctx, center);
                clip.setEndAngle(clip.getStartAngle() + 360f);
            }
        } catch (Exception ignored) {
        }

        return clip;
    }

    private void addEntityClip(TimelineTrack track, EntityClip clip) {
        if (track == null || clip == null) {
            return;
        }
        commandHistory.executeCommand(new AddEntityClipCommand(model, track, clip));
    }

    /**
     * ⭐ 创建 Path Clip 的右键菜单
     */
    private TimelineContextMenu createPathClipMenu(com.director.ui.timeline.interaction.PathClipHit pathClipHit) {
        if (pathClipHit == null) {
            return TimelineContextMenuBuilder
                    .custom(commandHistory, "PathClipMenu")
                    .disabledItem(tr(DirectorLang.Timeline.ContextMenu.NO_PATH_CLIP))
                    .build();
        }
        
        com.director.timeline.model.camera.path.CameraPathClip clip = pathClipHit.clip();
        com.director.timeline.model.TimelineTrack track = pathClipHit.track();
        
        if (clip == null || track == null) {
            return TimelineContextMenuBuilder
                    .custom(commandHistory, "PathClipMenu")
                    .disabledItem(tr(DirectorLang.Timeline.ContextMenu.NO_PATH_CLIP))
                    .build();
        }
        
        if (track.isLocked()) {
            return TimelineContextMenuBuilder
                    .custom(commandHistory, "PathClipMenu")
                    .disabledItem(tr(DirectorLang.Timeline.ContextMenu.PATH_CLIP_LABEL, clip.getName()))
                    .disabledItem(tr(DirectorLang.Timeline.ContextMenu.TRACK_LOCKED))
                    .build();
        }
        
        if (track.getType() == com.director.timeline.model.TrackType.CAMERA) {
            com.director.timeline.playback.camera.TimelineCameraController controller =
                    com.director.timeline.playback.camera.TimelineCameraController.getInstance();
            com.director.timeline.model.TimelineTrack activeTrack = controller.getActiveCameraTrack();
            
            if (activeTrack != track) {
                return TimelineContextMenuBuilder
                        .custom(commandHistory, "PathClipMenu")
                        .disabledItem(tr(DirectorLang.Timeline.ContextMenu.PATH_CLIP_LABEL, clip.getName()))
                        .disabledItem(tr(DirectorLang.Timeline.ContextMenu.TRACK_INACTIVE))
                        .build();
            }
        }
        
        TimelineContextMenuBuilder builder = TimelineContextMenuBuilder
                .custom(commandHistory, "PathClipMenu")
                .disabledItem(tr(DirectorLang.Timeline.ContextMenu.PATH_CLIP_LABEL, clip.getName()))
                .disabledItem(tr(DirectorLang.Timeline.ContextMenu.TIME_RANGE, clip.getStartTime(), clip.getEndTime()))
                .separator()
                .item(tr(DirectorLang.Timeline.ContextMenu.PATH_PROPERTIES), () -> {
                    model.selectItem(clip, false);
                    DirectorServices.get().uiSelection().setCurrentSelection(clip);
                    LOGGER.info("[TimelineContextMenuFactory] 打开 Path Clip 属性: {}", clip.getName());
                })
                .separator()
                ;

        if (clip instanceof CustomPathClip customPathClip) {
            builder.item(tr(DirectorLang.Timeline.ContextMenu.ADD_CAMERA_KEYFRAME), () -> {
                float currentTime = Math.max(customPathClip.getStartTime(),
                        Math.min(customPathClip.getEndTime(), model.getCurrentTime()));
                String timeLabel = formatTime(currentTime);
                CameraKeyframe keyframe = new CameraKeyframe(
                        tr(DirectorLang.Timeline.DefaultNames.CAMERA_AT, timeLabel), currentTime);
                boolean captured = TimelineCameraController.getInstance().captureFromCurrentCamera(keyframe);
                if (!captured) {
                    LOGGER.warn("[TimelineContextMenu] 摄像机状态捕获失败（可能不在玩家模式），取消添加关键帧");
                    return;
                }
                commandHistory.executeCommand(
                    new com.director.ui.timeline.commands.AddCameraPathKeyframeCommand(model, customPathClip, keyframe)
                );
            });
            builder.separator();
        }

        builder.command(tr(DirectorLang.Timeline.ContextMenu.DELETE_PATH_CLIP),
                    new com.director.ui.timeline.commands.DeletePathClipCommand(model, track, clip), 
                    "Del");
        
        return builder.build();
    }

    private String formatTime(float time) {
        return String.format("%.2f", time);
    }
}
