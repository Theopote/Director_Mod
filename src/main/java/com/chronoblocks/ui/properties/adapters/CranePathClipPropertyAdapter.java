package com.chronoblocks.ui.properties.adapters;

import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.timeline.container.ContainerTimelineService;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.camera.path.CameraPathEnterContext;
import com.chronoblocks.ui.timeline.model.camera.path.CranePathClip;
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
 * CranePathClipPropertyAdapter - 升降路径片段属性适配器
 * 
 * <p>按照导演视角的 UI 分区组织，提供友好的参数编辑体验。
 * 
 * <p>统一分区结构：
 * <ul>
 *   <li>Header: Camera Path · CRANE · vertical move</li>
 *   <li>Timing: 时间信息（只读）</li>
 *   <li>Motion: 核心语义参数（Height Delta）</li>
 *   <li>Advanced: 高级选项（对齐设置）</li>
 * </ul>
 * 
 * <p>Crane 的导演语义：镜头只做升降，构图完全不动。
 * 
 * @author ChronoBlocks Team
 * @version 2.0
 */
public class CranePathClipPropertyAdapter implements IPropertyAdapter<CranePathClip> {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(CranePathClipPropertyAdapter.class);
    
    // ⭐ 性能优化：缓存 ImGui 对象
    private final ImFloat heightDeltaFloat = new ImFloat(0f);
    private CranePathClip lastClip = null;
    
    // ⭐ 性能优化：跟踪滑动条拖动状态，避免拖动时频繁调用 markDirty()
    private boolean wasHeightDeltaSliderActive = false;
    
    @Override
    public Class<CranePathClip> getTargetType() {
        return CranePathClip.class;
    }
    
    @Override
    public boolean supports(Object target) {
        return target instanceof CranePathClip;
    }
    
    @Override
    public boolean renderProperties(CranePathClip clip) {
        if (clip == null) {
            ImGui.textDisabled("未选择升降路径片段");
            lastClip = null;
            return false;
        }
        
        // ⭐ 性能优化：只在 clip 变化时更新缓存值
        if (clip != lastClip) {
            heightDeltaFloat.set(clip.getHeightDelta());
            lastClip = clip;
            
            // ⭐ 重置滑动条拖动状态（切换 clip 时）
            wasHeightDeltaSliderActive = false;
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
     * Header: 🎥 Camera Path · CRANE
     * Vertical Move
     */
    private void renderHeader(CranePathClip clip) {
        // 第一行：类型标识（突出显示）
        ImGui.text("🎥 Camera Path");
        ImGui.sameLine();
        ImGui.text("· CRANE");
        
        // 第二行：语义描述（稍小，灰色）
        ImGui.textDisabled("Vertical Move");
    }
    
    /**
     * Timing: 时间信息（只读）
     */
    private boolean renderTiming(CranePathClip clip) {
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
     * Motion: 核心语义参数（Height Delta）
     * 增强升降语义：↓ Down     Height (blocks)     Up ↑
     */
    private boolean renderMotionSection(CranePathClip clip) {
        boolean modified = false;
        
        if (ImGui.collapsingHeader("Motion", ImGuiTreeNodeFlags.DefaultOpen)) {
            // ⭐ 方向提示：在 slider 上方显示升降语义
            ImGui.textDisabled("↓ Down");
            ImGui.sameLine();
            
            // 计算可用宽度，使 slider 居中
            float availableWidth = ImGui.getContentRegionAvailX();
            float leftTextWidth = ImGui.calcTextSize("↓ Down").x;
            float rightTextWidth = ImGui.calcTextSize("Up ↑").x;
            float spacing = 10f; // 左右间距
            
            // 计算 slider 的起始位置（居中）
            float sliderStartX = ImGui.getCursorPosX() + (availableWidth - leftTextWidth - rightTextWidth - spacing * 2) / 2f;
            ImGui.setCursorPosX(sliderStartX);
            
            // Slider（使用更语义化的 label）
            String sliderLabel = "Height (blocks)";
            boolean heightDeltaSliderChanged = ImGui.sliderFloat(sliderLabel, heightDeltaFloat.getData(), -32f, 32f, "%.1f");
            boolean isHeightDeltaSliderActive = ImGui.isItemActive();
            
            if (heightDeltaSliderChanged) {
                clip.setHeightDelta(heightDeltaFloat.get());
                // ⭐ 性能优化：只在拖动结束时才标记为已修改（触发 markDirty）
                // 如果滑动条正在被拖动，不标记为 modified，避免频繁触发 markDirty()
                if (!isHeightDeltaSliderActive) {
                    modified = true;
                }
            }
            
            // ⭐ 检测拖动结束：从 active 变为 inactive
            if (wasHeightDeltaSliderActive && !isHeightDeltaSliderActive) {
                modified = true; // 拖动结束，标记为已修改
            }
            wasHeightDeltaSliderActive = isHeightDeltaSliderActive;
            
            // 右侧方向提示
            ImGui.sameLine();
            ImGui.textDisabled("Up ↑");
            
            // ⭐ 解释性文本：强调 Crane 的构图稳定性
            ImGui.spacing();
            ImGui.textDisabled("Keeps horizontal framing stable");
            
            // ⭐ 微调提示：告诉用户如何精确调整
            ImGui.textDisabled("Tip: Hold Ctrl for fine adjustment");
        }
        
        return modified;
    }
    
    /**
     * Advanced: 高级选项（对齐设置、调试信息）
     */
    private boolean renderAdvancedSection(CranePathClip clip) {
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
                if (startPos != null) {
                    ImGui.textDisabled(String.format("Start Position: (%.2f, %.2f, %.2f)", 
                            startPos.x, startPos.y, startPos.z));
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
    private void syncCacheFromClip(CranePathClip clip) {
        if (Math.abs(heightDeltaFloat.get() - clip.getHeightDelta()) > 0.01f) {
            heightDeltaFloat.set(clip.getHeightDelta());
        }
    }
    
    /**
     * ⭐ 从当前摄像机状态立即对齐（UI 主动调用）
     */
    private void alignFromCurrentCamera(CranePathClip clip) {
        try {
            CameraRuntime runtime = CameraRuntime.getInstance();
            CameraSample currentSample = runtime.getCurrentSample();
            
            if (currentSample == null) {
                LOGGER.warn("[CranePathClipPropertyAdapter] 无法获取当前摄像机状态，无法对齐");
                return;
            }

            CameraPathEnterContext ctx = getCameraPathEnterContext(currentSample);

            // 调用对齐方法
            clip.onEnter(ctx);
            
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[CranePathClipPropertyAdapter] 已从当前摄像机状态对齐: 位置 {}", 
                        clip.getStartPosition());
            }
        } catch (Exception e) {
            LOGGER.error("[CranePathClipPropertyAdapter] 对齐时发生错误", e);
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
    private boolean isPlayheadOnClip(CranePathClip clip) {
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
    private void updateCameraPreview(CranePathClip clip) {
        try {
            TimelineModel timelineModel = ContainerTimelineService.getInstance().getTimelineModel();
            if (timelineModel == null) {
                return;
            }
            
            TimelineCameraController controller = TimelineCameraController.getInstance();

            float currentTime = timelineModel.getCurrentTime();
            controller.previewAt(currentTime);
            
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[CranePathClipPropertyAdapter] 实时预览更新摄像机 @ {}s", currentTime);
            }
        } catch (Exception e) {
            LOGGER.error("[CranePathClipPropertyAdapter] 实时预览更新摄像机时发生错误", e);
        }
    }
}
