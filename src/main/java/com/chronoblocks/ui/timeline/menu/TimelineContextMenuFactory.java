package com.chronoblocks.ui.timeline.menu;

import com.chronoblocks.ui.timeline.commands.*;
import com.chronoblocks.ui.panels.TimelinePanel;
import com.chronoblocks.ui.timeline.dialog.TrackRenameDialog;
import com.chronoblocks.ui.timeline.interaction.TimelineHitResult;
import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.timeline.model.TimelineInterpolation;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.TrackType;

import imgui.ImGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * TimelineContextMenuFactory（ChronoBlocks 风格版）
 *
 * <p>负责根据当前点击的元素（片段、关键帧、轨道、空白区域）
 * 构建带有 ChronoBlocks 设计语言的右键菜单。
 *
 * <p>特点：
 * <ul>
 *   <li>统一使用 TimelineContextMenuBuilder 流式 API</li>
 *   <li>容器优先：Clip 本质上是"容器在时间线上的一段活动"</li>
 *   <li>动画语义明确：关键帧菜单以插值类型为主导</li>
 *   <li>分区清晰：Clip 菜单分为编辑/时长/容器/高级四个区块</li>
 *   <li>轻命令依赖：目前主要通过 Runnable 回调，后续可无缝接入命令系统</li>
 * </ul>
 *
 * <p>使用方式（示例）：
 * <pre>{@code
 * TimelineContextMenuFactory factory = TimelineContextMenuFactory
 *     .forPanel(panel, model, commandHistory);
 * 
 * TimelineContextMenu menu = factory.createClipMenu(
 *     clip, track,
 *     () -> commandHistory.executeCommand(new DeleteClipCommand(...)),
 *     () -> commandHistory.executeCommand(new DuplicateClipCommand(...)),
 *     () -> panel.requestClipRename(clip),
 *     () -> panel.requestClipContainerBinding(clip)
 * );
 * menu.open();
 * }</pre>
 *
 * @author ChronoBlocks Team
 * @version 2.0 - ChronoBlocks Style
 */
public class TimelineContextMenuFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineContextMenuFactory.class);

    private final TimelineCommandHistory commandHistory;
    private final TimelineModel model;
    private final TimelinePanel panel;  // 可选引用，用于调用面板方法

    /**
     * 私有构造函数
     */
    private TimelineContextMenuFactory(
            TimelineCommandHistory commandHistory,
            TimelineModel model,
            TimelinePanel panel) {
        this.commandHistory = Objects.requireNonNull(commandHistory, "commandHistory");
        this.model = Objects.requireNonNull(model, "model");
        this.panel = panel;  // panel 可以为 null
    }

    /**
     * 静态工厂方法（推荐使用）
     * 
     * @param panel Timeline 面板实例（可选，用于调用面板方法）
     * @param model Timeline 模型
     * @param commandHistory 命令历史管理器
     * @return Factory 实例
     */
    public static TimelineContextMenuFactory forPanel(
            TimelinePanel panel,
            TimelineModel model,
            TimelineCommandHistory commandHistory) {
        return new TimelineContextMenuFactory(commandHistory, model, panel);
    }

    /**
     * 构造函数（向后兼容，不包含 panel 引用）
     */
    public TimelineContextMenuFactory(
            TimelineCommandHistory commandHistory,
            TimelineModel model) {
        this(commandHistory, model, null);
    }

    // =========================================================
    // 统一入口：根据 HitResult 构建菜单
    // =========================================================

    /**
     * 根据命中结果构建上下文菜单（统一入口）
     * 
     * @param hit 命中检测结果
     * @return 构建好的菜单，如果类型不支持则返回 null
     */
    public TimelineContextMenu createMenu(TimelineHitResult hit) {
        return buildContextMenu(hit);
    }

    /**
     * 根据命中结果构建上下文菜单
     * 
     * @param hit 命中检测结果
     * @return 构建好的菜单，如果类型不支持则返回 null
     */
    public TimelineContextMenu buildContextMenu(TimelineHitResult hit) {
        if (hit == null) {
            System.out.println("[TimelineContextMenuFactory] ERROR: hit 为 null");
            LOGGER.warn("[Timeline] buildContextMenu: hit 为 null");
            return null;
        }

        System.out.println("[TimelineContextMenuFactory] buildContextMenu: hit.type() = " + hit.type() + 
                ", track = " + hit.track() + ", clip = " + hit.clip() + ", time = " + hit.time());
        LOGGER.info("[Timeline] buildContextMenu: hit.type() = {}, track = {}, clip = {}, time = {}", 
                hit.type(), hit.track(), hit.clip(), hit.time());

        TimelineContextMenu menu = switch (hit.type()) {
            case TRACK_HEADER -> {
                LOGGER.debug("[Timeline] 创建 TRACK_HEADER 菜单");
                yield createTrackMenu(hit.track(), hit.trackIndex());
            }
            case CLIP -> {
                LOGGER.debug("[Timeline] 创建 CLIP 菜单");
                TimelineTrack track = hit.track() != null ? hit.track() :
                    (hit.clip() != null && hit.clip().getParentTrack() != null ? 
                        hit.clip().getParentTrack() : null);
                if (track != null) {
                    yield createClipMenu(hit.clip(), track);
                }
                LOGGER.warn("[Timeline] CLIP 菜单创建失败：track 为 null");
                yield null;
            }
            case KEYFRAME -> {
                LOGGER.debug("[Timeline] 创建 KEYFRAME 菜单");
                // 使用现有的 createKeyframeMenu 方法
                if (hit.clip() != null && hit.time() >= 0f) {
                    yield createKeyframeMenu(hit.clip(), hit.time());
                }
                LOGGER.warn("[Timeline] KEYFRAME 菜单创建失败：clip 为 null 或 time < 0");
                yield null;
            }
            case EMPTY -> {
                LOGGER.debug("[Timeline] 创建 EMPTY 菜单");
                yield createEmptyAreaMenu(hit.time());
            }
            default -> {
                LOGGER.warn("[Timeline] 不支持的 hit.type(): {}", hit.type());
                yield null;
            }
        };

        if (menu == null) {
            System.out.println("[TimelineContextMenuFactory] ERROR: buildContextMenu 返回 null，hit.type() = " + hit.type());
            LOGGER.warn("[Timeline] buildContextMenu 返回 null，hit.type() = {}", hit.type());
        } else {
            System.out.println("[TimelineContextMenuFactory] 成功创建菜单");
            LOGGER.info("[Timeline] buildContextMenu 成功创建菜单");
        }

        return menu;
    }

    /**
     * 创建轨道菜单（简化版本，使用 trackIndex）
     */
    private TimelineContextMenu createTrackMenu(TimelineTrack track, int trackIndex) {
        if (track == null) {
            return null;
        }
        // 使用现有的 createTrackHeaderMenu 方法
        return createTrackHeaderMenu(track);
    }

    // =========================================================
    // Clip（片段）菜单 - ChronoBlocks 风格
    // =========================================================

    /**
     * 创建片段上下文菜单（ChronoBlocks 风格）
     *
     * <p>菜单结构：
     * <ul>
     *   <li>顶部信息：片段名称、时间范围</li>
     *   <li>编辑：复制、粘贴、复制片段</li>
     *   <li>时长：拆分、对齐、预设时长</li>
     *   <li>容器：在容器面板中选中、只显示该容器相关片段</li>
     *   <li>高级：重命名、绑定容器、删除</li>
     * </ul>
     *
     * @param clip 目标片段
     * @param track 片段所在轨道
     * @return 构建好的菜单
     */
    public TimelineContextMenu createClipMenu(TimelineClip clip, TimelineTrack track) {
        if (clip == null || track == null) {
            LOGGER.warn("[Timeline] 创建 Clip 菜单失败：clip 或 track 为 null");
            return TimelineContextMenuBuilder
                    .forClip(commandHistory)
                    .disabledItem("无片段")
                    .build();
        }

        String clipName = safeName(clip.getLabel());
        UUID containerId = clip.getBoundContainerId();

        DeleteClipCommand deleteCmd = new DeleteClipCommand(model, clip, track.getClips());

        TimelineContextMenuBuilder builder = TimelineContextMenuBuilder
                .forClip(commandHistory)

                // -------- 顶部信息 --------
                .disabledItem(String.format("片段：%s", clipName))
                .disabledItem(String.format("@ %.2fs - %.2fs", clip.getStartTime(), clip.getEndTime()))
                .separator()

                // -------- 1. 常规编辑 --------
                .item("复制", () -> {
                    // TODO: 实现剪贴板系统
                    LOGGER.debug("[Timeline] 复制片段到剪贴板: {}", clipName);
                }, "Ctrl+C", false)  // 暂时禁用
                .item("粘贴", () -> {
                    // TODO: 实现剪贴板系统
                    LOGGER.debug("[Timeline] 从剪贴板粘贴片段");
                }, "Ctrl+V", false)  // 暂时禁用
                .command("复制片段",
                    new DuplicateClipCommand(model, track, clip),
                    "Ctrl+D")
                .separator()

                // -------- 2. 片段时长 & 分割 --------
                .item("在当前位置拆分片段", () -> {
                    // TODO: 实现拆分片段命令
                    float cursorTime = model.getCurrentTime();
                    LOGGER.debug("[Timeline] 拆分片段 {} @ {:.2fs}", clipName, cursorTime);
                }, "S", false)  // 暂时禁用，需要实现 SplitClipCommand
                .item("对齐起点到网格", () -> {
                    float snappedStart = model.snapToGrid(clip.getStartTime());
                    clip.setStartTime(snappedStart);
                    model.markDirty();
                    LOGGER.debug("[Timeline] 对齐片段起点到网格: {} -> {:.2fs}", clipName, snappedStart);
                })
                .item("对齐终点到网格", () -> {
                    float snappedEnd = model.snapToGrid(clip.getEndTime());
                    clip.setEndTime(snappedEnd);
                    model.markDirty();
                    LOGGER.debug("[Timeline] 对齐片段终点到网格: {} -> {:.2fs}", clipName, snappedEnd);
                })
                .separator()
                .command("设置时长为 1s",
                    new SetClipLengthCommand(model, clip, 1.0f))
                .command("设置时长为 2s",
                    new SetClipLengthCommand(model, clip, 2.0f))
                .command("设置时长为 5s",
                    new SetClipLengthCommand(model, clip, 5.0f))
                .item("设置时间长度...", () -> {
                    if (panel != null) {
                        panel.requestClipLength(clip);
                    }
                }, null, panel != null)
                .separator()

                // -------- 3. 容器相关（ChronoBlocks 特色）--------
                .item("在容器面板中选中", () -> {
                    // TODO: 实现跳转到容器面板功能
                    if (containerId != null) {
                        LOGGER.debug("[Timeline] 跳转到容器面板: {}", containerId);
                    }
                }, null, containerId != null)
                .item("只显示该容器相关片段", () -> {
                    // TODO: 实现容器隔离视图
                    LOGGER.debug("[Timeline] 只显示容器 {} 相关片段", containerId);
                }, null, containerId != null)
                .separator()

                // -------- 4. 高级操作 --------
                .item("重命名片段...", () -> {
                    if (panel != null) {
                        panel.requestClipRename(clip);
                    }
                }, null, panel != null)
                .item("绑定到容器...", () -> {
                    if (panel != null) {
                        panel.requestClipContainerBinding(clip);
                    }
                }, null, panel != null)
                .command("取消容器绑定",
                    new BindClipToContainerCommand(model, clip, null, null),
                    null,
                    containerId != null)
                .separator()

                // -------- 5. 危险操作 --------
                .command("删除片段", deleteCmd, "Del");

        return builder.open();
    }

    // =========================================================
    // Keyframe（关键帧）菜单 - ChronoBlocks 风格
    // =========================================================

    /**
     * 创建关键帧菜单（ChronoBlocks 风格）
     *
     * <p>菜单结构：
     * <ul>
     *   <li>信息：关键帧时间</li>
     *   <li>编辑：删除关键帧</li>
     *   <li>插值：线性、平滑、阶梯（ChronoBlocks 自定义插值枚举）</li>
     *   <li>复制粘贴：复制/粘贴关键帧（预留）</li>
     * </ul>
     *
     * @param clip 所属片段
     * @param keyframeTime 关键帧时间
     * @return 构建好的菜单
     */
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

                // -------- 信息 --------
                .disabledItem(String.format("关键帧 @ %.3fs", keyframeTime))
                .separator()

                // -------- 编辑 --------
                .command("删除关键帧",
                    new RemoveKeyframeCommand(model, clip, keyframeTime),
                    "Del")
                .separator()

                // -------- 插值方式（ChronoBlocks 自定义插值枚举）--------
                .submenu("插值方式")
                    .command("线性（匀速）",
                        new ChangeKeyframeInterpolationCommand(model, track, clip, keyframeTime, TimelineInterpolation.LINEAR))
                    .command("平滑（缓入缓出）",
                        new ChangeKeyframeInterpolationCommand(model, track, clip, keyframeTime, TimelineInterpolation.SMOOTH))
                    .command("阶梯（保持再跳变）",
                        new ChangeKeyframeInterpolationCommand(model, track, clip, keyframeTime, TimelineInterpolation.STEP))
                .end()
                .separator()

                // -------- 复制粘贴（预留）--------
                .item("复制关键帧", () -> {
                    // TODO: 实现关键帧剪贴板
                    LOGGER.debug("[Timeline] 复制关键帧 @ {:.3fs}", keyframeTime);
                }, "Ctrl+C", false)
                .item("粘贴关键帧", () -> {
                    // TODO: 实现关键帧剪贴板
                    LOGGER.debug("[Timeline] 粘贴关键帧");
                }, "Ctrl+V", false)
                .open();
    }

    // =========================================================
    // Track（轨道头）菜单 - ChronoBlocks 风格
    // =========================================================

    /**
     * 创建轨道头菜单（ChronoBlocks 风格）
     *
     * <p>菜单结构：
     * <ul>
     *   <li>信息：轨道名称、类型</li>
     *   <li>轨道控制：静音、独奏</li>
     *   <li>轨道管理：重命名、插入轨道、删除</li>
     *   <li>容器绑定：根据轨道类型显示特定选项</li>
     * </ul>
     *
     * @param track 轨道
     * @return 构建好的菜单
     */
    public TimelineContextMenu createTrackHeaderMenu(TimelineTrack track) {
        if (track == null) {
            LOGGER.warn("[Timeline] 创建 Track 菜单失败：track 为 null");
            return TimelineContextMenuBuilder
                    .forTrack(commandHistory)
                    .disabledItem("无轨道")
                    .build();
        }

        List<TimelineTrack> tracks = model.getTracks();
        int currentIndex = tracks.indexOf(track);
        boolean canMoveUp = currentIndex > 0;
        boolean canMoveDown = currentIndex >= 0 && currentIndex < tracks.size() - 1;

        String trackName = safeName(track.getName());

        DeleteTrackCommand deleteTrackCmd = new DeleteTrackCommand(model, track, tracks);

        TimelineContextMenuBuilder builder = TimelineContextMenuBuilder
                .forTrack(commandHistory)

                // -------- 顶部信息 --------
                .disabledItem(String.format("轨道：%s", trackName))
                .disabledItem(String.format("类型：%s", track.getType().getDisplayName()))
                .separator()

                // -------- 1. 轨道控制 --------
                .command(track.isMuted() ? "取消静音" : "静音",
                    new ToggleTrackMuteCommand(model, track),
                    "M")
                .command(track.isSolo() ? "取消独奏" : "独奏",
                    new ToggleTrackSoloCommand(model, track),
                    "S")
                .command(track.isLocked() ? "解锁轨道" : "锁定轨道",
                    new ToggleTrackLockCommand(model, track))
                .separator()

                // -------- 2. 轨道管理 --------
                .item("重命名轨道...", () -> {
                    TrackRenameDialog.open(track);
                }, null, true)
                .item("在上方插入同类型轨道", () -> {
                    // TODO: 实现插入轨道命令
                    LOGGER.debug("[Timeline] 在上方插入同类型轨道: {}", track.getType());
                }, null, false)  // 暂时禁用
                .item("在下方插入同类型轨道", () -> {
                    // TODO: 实现插入轨道命令
                    LOGGER.debug("[Timeline] 在下方插入同类型轨道: {}", track.getType());
                }, null, false)  // 暂时禁用
                .separator()

                // -------- 3. 移动操作 --------
                .command("上移轨道",
                    new MoveTrackCommand(model, currentIndex, currentIndex - 1),
                    "Alt+↑",
                    canMoveUp)
                .command("下移轨道",
                    new MoveTrackCommand(model, currentIndex, currentIndex + 1),
                    "Alt+↓",
                    canMoveDown)
                .separator()

                // -------- 4. 危险操作 --------
                .command("删除轨道", deleteTrackCmd, "Del");

        // -------- 5. 根据轨道类型生成额外菜单 --------
        builder.separator();
        addTrackTypeSpecificMenu(builder, track);

        return builder.open();
    }

    // =========================================================
    // 空白区域菜单 - ChronoBlocks 风格
    // =========================================================

    /**
     * 创建时间线空白区域菜单（ChronoBlocks 风格）
     *
     * <p>菜单结构：
     * <ul>
     *   <li>信息：当前时间</li>
     *   <li>轨道管理：新增轨道</li>
     *   <li>粘贴：粘贴片段到此处</li>
     *   <li>播放控制：从此处开始播放、设置播放范围（预留）</li>
     * </ul>
     *
     * @param time 当前鼠标所在时间
     * @return 构建好的菜单
     */
    public TimelineContextMenu createEmptyAreaMenu(float time) {
        TimelineContextMenuBuilder builder = TimelineContextMenuBuilder
                .custom(commandHistory, "TimelineEmptyAreaMenu")

                // -------- 信息 --------
                .disabledItem(String.format("时间：%.3fs", time))
                .separator()

                // -------- 轨道管理 --------
                .item("新增轨道", () -> {
                    if (panel != null) {
                        panel.onAddTrackClicked();
                    } else {
                        LOGGER.debug("[Timeline] 无法打开新建轨道菜单，panel 为 null");
                    }
                }, "Ctrl+T")
                .separator()

                // -------- 粘贴 --------
                .item("粘贴片段到此处", () -> {
                    // TODO: 实现剪贴板粘贴
                    LOGGER.debug("[Timeline] 粘贴片段到时间 {:.3fs}", time);
                }, "Ctrl+V", false)  // 暂时禁用
                .separator()

                // -------- 播放控制（预留）--------
                .item("从此处开始播放", () -> {
                    model.setCurrentTime(time);
                    LOGGER.debug("[Timeline] 从时间 {:.3fs} 开始播放", time);
                })
                .item("设置播放范围起点为此处", () -> {
                    // TODO: 实现播放范围设置
                    LOGGER.debug("[Timeline] 设置播放范围起点: {:.3fs}", time);
                }, null, false)
                .item("设置播放范围终点为此处", () -> {
                    // TODO: 实现播放范围设置
                    LOGGER.debug("[Timeline] 设置播放范围终点: {:.3fs}", time);
                }, null, false);

        return builder.open();
    }

    // =========================================================
    // 辅助方法
    // =========================================================

    /**
     * 根据轨道类型添加特定菜单项
     */
    private void addTrackTypeSpecificMenu(TimelineContextMenuBuilder builder, TimelineTrack track) {
        TrackType type = track.getType();
        if (type == null) {
            return;
        }

        switch (type) {
            case CONTAINER -> // 容器轨道特定菜单
                    builder.submenu("容器动画")
                        .item("添加位置关键帧", () -> {
                            // TODO: 实现容器位置关键帧
                            LOGGER.debug("[Timeline] 添加容器位置关键帧");
                        })
                        .item("添加旋转关键帧", () -> {
                            // TODO: 实现容器旋转关键帧
                            LOGGER.debug("[Timeline] 添加容器旋转关键帧");
                        })
                        .item("添加缩放关键帧", () -> {
                            // TODO: 实现容器缩放关键帧
                            LOGGER.debug("[Timeline] 添加容器缩放关键帧");
                        })
                    .end();
            case CAMERA -> // 摄像机轨道特定菜单
                    builder.item("添加摄像机关键帧", () -> LOGGER.debug("[Timeline] 添加摄像机关键帧"));
            case AUDIO -> // 音频轨道特定菜单
                    builder.item("导入音频文件", () -> LOGGER.debug("[Timeline] 导入音频文件"));
            default -> {
                // 其他类型轨道暂无特定菜单
            }
        }
    }

    /**
     * 安全获取名称（防止 null）
     */
    private static String safeName(String name) {
        if (name == null || name.isEmpty()) {
            return "<未命名>";
        }
        return name;
    }

    /**
     * 检查是否有任何 Timeline 相关的弹窗正在打开
     * 可用于防止多个 popup 冲突
     */
    public static boolean anyTimelinePopupOpen() {
        return ImGui.isPopupOpen("ClipContextMenu")
            || ImGui.isPopupOpen("KeyframeContextMenu")
            || ImGui.isPopupOpen("TrackContextMenu")
            || ImGui.isPopupOpen("TimelineEmptyAreaMenu");
    }
}
