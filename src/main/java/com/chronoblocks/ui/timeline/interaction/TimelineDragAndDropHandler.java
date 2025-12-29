package com.chronoblocks.ui.timeline.interaction;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;
import com.chronoblocks.core.container.ContainerAnimationPreset;
import com.chronoblocks.core.animation.ClipAnimationConfig;
import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.TrackType;
import com.chronoblocks.ui.timeline.render.TimelineRenderer;
import com.chronoblocks.ui.timeline.snapping.TimelineSnapping;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import imgui.ImGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;

/**
 * 处理时间轴上的拖放逻辑：容器拖入轨道、时间吸附、初始时长估算。
 */
public class TimelineDragAndDropHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineDragAndDropHandler.class);

    private final TimelineModel model;
    private final Runnable onDataChangedCallback;

    public TimelineDragAndDropHandler(TimelineModel model, Runnable onDataChangedCallback) {
        this.model = model;
        this.onDataChangedCallback = onDataChangedCallback;
    }

    /**
     * 处理拖放目标区域。
     *
     * @return 目标轨道索引（用于高亮显示）-1 表示无有效目标。
     */
    public int handleDragDropTarget(float trackRegionTop, float scrollY, float contentX) {
        int dropTargetIndex = -1;

        // 在 invisibleButton 之后设置拖放目标
        if (ImGui.beginDragDropTarget()) {
            float mouseX = ImGui.getMousePosX();
            int targetTrackIndex = getTargetTrackIndex(trackRegionTop, scrollY);
            boolean isValidTrack = checkTrackValidity(targetTrackIndex);

            // 设置高亮索引用于 Panel 渲染
            dropTargetIndex = isValidTrack ? targetTrackIndex : -1;

            // 接受载荷
            Object payload = ImGui.acceptDragDropPayload("CHRONOBLOCKS_CONTAINER");
            if (payload != null) {
                String containerId = (String) payload;
                // 拖放结束，重置高亮
                dropTargetIndex = -1;

                if (isValidTrack) {
                    handleContainerDropToTrack(containerId, targetTrackIndex, mouseX, contentX);
                } else {
                    LOGGER.warn("[DragDrop] Reject drop: target track does not accept containers");
                }
            }

            // 也接受其他类型的载荷（如果有的话）
            ImGui.acceptDragDropPayload("CHRONOBLOCKS_ITEM");
            ImGui.endDragDropTarget();
        }

        return dropTargetIndex;
    }

    /**
     * 根据鼠标位置计算目标轨道索引。
     */
    private int getTargetTrackIndex(float trackRegionTop, float scrollY) {
        float mouseY = ImGui.getMousePosY();
        float totalRowHeight = TimelineRenderer.getTrackHeight() + TimelineRenderer.getTrackGap();
        float relativeY = mouseY - trackRegionTop + scrollY;
        int index = (int) (relativeY / totalRowHeight);

        // 边界修正
        if (index < 0) index = 0;
        if (index >= model.getTrackCount()) {
            index = model.getTrackCount() > 0 ? model.getTrackCount() - 1 : 0;
        }
        return index;
    }

    /**
     * 检查轨道是否允许接收容器载荷。
     */
    private boolean checkTrackValidity(int index) {
        if (index < 0 || index >= model.getTrackCount()) return false;
        TimelineTrack track = model.getTrackAt(index);
        if (track == null) return false;

        TrackType type = track.getType();
        return type != null && type.acceptsContainerPayload();
    }

    /**
     * 处理容器拖放到轨道的具体逻辑。
     *
     * <p>规则：
     * <ul>
     *   <li>如果容器已经在时间线上有片段，则移动现有片段到新位置。</li>
     *   <li>如果容器还没有片段，则创建新片段。</li>
     *   <li>每个容器只能有一个片段。</li>
     * </ul>
     */
    private void handleContainerDropToTrack(String containerId, int trackIndex, float mouseX, float contentX) {
        try {
            // 支持批量拖拽：容器面板可能传入逗号分隔的多个 UUID
            String[] ids = containerId != null ? containerId.split(",") : new String[0];
            if (ids.length == 0) {
                return;
            }

            TimelineTrack targetTrack = model.getTrackAt(trackIndex);
            if (targetTrack == null) {
                LOGGER.warn("[DragDrop] 目标轨道不存在，索引: {}", trackIndex);
                return;
            }

            TrackType trackType = targetTrack.getType();

            for (String idStr : ids) {
                UUID containerUuid;
                try {
                    containerUuid = UUID.fromString(idStr.trim());
                } catch (Exception e) {
                    LOGGER.warn("[DragDrop] 无法解析容器 UUID: {}", idStr);
                    continue;
                }

                Container container = Objects.requireNonNull(ContainerManager.getInstance()).getContainer(containerUuid);
                if (container == null) {
                    LOGGER.warn("[DragDrop] 容器不存在: {}", containerUuid);
                    continue;
                }

                // 轨道类型与容器类型的校验
                if (trackType == TrackType.ERASE) {
                    if (container.getType() != com.chronoblocks.core.container.ContainerType.DEMOLITION) {
                        LOGGER.warn("[DragDrop] 拒绝拖放：清除轨道仅接受拆除容器（当前: {}）", container.getType());
                        continue;
                    }
                } else if (trackType == TrackType.CONTAINER) {
                    if (container.getType() == com.chronoblocks.core.container.ContainerType.DEMOLITION) {
                        LOGGER.warn("[DragDrop] 拒绝拖放：容器轨道不接受拆除容器");
                        continue;
                    }
                }

                // 检查容器是否已经在时间线上有片段
                var timelineService = com.chronoblocks.ui.timeline.container.ContainerTimelineService.getInstance();
                var existingClips = timelineService.findClipsForContainer(containerUuid);

                if (!existingClips.isEmpty()) {
                    // 容器已经有片段，移动现有片段到新位置
                    TimelineClip existingClip = existingClips.getFirst(); // 每个容器只有一个片段
                    TimelineTrack oldTrack = model.findTrackForClip(existingClip);

                    // 计算新位置时间与吸附
                    float dropTime = model.screenToTime(mouseX, contentX);
                    if (model.isSnappingEnabled()) {
                        TimelineSnapping.Result snap = TimelineSnapping.snapTime(model, dropTime, targetTrack, null, 0f);
                        dropTime = snap.snappedTime;
                    }
                    dropTime = Math.max(0f, Math.min(dropTime, model.getDuration() - 1f));

                    // 保持片段长度不变，只移动开始时间
                    float clipLength = existingClip.getEndTime() - existingClip.getStartTime();
                    existingClip.setStartTime(dropTime);
                    existingClip.setEndTime(dropTime + clipLength);

                    // 如果目标轨道不同，需要移动片段到新轨道
                    if (oldTrack != null && oldTrack != targetTrack) {
                        oldTrack.removeClip(existingClip);
                        targetTrack.addClip(existingClip);
                    } else if (oldTrack == null) {
                        // 片段没有父轨道，添加到目标轨道
                        targetTrack.addClip(existingClip);
                    }

                    // 标记时长需要重新计算（片段位置改变可能影响总时长）
                    model.markDurationDirty();

                    // 选中移动的片段
                    model.setSelectedClip(existingClip);

                    // 触发数据改变回调
                    onDataChangedCallback.run();

                    LOGGER.info("[DragDrop] 容器 {} 的片段已移动到轨道 {}，新位置: {}s",
                        container.getName(), targetTrack.getName(), dropTime);
                    continue;
                }

                // 容器还没有片段，创建新片段
                // 计算时间与吸附
                float dropTime = model.screenToTime(mouseX, contentX);
                if (model.isSnappingEnabled()) {
                    TimelineSnapping.Result snap = TimelineSnapping.snapTime(model, dropTime, targetTrack, null, 0f);
                    dropTime = snap.snappedTime;
                }
                dropTime = Math.max(0f, Math.min(dropTime, model.getDuration() - 1f));

                // 计算初始片段时长
                float duration = calculateInitialClipDuration(container);

                // 创建片段（颜色跟随轨道类型）
                int clipColor = trackType != null ? trackType.getClipColor() : 0xFF4A90E2;
                TimelineClip clip = new TimelineClip(
                    container.getName(),
                    dropTime,
                    dropTime + duration,
                    clipColor
                );

                // 绑定容器 ID 和名称
                clip.bindToContainer(containerUuid, container.getName());
            
                // ★ 重要：从容器的动画预设初始化片段的动画配置
                // 片段有独立的动画配置，不依赖容器的状态
                initializeClipAnimationConfig(clip, container);

                // 清除轨道默认使用“淡出”更符合直觉（不强制，用户可改）
                if (trackType == TrackType.ERASE) {
                    ClipAnimationConfig cfg = clip.getAnimationConfig();
                    cfg.setPhase(ClipAnimationConfig.AnimationPhase.EXIT);
                    cfg.setMode(com.chronoblocks.core.container.AnimationMode.FADE_OUT);
                    clip.setAnimationConfig(cfg);
                }

                // 添加到目标轨道
                targetTrack.addClip(clip);

                // 标记时长需要重新计算（新片段可能超过当前总时长）
                model.markDurationDirty();

                // 轨道分支：
                // - 容器轨道：拖入即隐藏容器，确保播放用快照恢复
                // - 清除轨道：不隐藏容器，仅确保存在快照（用于可逆清除/拖动恢复）
                try {
                    ensureContainerWorldBinding(container);

                    var visibilityService = com.chronoblocks.core.container.visibility.ContainerServices.visibility();
                    if (visibilityService != null) {
                        if (trackType == TrackType.CONTAINER) {
                            if (container.getState() != com.chronoblocks.core.container.ContainerState.HIDDEN) {
                                visibilityService.hideContainer(container);
                                container.setState(com.chronoblocks.core.container.ContainerState.HIDDEN);
                                LOGGER.info("[DragDrop] 容器 {} 已隐藏（已添加到容器轨道）", container.getName());
                            }
                        } else if (trackType == TrackType.ERASE) {
                            // 预热快照：不改世界、不改容器状态
                            visibilityService.createSnapshotFromBlockStore(container);
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warn("[DragDrop] 初始化容器轨道行为失败: {}", container.getName(), e);
                }

                // 自动选中新创建的片段，使其可以立即被拖动
                model.setSelectedClip(clip);

                // 触发数据改变回调
                onDataChangedCallback.run();

                LOGGER.info("[DragDrop] Container {} 已添加到轨道 {}，时间 {}s，时长 {}s",
                    container.getName(), targetTrack.getName(), dropTime, duration);
            }
        } catch (Exception e) {
            LOGGER.error("[DragDrop] 处理容器拖放失败", e);
        }
    }
    
    /**
     * 确保容器绑定了世界
     * 
     * <p>如果容器没有绑定世界，尝试从客户端获取当前世界并绑定
     * 
     * @param container 容器对象
     */
    private void ensureContainerWorldBinding(Container container) {
        if (container == null) {
            return;
        }
        
        if (container.getLevelKey() != null) {
            return; // 已经绑定世界
        }
        
        // 尝试从客户端获取当前世界
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.world != null) {
                RegistryKey<World> worldKey = client.world.getRegistryKey();
                container.setLevelKey(worldKey);
                
                LOGGER.info("[DragDrop] 容器 {} 已绑定世界: {}",
                    container.getName(), worldKey.getValue());
            } else {
                LOGGER.warn("[DragDrop] 无法获取客户端世界，容器 {} 无法绑定世界",
                    container.getName());
            }
        } catch (Exception e) {
            LOGGER.error("[DragDrop] 绑定容器世界失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 根据容器中的方块数量计算片段的初始时间长度。
     */
    private float calculateInitialClipDuration(Container container) {
        if (container == null) {
            return 2.0f; // 默认 2 秒
        }

        // 获取容器中的方块数量（包括子容器的总方块数）
        int blockCount = container.getTotalBlockCount();

        // 计算时间长度：每 10 个方块 2 秒
        float duration = (blockCount / 10.0f) * 2.0f;

        // 最少 2 秒
        duration = Math.max(2.0f, duration);

        // 最多 60 秒
        duration = Math.min(60.0f, duration);

        LOGGER.debug("[DragDrop] 容器 {} 有 {} 个方块，计算初始时长: {}s",
            container.getName(), blockCount, duration);

        return duration;
    }
    
    /**
     * 初始化片段的动画配置
     * 
     * <p>重要：片段有独立的动画配置，不依赖容器的状态。
     * 当容器被拖动到时间线时，会从容器的默认动画预设中初始化片段的配置，
     * 但之后片段的配置可以独立修改，不会影响容器，容器的状态也不会影响片段。
     * 
     * <p>流程：
     * <ol>
     *   <li>从容器的 {@link ContainerAnimationPreset} 获取默认配置</li>
     *   <li>创建 {@link ClipAnimationConfig} 并设置动画模式、时长、缓动等</li>
     *   <li>将配置应用到片段，使片段拥有独立的动画控制能力</li>
     * </ol>
     * 
     * @param clip 时间线片段
     * @param container 容器对象
     */
    private void initializeClipAnimationConfig(TimelineClip clip, Container container) {
        if (clip == null || container == null) {
            LOGGER.warn("[DragDrop] 无法初始化片段动画配置：clip 或 container 为空");
            return;
        }
        
        try {
            // 获取容器的默认动画预设
            ContainerAnimationPreset preset = container.getDefaultAnimationPreset();
            if (preset == null) {
                preset = ContainerAnimationPreset.defaultPreset();
            }
            
            // 从预设创建动画配置
            // 注意：使用片段的实际时长，而不是预设的建议时长
            float clipDuration = clip.getLength();
            float animationDuration = Math.min(preset.getSuggestedDuration(), clipDuration);
            
            ClipAnimationConfig config = ClipAnimationConfig.fromPreset(
                preset.getMode(),
                animationDuration,
                preset.getDelay(),
                preset.getEasing(),
                preset.getOrdering()
            );

            // ✅ 兼容旧预设：把“随机/螺旋/切片/波浪/扩散/向下生长”等迁移到 SequenceParameters
            com.chronoblocks.core.animation.LegacyAnimationModeMigrator.migrateIfNeeded(config);
            
            // 复制预设中的额外参数
            if (!preset.getParams().isEmpty()) {
                config.getParams().putAll(preset.getParams());
            }
            
            // 应用配置到片段
            clip.setAnimationConfig(config);
            
            LOGGER.info("[DragDrop] 片段 {} 动画配置已初始化：模式={}, 时长={}s, 缓动={}",
                clip.getLabel(),
                preset.getMode(),
                animationDuration,
                preset.getEasing());
        } catch (Exception e) {
            LOGGER.error("[DragDrop] 初始化片段动画配置失败，使用默认配置", e);
            // 使用默认配置作为后备
            clip.setAnimationConfig(ClipAnimationConfig.defaultConfig());
        }
    }
}
