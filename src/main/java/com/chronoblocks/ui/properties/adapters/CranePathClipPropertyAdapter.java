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
import imgui.type.ImFloat;
import net.minecraft.util.math.Vec3d;
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
     * Header: Camera Path · CRANE · vertical move
     */
    private void renderHeader(CranePathClip clip) {
        ImGui.textDisabled("Camera Path");
        ImGui.sameLine();
        ImGui.text("CRANE");
        ImGui.sameLine();
        ImGui.textDisabled("· vertical move");
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
     */
    private boolean renderMotionSection(CranePathClip clip) {
        boolean modified = false;
        
        if (ImGui.collapsingHeader("Motion", ImGuiTreeNodeFlags.DefaultOpen)) {
            if (ImGui.sliderFloat("Height Change (blocks)", heightDeltaFloat.getData(), -32f, 32f, "%.1f")) {
                clip.setHeightDelta(heightDeltaFloat.get());
                modified = true;
            }
            
            ImGui.textDisabled(
                heightDeltaFloat.get() >= 0
                    ? "Positive = move up"
                    : "Negative = move down"
            );
        }
        
        return modified;
    }
    
    /**
     * Advanced: 高级选项（对齐设置、调试信息）
     */
    private boolean renderAdvancedSection(CranePathClip clip) {
        boolean modified = false;
        
        if (ImGui.collapsingHeader("Advanced", ImGuiTreeNodeFlags.None)) {
            boolean alignStart = clip.isAlignStartToCurrentPosition();
            if (ImGui.checkbox("Align start to current camera", alignStart)) {
                clip.setAlignStartToCurrentPosition(!alignStart);
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
            
            Vec3d currentPos = currentSample.position();
            
            // 从 yaw/pitch 计算 lookAt
            double yawRad = Math.toRadians(currentSample.yaw());
            double pitchRad = Math.toRadians(currentSample.pitch());
            double cosPitch = Math.cos(pitchRad);
            Vec3d currentLookAt = currentPos.add(
                -Math.sin(yawRad) * cosPitch * 10.0,
                -Math.sin(pitchRad) * 10.0,
                Math.cos(yawRad) * cosPitch * 10.0
            );
            
            // 构造 CameraPathEnterContext
            CameraPathEnterContext ctx = new CameraPathEnterContext(currentPos, currentLookAt);
            
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
            if (controller == null) {
                return;
            }
            
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
