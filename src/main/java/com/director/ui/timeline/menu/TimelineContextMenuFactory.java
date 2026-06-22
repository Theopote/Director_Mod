package com.director.ui.timeline.menu;

import com.director.core.DirectorServices;
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
            com.director.timeline.model.clip.ClipKeyframe clipKeyframe,
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
        java.util.List<com.director.timeline.model.clip.ClipKeyframe> selectedClipKeyframes =
                model.getSelectedItems().stream()
                        .filter(com.director.timeline.model.clip.ClipKeyframe.class::isInstance)
                        .map(com.director.timeline.model.clip.ClipKeyframe.class::cast)
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
                        new com.director.ui.timeline.commands.RemoveClipKeyframeCommand(model, track, clip, clipKeyframe),
                        "Del")
                .separator()
                // 跳转到此关键帧
                .item("跳转到此时间", () -> {
                    model.setCurrentTime(clipKeyframe.getTime());
                    com.director.timeline.playback.block.TimelineClipBlockController
                            .getInstance()
                            .update();
                })
                .separator()
                // 插值方式子菜单
                .submenu("插值方式")
                    .command(
                        (interp == TimelineInterpolation.LINEAR ? "✓ " : "") + "线性（匀速）",
                        new com.director.ui.timeline.commands.ChangeClipKeyframeInterpolationCommand(
                            model, track, clip, clipKeyframe, TimelineInterpolation.LINEAR
                        ))
                    .command(
                        (interp == TimelineInterpolation.SMOOTH ? "✓ " : "") + "平滑（缓入缓出）",
                        new com.director.ui.timeline.commands.ChangeClipKeyframeInterpolationCommand(
                            model, track, clip, clipKeyframe, TimelineInterpolation.SMOOTH
                        ))
                    .command(
                        (interp == TimelineInterpolation.STEP ? "✓ " : "") + "阶梯（保持再跳变）",
                        new com.director.ui.timeline.commands.ChangeClipKeyframeInterpolationCommand(
                            model, track, clip, clipKeyframe, TimelineInterpolation.STEP
                        ))
                .end()
                .separator()
                // 重置插值
                .command("重置插值为线性",
                        new com.director.ui.timeline.commands.ChangeClipKeyframeInterpolationCommand(
                            model, track, clip, clipKeyframe, TimelineInterpolation.LINEAR
                        ))
                ;

        // 多选：批量插值（SubMenuBuilder 不支持 enabled 参数，这里用禁用项占位）
        TimelineContextMenuBuilder.SubMenuBuilder sub = builder.submenu("批量插值");
        if (hasMultiClipKeyframes) {
            sub.command("线性（Linear）",
                    new com.director.ui.timeline.commands.BatchChangeClipKeyframeInterpolationCommand(
                            model, selectedClipKeyframes, TimelineInterpolation.LINEAR));
            sub.command("平滑（Smooth）",
                    new com.director.ui.timeline.commands.BatchChangeClipKeyframeInterpolationCommand(
                            model, selectedClipKeyframes, TimelineInterpolation.SMOOTH));
            sub.command("阶梯（Step）",
                    new com.director.ui.timeline.commands.BatchChangeClipKeyframeInterpolationCommand(
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
        com.director.timeline.model.clip.ClipKeyframe kf =
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
            com.director.timeline.model.TimelineSelectable keyframe,
            com.director.timeline.model.TimelineTrack track) {
        if (keyframe == null || track == null) {
            LOGGER.warn("[Timeline] 创建自动化关键帧菜单失败：keyframe 或 track 为 null");
            return TimelineContextMenuBuilder
                    .custom(commandHistory, "AutomationKeyframeMenu")
                    .disabledItem("无关键帧")
                    .build();
        }

        // ⭐ 音频事件：提供“取消链接（不再跟随片段移动）”
        if (keyframe instanceof AudioEvent audioEvent) {
            var audioService = com.director.ui.timeline.audio.TimelineAudioService.getInstance();
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
                        .disabledItem("摄像机关键帧 @ " + String.format("%.3fs", cameraKeyframe.getTime()))
                        .disabledItem("轨道已锁定，无法操作")
                        .build();
            }
            
            // 检查是否是激活的摄像机轨道
            com.director.timeline.playback.camera.TimelineCameraController controller =
                    com.director.timeline.playback.camera.TimelineCameraController.getInstance();
            com.director.timeline.model.TimelineTrack activeTrack = controller.getActiveCameraTrack();
            
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
                        new com.director.ui.timeline.commands.RemoveAutomationKeyframeCommand(model, track, keyframe),
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
                // ⚠️ 独奏和静音选项已删除（暂时不需要）
                // .command(track.isMuted() ? "取消静音" : "静音",
                //     new ToggleTrackMuteCommand(model, track),
                //     "M")
                // .command(track.isSolo() ? "取消独奏" : "独奏",
                //     new ToggleTrackSoloCommand(model, track),
                //     "S")
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

        // ⭐ 新建轨道：改为子菜单，包含所有轨道类型选项
        TimelineContextMenuBuilder.SubMenuBuilder addTrackSubmenu = builder.submenu("新建轨道");
        for (com.director.timeline.model.TrackType type : com.director.timeline.model.TrackType.values()) {
            // 为每个轨道类型创建菜单项
            addTrackSubmenu.item(type.getIconAndName(), () -> createNewTrack(type));
        }
        addTrackSubmenu.end();
        
        builder.separator();
        
        // ⭐ 标记：在时间标尺上右键添加标记
        builder.command("在当前位置添加标记",
                new com.director.ui.timeline.commands.AddMarkerCommand(
                        model,
                        new TimelineMarker("Marker @" + formatTime(time), time)));

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
                
                // ⭐ 摄像机属性菜单项
                builder
                    .item("摄像机属性", () -> {
                        // 选择轨道并打开属性面板
                        DirectorServices.get().uiSelection().selectTrack(track);
                        LOGGER.info("[TimelineContextMenuFactory] 打开摄像机属性: {}", track.getName());
                    })
                    .separator();
                
                if (isActiveTrack) {
                    // 激活轨道：摄像机路径统一以片段形式创建，关键帧只能添加到自定义路径片段内
                    builder
                        .submenu("摄像机路径")
                            .item("创建自定义路径", () -> {
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
                            .item("创建围绕路径 (Orbit)", () -> {
                                // 创建 Orbit Path Clip
                                float currentTime = model.getCurrentTime();
                                float duration = 5.0f; // 默认 5 秒
                                
                                // 获取当前摄像机位置作为中心点（如果可能）
                                OrbitPathClip orbitClip = getOrbitPathClip(currentTime, duration);

                                // ⭐ 性能优化：使用命令系统，避免直接调用 markDirty() 导致重复触发模型变更通知
                                com.director.ui.timeline.commands.AddPathClipCommand cmd = 
                                    new com.director.ui.timeline.commands.AddPathClipCommand(model, track, orbitClip);
                                commandHistory.executeCommand(cmd);
                                
                                // 选择轨道并打开属性面板（让用户编辑参数）
                                DirectorServices.get().uiSelection().selectTrack(track);
                                LOGGER.info("[TimelineContextMenuFactory] 创建围绕路径: {} @ {:.2f}s", 
                                    orbitClip.getName(), currentTime);
                            })
                            .item("创建推拉路径 (Dolly)", () -> {
                                // 创建 Dolly Path Clip
                                float currentTime = model.getCurrentTime();
                                DollyPathClip dollyClip = getDollyPathClip(currentTime);

                                // ⭐ 性能优化：使用命令系统
                                com.director.ui.timeline.commands.AddPathClipCommand cmd = 
                                    new com.director.ui.timeline.commands.AddPathClipCommand(model, track, dollyClip);
                                commandHistory.executeCommand(cmd);
                                
                                // 选择轨道并打开属性面板（让用户编辑参数）
                                DirectorServices.get().uiSelection().selectTrack(track);
                                LOGGER.info("[TimelineContextMenuFactory] 创建推拉路径: {} @ {:.2f}s", 
                                    dollyClip.getName(), currentTime);
                            })
                            .item("创建升降路径 (Crane)", () -> {
                                // 创建 Crane Path Clip
                                float currentTime = model.getCurrentTime();
                                CranePathClip craneClip = getCranePathClip(currentTime);

                                // ⭐ 性能优化：使用命令系统
                                com.director.ui.timeline.commands.AddPathClipCommand cmd = 
                                    new com.director.ui.timeline.commands.AddPathClipCommand(model, track, craneClip);
                                commandHistory.executeCommand(cmd);
                                
                                // 选择轨道并打开属性面板（让用户编辑参数）
                                DirectorServices.get().uiSelection().selectTrack(track);
                                LOGGER.info("[TimelineContextMenuFactory] 创建升降路径: {} @ {:.2f}s", 
                                    craneClip.getName(), currentTime);
                            })
                        .end();
                } else {
                    // 非激活轨道：禁用路径创建
                    builder
                        .submenu("摄像机路径")
                            .disabledItem("创建路径（仅激活轨道可用）")
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
            case ACTOR -> // 演员轨道：添加 EntityClip 创建菜单
                    builder
                        .submenu("创建实体片段")
                            .item("出现/绑定 (Spawn)", () -> {
                                float currentTime = model.getCurrentTime();
                                EntitySpawnClip clip =
                                    new EntitySpawnClip(
                                        "出现", currentTime, currentTime + 1.0f);
                                addEntityClip(track, clip);
                            })
                            .item("位移/旋转 (Transform)", () -> {
                                float currentTime = model.getCurrentTime();
                                EntityTransformClip clip =
                                    new EntityTransformClip(
                                        "移动", currentTime, currentTime + 2.0f);
                                addEntityClip(track, clip);
                            })
                            .item("动画 (Animation)", () -> {
                                float currentTime = model.getCurrentTime();
                                EntityAnimationClip clip =
                                    new EntityAnimationClip(
                                        "动画", currentTime, currentTime + 2.0f);
                                addEntityClip(track, clip);
                            })
                            .separator()
                            .item("行为 (Action)", () -> {
                                float currentTime = model.getCurrentTime();
                                EntityActionClip clip =
                                    new EntityActionClip(
                                        "行为", currentTime, currentTime + 0.5f);
                                addEntityClip(track, clip);
                            })
                            .item("状态 (State)", () -> {
                                float currentTime = model.getCurrentTime();
                                EntityStateClip clip =
                                    new EntityStateClip(
                                        "状态", currentTime, currentTime + 2.0f);
                                addEntityClip(track, clip);
                            })
                            .item("看向 (LookAt)", () -> {
                                float currentTime = model.getCurrentTime();
                                EntityLookAtClip clip =
                                    new EntityLookAtClip(
                                        "看向", currentTime, currentTime + 2.0f);
                                addEntityClip(track, clip);
                            })
                            .item("离场 (Despawn)", () -> {
                                float currentTime = model.getCurrentTime();
                                EntityDespawnClip clip =
                                    new EntityDespawnClip(
                                        "离场", currentTime, currentTime + 1.0f);
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
                "升降路径 @" + formatTime(currentTime),
                    currentTime,
                currentTime + duration
            );

        // 设置默认参数
        craneClip.setHeightDelta(10.0f); // 默认高度变化 10 方块（上升）
        return craneClip;
    }

    private @NotNull CustomPathClip getCustomPathClip(float currentTime) {
        float duration = 5.0f;
        return new CustomPathClip(
            "自定义路径 @" + formatTime(currentTime),
            currentTime,
            currentTime + duration
        );
    }

    private @NotNull DollyPathClip getDollyPathClip(float currentTime) {
        float duration = 5.0f; // 默认 5 秒

        // 创建 Dolly Path Clip
        DollyPathClip dollyClip =
            new DollyPathClip(
                "推拉路径 @" + formatTime(currentTime),
                    currentTime,
                currentTime + duration
            );

        // 设置默认参数
        dollyClip.setDistance(5.0f); // 默认推拉距离 5 方块
        dollyClip.setDirectionMode(
            DollyPathClip.DirectionMode.CAMERA_FORWARD
        );
        return dollyClip;
    }

    private @NotNull OrbitPathClip getOrbitPathClip(float currentTime, float duration) {
        net.minecraft.util.math.Vec3d center = net.minecraft.util.math.Vec3d.ZERO;
        float height = 64.0f; // 默认高度
        float radius = 10.0f; // 默认半径 10 方块

        // 尝试从当前摄像机状态获取位置
        com.director.timeline.playback.camera.CameraRuntime runtime =
            com.director.timeline.playback.camera.CameraRuntime.getInstance();
        com.director.timeline.playback.camera.CameraSample currentSample = runtime.getCurrentSample();
        if (currentSample != null) {
            center = currentSample.position();
            height = (float) center.y;
        }

        // 创建 Orbit Path Clip
        return new OrbitPathClip(
            "围绕路径 @" + formatTime(currentTime),
                currentTime,
            currentTime + duration,
            center,
            radius,
            height
        );
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
                    .disabledItem("无 Path Clip")
                    .build();
        }
        
        com.director.timeline.model.camera.path.CameraPathClip clip = pathClipHit.clip();
        com.director.timeline.model.TimelineTrack track = pathClipHit.track();
        
        if (clip == null || track == null) {
            return TimelineContextMenuBuilder
                    .custom(commandHistory, "PathClipMenu")
                    .disabledItem("无 Path Clip")
                    .build();
        }
        
        // 检查轨道锁定和激活状态
        if (track.isLocked()) {
            return TimelineContextMenuBuilder
                    .custom(commandHistory, "PathClipMenu")
                    .disabledItem("Path Clip: " + clip.getName())
                    .disabledItem("轨道已锁定，无法操作")
                    .build();
        }
        
        if (track.getType() == com.director.timeline.model.TrackType.CAMERA) {
            com.director.timeline.playback.camera.TimelineCameraController controller =
                    com.director.timeline.playback.camera.TimelineCameraController.getInstance();
            com.director.timeline.model.TimelineTrack activeTrack = controller.getActiveCameraTrack();
            
            if (activeTrack != track) {
                return TimelineContextMenuBuilder
                        .custom(commandHistory, "PathClipMenu")
                        .disabledItem("Path Clip: " + clip.getName())
                        .disabledItem("轨道未激活，无法操作")
                        .build();
            }
        }
        
        TimelineContextMenuBuilder builder = TimelineContextMenuBuilder
                .custom(commandHistory, "PathClipMenu")
                .disabledItem("Path Clip: " + clip.getName())
                .disabledItem(String.format("@ %.2fs - %.2fs", clip.getStartTime(), clip.getEndTime()))
                .separator()
                .item("Path 属性...", () -> {
                    // 选择 Path Clip 并打开属性面板
                    model.selectItem(clip, false);
                    DirectorServices.get().uiSelection().setCurrentSelection(clip);
                    LOGGER.info("[TimelineContextMenuFactory] 打开 Path Clip 属性: {}", clip.getName());
                })
                .separator()
                ;

        if (clip instanceof CustomPathClip customPathClip) {
            builder.item("添加摄像机关键帧", () -> {
                float currentTime = Math.max(customPathClip.getStartTime(),
                        Math.min(customPathClip.getEndTime(), model.getCurrentTime()));
                String timeLabel = formatTime(currentTime);
                CameraKeyframe keyframe = new CameraKeyframe("Camera @" + timeLabel, currentTime);
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

        builder.command("删除 Path Clip", 
                    new com.director.ui.timeline.commands.DeletePathClipCommand(model, track, clip), 
                    "Del");
        
        return builder.build();
    }

    private String formatTime(float time) {
        return String.format("%.2f", time);
    }
}
