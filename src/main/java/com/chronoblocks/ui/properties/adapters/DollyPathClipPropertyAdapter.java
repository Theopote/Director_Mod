package com.chronoblocks.ui.properties.adapters;

import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.timeline.container.ContainerTimelineService;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.camera.path.CameraPathEnterContext;
import com.chronoblocks.ui.timeline.model.camera.path.DollyPathClip;
import com.chronoblocks.ui.timeline.playback.camera.CameraRuntime;
import com.chronoblocks.ui.timeline.playback.camera.CameraSample;
import com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImFloat;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DollyPathClipPropertyAdapter - 推拉路径片段属性适配器
 * 
 * <p>按照导演视角的 UI 分区组织，提供友好的参数编辑体验。
 * 
 * <p>统一分区结构：
 * <ul>
 *   <li>Header: Camera Path · DOLLY · push / pull</li>
 *   <li>Timing: 时间信息（只读）</li>
 *   <li>Motion: 核心语义参数（Distance）</li>
 *   <li>Direction: 方向模式（如果需要）</li>
 *   <li>Advanced: 高级选项（对齐设置）</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 2.0
 */
public class DollyPathClipPropertyAdapter implements IPropertyAdapter<DollyPathClip> {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DollyPathClipPropertyAdapter.class);
    
    // ⭐ 性能优化：缓存 ImGui 对象
    private final ImFloat distanceFloat = new ImFloat(0f);
    private final ImFloat[] fixedDirectionArr = new ImFloat[]{new ImFloat(0f), new ImFloat(0f), new ImFloat(1f)};
    private DollyPathClip lastClip = null;
    
    // ⭐ 性能优化：跟踪滑动条拖动状态，避免拖动时频繁调用 markDirty()
    private boolean wasDistanceSliderActive = false;
    
    @Override
    public Class<DollyPathClip> getTargetType() {
        return DollyPathClip.class;
    }
    
    @Override
    public boolean supports(Object target) {
        return target instanceof DollyPathClip;
    }
    
    @Override
    public boolean renderProperties(DollyPathClip clip) {
        if (clip == null) {
            ImGui.textDisabled("未选择推拉路径片段");
            lastClip = null;
            return false;
        }
        
        // ⭐ 性能优化：只在 clip 变化时更新缓存值
        if (clip != lastClip) {
            distanceFloat.set(clip.getDistance());
            Vec3d fixedDir = clip.getFixedDirection();
            fixedDirectionArr[0].set((float) fixedDir.x);
            fixedDirectionArr[1].set((float) fixedDir.y);
            fixedDirectionArr[2].set((float) fixedDir.z);
            lastClip = clip;
            
            // ⭐ 重置滑动条拖动状态（切换 clip 时）
            wasDistanceSliderActive = false;
        }
        
        boolean modified = false;
        
        // ⭐ 实时预览：检查播放头是否在当前 clip 上
        boolean isPlayheadOnClip = isPlayheadOnClip(clip);
        
        // ========== Header ==========
        renderHeader(clip);
        ImGui.separator();
        
        // ========== Timing ==========
        modified |= renderTiming(clip);
        ImGui.separator();
        
        // ========== Motion ==========
        modified |= renderMotionSection(clip);
        ImGui.separator();
        
        // ========== Direction ==========
        modified |= renderDirectionSection(clip);
        ImGui.separator();
        
        // ========== Advanced ==========
        modified |= renderAdvancedSection(clip);
        
        // ⭐ 性能优化：如果值被外部修改，同步更新缓存
        if (!modified) {
            syncCacheFromClip(clip);
        }
        
        // ⭐ 实时预览：如果参数被修改且播放头在当前 clip 上，立即更新摄像机
        if (modified && isPlayheadOnClip) {
            updateCameraPreview(clip);
        }
        
        return modified;
    }
    
    // ========== UI Sections ==========
    
    /**
     * Header: 🎥 Camera Path · DOLLY
     * Push / Pull
     */
    private void renderHeader(DollyPathClip clip) {
        // 第一行：类型标识（突出显示）
        ImGui.text("🎥 Camera Path");
        ImGui.sameLine();
        ImGui.text("· DOLLY");
        
        // 第二行：语义描述（稍小，灰色）
        ImGui.textDisabled("Push / Pull");
    }
    
    /**
     * Timing: 时间信息（只读）
     */
    private boolean renderTiming(DollyPathClip clip) {
        boolean modified = false;
        
        if (ImGui.collapsingHeader("Timing", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.beginDisabled();
            float startTime = clip.getStartTime();
            float endTime = clip.getEndTime();
            float duration = clip.getDuration();
            
            ImGui.text(String.format("Start: %.2fs", startTime));
            ImGui.text(String.format("End:   %.2fs", endTime));
            ImGui.text(String.format("Duration: %.2fs", duration));
            ImGui.endDisabled();
            
            ImGui.textDisabled("Edit timing on the timeline.");
        }
        
        return modified;
    }
    
    /**
     * Motion: 核心语义参数（Distance）
     * 添加方向视觉提示：← Pull Back     Distance (blocks)     Push In →
     */
    private boolean renderMotionSection(DollyPathClip clip) {
        boolean modified = false;
        
        if (ImGui.collapsingHeader("Motion", ImGuiTreeNodeFlags.DefaultOpen)) {
            // ⭐ 方向提示：在 slider 上方显示方向语义
            ImGui.textDisabled("← Pull Back");
            ImGui.sameLine();
            
            // 计算可用宽度，使 slider 居中
            float availableWidth = ImGui.getContentRegionAvailX();
            float leftTextWidth = ImGui.calcTextSize("← Pull Back").x;
            float rightTextWidth = ImGui.calcTextSize("Push In →").x;
            float spacing = 10f; // 左右间距
            
            // 计算 slider 的起始位置（居中）
            float sliderStartX = ImGui.getCursorPosX() + (availableWidth - leftTextWidth - rightTextWidth - spacing * 2) / 2f;
            ImGui.setCursorPosX(sliderStartX);
            
            // Slider（使用隐藏的 label，实际显示在 slider 内部）
            String sliderLabel = "Distance (blocks)";
            boolean distanceSliderChanged = ImGui.sliderFloat(sliderLabel, distanceFloat.getData(), -32f, 32f, "%.1f");
            boolean isDistanceSliderActive = ImGui.isItemActive();
            
            if (distanceSliderChanged) {
                clip.setDistance(distanceFloat.get());
                // ⭐ 性能优化：只在拖动结束时才标记为已修改（触发 markDirty）
                // 如果滑动条正在被拖动，不标记为 modified，避免频繁触发 markDirty()
                if (!isDistanceSliderActive) {
                    modified = true;
                }
            }
            
            // ⭐ 检测拖动结束：从 active 变为 inactive
            if (wasDistanceSliderActive && !isDistanceSliderActive) {
                modified = true; // 拖动结束，标记为已修改
            }
            wasDistanceSliderActive = isDistanceSliderActive;
            
            // 右侧方向提示
            ImGui.sameLine();
            ImGui.textDisabled("Push In →");
        }
        
        return modified;
    }
    
    /**
     * Direction: 方向模式（语义化改进）
     * 改成意图式选择，对导演更友好
     */
    private boolean renderDirectionSection(DollyPathClip clip) {
        boolean modified = false;
        
        if (ImGui.collapsingHeader("Direction", ImGuiTreeNodeFlags.DefaultOpen)) {
            DollyPathClip.DirectionMode mode = clip.getDirectionMode();
            
            // ⭐ 语义化选项 1：沿镜头朝向（最常用）
            if (ImGui.radioButton("● Along Camera View", mode == DollyPathClip.DirectionMode.CAMERA_FORWARD)) {
                if (mode != DollyPathClip.DirectionMode.CAMERA_FORWARD) {
                    clip.setDirectionMode(DollyPathClip.DirectionMode.CAMERA_FORWARD);
                    modified = true;
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("沿当前摄像机的镜头朝向推拉（最常用）");
            }
            
            // ⭐ 语义化选项 2：向焦点推拉
            if (ImGui.radioButton("○ Towards Focus Point", mode == DollyPathClip.DirectionMode.TOWARDS_TARGET)) {
                if (mode != DollyPathClip.DirectionMode.TOWARDS_TARGET) {
                    clip.setDirectionMode(DollyPathClip.DirectionMode.TOWARDS_TARGET);
                    modified = true;
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("向当前注视点（Focus Point）推近或拉远");
            }
            
            // ⭐ 语义化选项 3：自定义方向（高级）
            if (ImGui.radioButton("○ Custom Direction", mode == DollyPathClip.DirectionMode.FIXED_DIRECTION)) {
                if (mode != DollyPathClip.DirectionMode.FIXED_DIRECTION) {
                    clip.setDirectionMode(DollyPathClip.DirectionMode.FIXED_DIRECTION);
                    modified = true;
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("使用固定的世界方向（高级选项）");
            }
            
            // ⭐ Custom Direction 输入（仅在 FIXED_DIRECTION 模式下显示，放在子区域）
            if (clip.getDirectionMode() == DollyPathClip.DirectionMode.FIXED_DIRECTION) {
                ImGui.spacing();
                ImGui.indent();
                ImGui.textDisabled("Custom Direction Vector:");
                float[] dirArr = {fixedDirectionArr[0].get(), fixedDirectionArr[1].get(), fixedDirectionArr[2].get()};
                boolean directionChanged = ImGui.dragFloat3("##direction_vector", dirArr, 0.01f);
                boolean isDirectionActive = ImGui.isItemActive();
                
                if (directionChanged) {
                    Vec3d newDir = new Vec3d(dirArr[0], dirArr[1], dirArr[2]);
                    clip.setFixedDirection(newDir);
                    fixedDirectionArr[0].set(dirArr[0]);
                    fixedDirectionArr[1].set(dirArr[1]);
                    fixedDirectionArr[2].set(dirArr[2]);
                    // ⭐ 性能优化：只在拖动结束时才标记为已修改
                    if (!isDirectionActive) {
                        modified = true;
                    }
                }
                
                // ⭐ 检测拖动结束
                if (directionChanged && !isDirectionActive) {
                    modified = true;
                }
                
                ImGui.textDisabled("Will be normalized automatically");
                ImGui.unindent();
            }
        }
        
        return modified;
    }
    
    /**
     * Advanced: 高级选项（对齐设置、调试信息）
     */
    private boolean renderAdvancedSection(DollyPathClip clip) {
        boolean modified = false;
        
        if (ImGui.collapsingHeader("Advanced", ImGuiTreeNodeFlags.None)) {
            ImBoolean align = new ImBoolean(clip.isAlignStartToCurrentPosition());
            if (ImGui.checkbox("Align start to current camera", align)) {
                clip.setAlignStartToCurrentPosition(align.get());
                modified = true;
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("启用后，进入 PathClip 时会自动对齐到当前摄像机位置，实现无缝衔接");
            }
            
            // 调试信息（可折叠）
            if (ImGui.collapsingHeader("Debug Info", ImGuiTreeNodeFlags.None)) {
                Vec3d startPos = clip.getStartPosition();
                Vec3d dollyDir = clip.getDollyDirection();
                if (startPos != null && dollyDir != null) {
                    ImGui.textDisabled(String.format("Start Position: (%.2f, %.2f, %.2f)", 
                            startPos.x, startPos.y, startPos.z));
                    ImGui.textDisabled(String.format("Dolly Direction: (%.2f, %.2f, %.2f)", 
                            dollyDir.x, dollyDir.y, dollyDir.z));
                } else {
                    ImGui.textDisabled("Not initialized (will be calculated on enter)");
                }
                
                // 手动对齐按钮
                if (ImGui.button("Align from current camera")) {
                    alignFromCurrentCamera(clip);
                    modified = true;
                }
            }
        }
        
        return modified;
    }
    
    // ========== Helper Methods ==========
    
    /**
     * 同步缓存值（从 clip 读取最新值）
     */
    private void syncCacheFromClip(DollyPathClip clip) {
        if (Math.abs(distanceFloat.get() - clip.getDistance()) > 0.01f) {
            distanceFloat.set(clip.getDistance());
        }
        Vec3d fixedDir = clip.getFixedDirection();
        if (Math.abs(fixedDirectionArr[0].get() - (float) fixedDir.x) > 0.01f ||
            Math.abs(fixedDirectionArr[1].get() - (float) fixedDir.y) > 0.01f ||
            Math.abs(fixedDirectionArr[2].get() - (float) fixedDir.z) > 0.01f) {
            fixedDirectionArr[0].set((float) fixedDir.x);
            fixedDirectionArr[1].set((float) fixedDir.y);
            fixedDirectionArr[2].set((float) fixedDir.z);
        }
    }
    
    /**
     * ⭐ 从当前摄像机状态立即对齐（UI 主动调用）
     */
    private void alignFromCurrentCamera(DollyPathClip clip) {
        try {
            CameraRuntime runtime = CameraRuntime.getInstance();
            CameraSample currentSample = runtime.getCurrentSample();
            
            if (currentSample == null) {
                LOGGER.warn("[DollyPathClipPropertyAdapter] 无法获取当前摄像机状态，无法对齐");
                return;
            }

            CameraPathEnterContext ctx = getCameraPathEnterContext(currentSample);

            // 调用对齐方法
            clip.onEnter(ctx);
            
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[DollyPathClipPropertyAdapter] 已从当前摄像机状态对齐: 位置 {}, 方向 {}", 
                        clip.getStartPosition(), clip.getDollyDirection());
            }
        } catch (Exception e) {
            LOGGER.error("[DollyPathClipPropertyAdapter] 对齐时发生错误", e);
        }
    }

    private static @NotNull CameraPathEnterContext getCameraPathEnterContext(CameraSample currentSample) {
        Vec3d currentPos = currentSample.position();
        // ⭐ 使用 yaw/pitch 构造函数，更精确且支持 CAMERA_FORWARD 模式
        return new CameraPathEnterContext(currentPos, currentSample.yaw(), currentSample.pitch());
    }

    /**
     * ⭐ 检查播放头是否在当前 clip 的时间范围内
     */
    private boolean isPlayheadOnClip(DollyPathClip clip) {
        TimelineModel timelineModel = ContainerTimelineService.getInstance().getTimelineModel();
        if (timelineModel == null) {
            return false;
        }
        
        float currentTime = timelineModel.getCurrentTime();
        float clipStartTime = clip.getStartTime();
        float clipEndTime = clip.getEndTime();
        
        return currentTime >= clipStartTime && currentTime <= clipEndTime;
    }
    
    /**
     * ⭐ 实时预览：当参数被修改且播放头在 clip 上时，立即更新摄像机
     */
    private void updateCameraPreview(DollyPathClip clip) {
        try {
            TimelineModel timelineModel = ContainerTimelineService.getInstance().getTimelineModel();
            if (timelineModel == null) {
                return;
            }
            
            TimelineCameraController controller = TimelineCameraController.getInstance();

            float currentTime = timelineModel.getCurrentTime();
            controller.previewAt(currentTime);
            
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[DollyPathClipPropertyAdapter] 实时预览更新摄像机 @ {}s", currentTime);
            }
        } catch (Exception e) {
            LOGGER.error("[DollyPathClipPropertyAdapter] 实时预览更新摄像机时发生错误", e);
        }
    }
}
