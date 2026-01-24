package com.chronoblocks.ui.properties.adapters;

import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.theme.UITheme;
import com.chronoblocks.ui.timeline.container.ContainerTimelineService;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.camera.path.CameraPathEnterContext;
import com.chronoblocks.ui.timeline.model.camera.path.OrbitPathClip;
import com.chronoblocks.ui.timeline.playback.camera.CameraRuntime;
import com.chronoblocks.ui.timeline.playback.camera.CameraSample;
import com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImFloat;
import imgui.type.ImString;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OrbitPathClipPropertyAdapter - 围绕路径片段属性适配器（重构版）
 * 
 * <p>按照导演视角的 UI 分区组织，提供友好的参数编辑体验。
 * 
 * <p>UI 分区：
 * <ul>
 *   <li>分区 1：路径类型与时间（只读）</li>
 *   <li>分区 2：围绕中心（Center / LookAt，支持 Pick From World）</li>
 *   <li>分区 3：运动形态（Radius, Height, Direction）</li>
 *   <li>分区 4：角度区间（Start/End Angle，带 Full Orbit 按钮）</li>
 *   <li>分区 5：调试与可视化（可选）</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 2.0
 */
public class OrbitPathClipPropertyAdapter implements IPropertyAdapter<OrbitPathClip> {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(OrbitPathClipPropertyAdapter.class);
    
    // ⭐ 性能优化：缓存 ImGui 对象，避免每帧创建新对象
    private ImString nameBuffer = new ImString("", 256);
    private final ImFloat radiusFloat = new ImFloat(0f);
    private final ImFloat heightFloat = new ImFloat(0f);
    private final ImFloat startAngleFloat = new ImFloat(0f);
    private final ImFloat endAngleFloat = new ImFloat(0f);
    private OrbitPathClip lastClip = null; // 用于检测 clip 变化，只在变化时更新缓存
    
    // ⭐ 性能优化：缓存上次显示的时间值，用于检测时间变化并强制刷新
    private float lastDisplayedStartTime = -1f;
    private float lastDisplayedEndTime = -1f;
    private float lastDisplayedDuration = -1f;
    
    // ⭐ 世界拾取状态
    private enum PickMode {
        NONE,           // 未激活
        PICK_CENTER,    // 拾取中心点
        PICK_LOOKAT     // 拾取注视点
    }
    private PickMode currentPickMode = PickMode.NONE;
    private OrbitPathClip pickingClip = null; // 正在拾取的 clip
    
    // ⭐ Edge trigger 状态跟踪（用于检测鼠标"刚按下"）
    private static boolean lastLeftMouseDown = false;
    
    // ⭐ 性能优化：跟踪滑动条拖动状态，避免拖动时频繁调用 markDirty()
    private boolean wasRadiusSliderActive = false;
    private boolean wasStartAngleSliderActive = false;
    private boolean wasEndAngleSliderActive = false;
    
    @Override
    public Class<OrbitPathClip> getTargetType() {
        return OrbitPathClip.class;
    }

    @Override
    public int getPriority() { return 80; }
    
    @Override
    public boolean supports(Object target) {
        return target instanceof OrbitPathClip;
    }
    
    @Override
    public boolean renderProperties(OrbitPathClip clip) {
        if (clip == null) {
            ImGui.textDisabled("未选择围绕路径片段");
            lastClip = null;
            currentPickMode = PickMode.NONE;
            pickingClip = null;
            return false;
        }
        
        // ⭐ 性能优化：只在 clip 变化时更新缓存值，避免每帧都更新
        if (clip != lastClip) {
            nameBuffer.set(clip.getName());
            radiusFloat.set(clip.getRadius());
            // ⭐ 高度：只在 ABSOLUTE 模式下更新缓存
            if (clip.getHeightMode() == com.chronoblocks.ui.timeline.model.camera.path.OrbitPathClip.HeightMode.ABSOLUTE) {
                heightFloat.set(clip.getHeight());
            }
            startAngleFloat.set(clip.getStartAngle());
            endAngleFloat.set(clip.getEndAngle());
            lastClip = clip;
            
            // 如果切换了 clip，取消拾取模式
            if (pickingClip != clip) {
                currentPickMode = PickMode.NONE;
                pickingClip = null;
            }
            
            // ⭐ 重置滑动条拖动状态（切换 clip 时）
            wasRadiusSliderActive = false;
            wasStartAngleSliderActive = false;
            wasEndAngleSliderActive = false;
            
            // ⭐ 重置时间显示缓存（切换 clip 时）
            lastDisplayedStartTime = -1f;
            lastDisplayedEndTime = -1f;
            lastDisplayedDuration = -1f;
        }
        
        // ⭐ 处理世界拾取（在 UI 渲染之前检查）
        handleWorldPick(clip);
        
        boolean modified = false;
        
        // ⭐ 实时预览：检查播放头是否在当前 clip 上
        boolean isPlayheadOnClip = isPlayheadOnClip(clip);
        
        // ========== 分区 1：路径类型与时间（只读）==========
        renderHeader(clip);
        ImGui.separator();
        
        // ========== 分区 2：围绕中心 ==========
        modified |= renderCenterSection(clip);
        UITheme.spacingSmall();
        
        // ========== 分区 3：运动形态 ==========
        modified |= renderMotionSection(clip);
        UITheme.spacingSmall();
        
        // ========== 分区 4：角度区间 ==========
        modified |= renderAngleSection(clip);
        UITheme.spacingSmall();
        
        // ========== 分区 5：调试与可视化（可选，折叠）==========
        // 注意：调试选项可以后续扩展，目前先留空
        // 未来可以添加：Show Path in World、Show Direction Arrow、Samples count 等选项
        
        // ⭐ 性能优化：如果值被外部修改，同步更新缓存（避免显示过时值）
        if (!modified) {
            syncCacheFromClip(clip);
        }
        
        // ⭐ 实时预览：如果参数被修改且播放头在当前 clip 上，立即更新摄像机
        if (modified && isPlayheadOnClip) {
            updateCameraPreview(clip);
        }
        
        return modified;
    }
    
    /**
     * 分区 1：路径类型与时间（只读）
     * 
     * <p>⭐ 性能优化：实时显示时间信息，当用户在时间线上拖动或调整片段时，
     * 时间显示会立即更新，无需等待选择变化。
     * 
     * <p>参考容器面板的时间显示逻辑：每次渲染都直接从 clip 读取最新值，
     * 直接使用 ImGui.text() 显示，确保每次渲染都显示最新值。
     */
    private void renderHeader(OrbitPathClip clip) {
        UITheme.title(Icons.CAMERA + " Orbit Camera Path");
        
        // ⭐ 实时读取最新时间值（确保拖动时能实时更新）
        // ⭐ 每次渲染都直接从 clip 读取，不依赖缓存，确保拖动过程中实时更新
        // ⭐ 参考 EntitySpawnClipPropertyAdapter 的做法：直接使用 ImGui.text() 显示时间
        float currentStartTime = clip.getStartTime();
        float currentEndTime = clip.getEndTime();
        float currentDuration = clip.getDuration();
        
        // 时间范围（只读，通过 Timeline 控制）
        // ⭐ 直接使用 ImGui.text() 显示，每次渲染都会显示最新值
        // ⭐ 不使用 pushID，避免 ImGui 缓存文本内容
        ImGui.textDisabled(String.format("时长: %.2f 秒", currentDuration));
        ImGui.textDisabled(String.format("开始: %.2f 秒", currentStartTime));
        ImGui.textDisabled(String.format("结束: %.2f 秒", currentEndTime));
        ImGui.textDisabled("提示：时间通过 Timeline 拖动/拉伸调整");
        
        // ⭐ 更新缓存的时间值（用于检测变化，但不影响显示）
        lastDisplayedStartTime = currentStartTime;
        lastDisplayedEndTime = currentEndTime;
        lastDisplayedDuration = currentDuration;
    }
    
    /**
     * 分区 2：围绕中心（Center / LookAt）
     */
    private boolean renderCenterSection(OrbitPathClip clip) {
        boolean modified = false;
        
        if (ImGui.collapsingHeader(Icons.TARGET + " 围绕中心", ImGuiTreeNodeFlags.DefaultOpen)) {
            // Center
            ImGui.text("Center");
            Vec3d center = clip.getCenter();
            float[] centerArr = {(float) center.x, (float) center.y, (float) center.z};
            if (ImGui.inputFloat3("##center", centerArr)) {
                clip.setCenter(new Vec3d(centerArr[0], centerArr[1], centerArr[2]));
                modified = true;
            }
            ImGui.sameLine();
            boolean isPickingCenter = currentPickMode == PickMode.PICK_CENTER && pickingClip == clip;
            // ⭐ 改进：按钮显示当前状态
            if (ImGui.button(isPickingCenter ? "取消拾取" : "从世界拾取")) {
                if (isPickingCenter) {
                    currentPickMode = PickMode.NONE;
                    pickingClip = null;
                } else {
                    currentPickMode = PickMode.PICK_CENTER;
                    pickingClip = clip;
                }
            }
            if (isPickingCenter) {
                ImGui.sameLine();
                ImGui.textColored(1.0f, 0.8f, 0.0f, 1.0f, Icons.CROSSHAIR + " 点击世界中的方块");
            }
            
            UITheme.spacingSmall();
            
            // Look At
            ImGui.text("Look At");
            Vec3d lookAt = clip.getLookAt();
            Vec3d center2 = clip.getCenter();
            boolean lookAtSameAsCenter = lookAt.equals(center2);
            
            // 单选按钮：Same as Center / Custom Point
            if (ImGui.radioButton("Same as Center", lookAtSameAsCenter)) {
                if (!lookAtSameAsCenter) {
                    clip.setLookAt(center2);
                    modified = true;
                }
            }
            ImGui.sameLine();
            if (ImGui.radioButton("Custom Point", !lookAtSameAsCenter)) {
                if (lookAtSameAsCenter) {
                    // 切换到自定义时，保持当前 lookAt（如果等于 center，则稍微偏移）
                    if (lookAt.equals(center2)) {
                        clip.setLookAt(center2.add(0, 5, 0)); // 默认向上偏移 5 格
                        modified = true;
                    }
                }
            }
            
            if (!lookAtSameAsCenter) {
                float[] lookAtArr = {(float) lookAt.x, (float) lookAt.y, (float) lookAt.z};
                if (ImGui.inputFloat3("##lookAt", lookAtArr)) {
                    clip.setLookAt(new Vec3d(lookAtArr[0], lookAtArr[1], lookAtArr[2]));
                    modified = true;
                }
                ImGui.sameLine();
                boolean isPickingLookAt = currentPickMode == PickMode.PICK_LOOKAT && pickingClip == clip;
                // ⭐ 改进：按钮显示当前状态
                if (ImGui.button(isPickingLookAt ? "取消拾取##lookAt" : "从世界拾取##lookAt")) {
                    if (isPickingLookAt) {
                        currentPickMode = PickMode.NONE;
                        pickingClip = null;
                    } else {
                        currentPickMode = PickMode.PICK_LOOKAT;
                        pickingClip = clip;
                    }
                }
                if (isPickingLookAt) {
                    ImGui.sameLine();
                    ImGui.textColored(1.0f, 0.8f, 0.0f, 1.0f, Icons.CROSSHAIR + " 点击世界中的方块");
                }
            }
        }
        
        return modified;
    }
    
    /**
     * 分区 3：运动形态（Radius, Height, Direction）
     */
    private boolean renderMotionSection(OrbitPathClip clip) {
        boolean modified = false;
        
        if (ImGui.collapsingHeader(Icons.SCALE + " 运动形态", ImGuiTreeNodeFlags.DefaultOpen)) {
            // Radius（模式选择 + Slider）
            ImGui.text("Radius");
            
            // ⭐ 半径模式选择（From Current Position / Custom Radius）
            com.chronoblocks.ui.timeline.model.camera.path.OrbitPathClip.RadiusMode radiusMode = clip.getRadiusMode();
            boolean isFromCurrent = radiusMode == com.chronoblocks.ui.timeline.model.camera.path.OrbitPathClip.RadiusMode.FROM_CURRENT_POSITION;
            
            // ⭐ 关键修复：切换到 From Current 模式时，立即对齐半径
            // ⭐ 允许重复点击来重新对齐（即使已经选中）
            if (ImGui.radioButton("From Current Camera Position", isFromCurrent)) {
                // 从其他模式切换过来
                if (!isFromCurrent) {
                    clip.setRadiusMode(com.chronoblocks.ui.timeline.model.camera.path.OrbitPathClip.RadiusMode.FROM_CURRENT_POSITION);
                    modified = true;
                }
            }
            // ⭐ 无论是否切换模式，只要被点击就重新对齐半径（允许重复点击重新对齐）
            if (ImGui.isItemClicked()) {
                // 如果当前不是 FROM_CURRENT 模式，先切换模式
                if (!isFromCurrent) {
                    clip.setRadiusMode(com.chronoblocks.ui.timeline.model.camera.path.OrbitPathClip.RadiusMode.FROM_CURRENT_POSITION);
                    modified = true;
                }
                // 重新对齐半径
                alignRadiusFromCurrentCamera(clip);
                modified = true;
            }
            ImGui.sameLine();
            if (ImGui.radioButton("Custom Radius", !isFromCurrent)) {
                if (isFromCurrent) {
                    clip.setRadiusMode(com.chronoblocks.ui.timeline.model.camera.path.OrbitPathClip.RadiusMode.ABSOLUTE);
                    modified = true;
                }
            }
            
            // ⭐ 只有在 Custom Radius 模式下才显示滑动条
            if (!isFromCurrent) {
                boolean radiusSliderChanged = ImGui.sliderFloat("##radius", radiusFloat.getData(), 0.1f, 100f, "%.1f blocks");
                boolean isRadiusSliderActive = ImGui.isItemActive();
                
                if (radiusSliderChanged) {
                    clip.setRadius(radiusFloat.get());
                    // ⭐ 性能优化：只在拖动结束时才标记为已修改（触发 markDirty）
                    // 如果滑动条正在被拖动，不标记为 modified，避免频繁触发 markDirty()
                    if (!isRadiusSliderActive) {
                        modified = true;
                    }
                }
                
                // ⭐ 检测拖动结束：从 active 变为 inactive
                if (wasRadiusSliderActive && !isRadiusSliderActive) {
                    modified = true; // 拖动结束，标记为已修改
                }
                wasRadiusSliderActive = isRadiusSliderActive;
                ImGui.sameLine();
                if (ImGui.button("+1##radius")) {
                    float newRadius = radiusFloat.get() + 1;
                    clip.setRadius(newRadius);
                    radiusFloat.set(newRadius);
                    modified = true;
                }
                ImGui.sameLine();
                if (ImGui.button("-1##radius")) {
                    float newRadius = Math.max(0.1f, radiusFloat.get() - 1);
                    clip.setRadius(newRadius);
                    radiusFloat.set(newRadius);
                    modified = true;
                }
            } else {
                // ⭐ From Current Position 模式：显示当前半径值（只读，但可以实时更新）
                // ⭐ 实时计算并显示当前半径（如果可能）
                float displayRadius = getDisplayRadius(clip);

                ImGui.textDisabled(String.format("Radius: %.2f blocks (从当前摄像机位置计算)", displayRadius));
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("半径会实时从当前摄像机位置计算（投影到 XZ 平面）\n点击上面的选项可以重新对齐");
                }
            }
            
            UITheme.spacingSmall();
            
            // Height（Keep Current / Custom）
            ImGui.text("Height");
            com.chronoblocks.ui.timeline.model.camera.path.OrbitPathClip.HeightMode heightMode = clip.getHeightMode();
            boolean isKeepCurrent = heightMode == com.chronoblocks.ui.timeline.model.camera.path.OrbitPathClip.HeightMode.KEEP_CURRENT;
            
            if (ImGui.radioButton("Keep Current Height", isKeepCurrent)) {
                if (!isKeepCurrent) {
                    // 切换到 KEEP_CURRENT 模式
                    clip.setHeightMode(com.chronoblocks.ui.timeline.model.camera.path.OrbitPathClip.HeightMode.KEEP_CURRENT);
                    // 立即缓存当前高度
                    com.chronoblocks.ui.timeline.playback.camera.CameraRuntime runtime = 
                        com.chronoblocks.ui.timeline.playback.camera.CameraRuntime.getInstance();
                    com.chronoblocks.ui.timeline.playback.camera.CameraSample currentSample = runtime.getCurrentSample();
                    if (currentSample != null) {
                        float currentHeight = (float) currentSample.position().y;
                        clip.setCachedHeight(currentHeight);
                    }
                    modified = true;
                }
            }
            ImGui.sameLine();
            if (ImGui.radioButton("Custom Height", !isKeepCurrent)) {
                if (isKeepCurrent) {
                    // 切换到 ABSOLUTE 模式
                    clip.setHeightMode(com.chronoblocks.ui.timeline.model.camera.path.OrbitPathClip.HeightMode.ABSOLUTE);
                    // 如果有缓存的高度，使用它；否则使用当前高度值
                    Float cached = clip.getCachedHeight();
                    if (cached != null) {
                        clip.setHeight(cached);
                        heightFloat.set(cached);
                    }
                    modified = true;
                }
            }
            
            if (!isKeepCurrent) {
                // ABSOLUTE 模式：显示高度输入框（使用 dragFloat 便于拖动调整）
                if (ImGui.dragFloat("##height", heightFloat.getData(), 0.1f, -1000f, 1000f, "%.1f blocks")) {
                    clip.setHeight(heightFloat.get());
                    modified = true;
                }
            } else {
                // KEEP_CURRENT 模式：显示当前缓存的高度（只读）
                Float cached = clip.getCachedHeight();
                if (cached != null) {
                    ImGui.textDisabled("当前高度: " + String.format("%.2f", cached));
                } else {
                    ImGui.textDisabled("将在首次采样时自动获取当前高度");
                }
            }
            
            UITheme.spacingSmall();
            
            // Direction（Clockwise / Counter-clockwise）
            ImGui.text("Direction");
            boolean clockwise = clip.isClockwise();
            if (ImGui.radioButton("Clockwise", clockwise)) {
                if (!clockwise) {
                    clip.setClockwise(true);
                    modified = true;
                }
            }
            ImGui.sameLine();
            if (ImGui.radioButton("Counter-clockwise", !clockwise)) {
                if (clockwise) {
                    clip.setClockwise(false);
                    modified = true;
                }
            }
        }
        
        return modified;
    }

    private static float getDisplayRadius(OrbitPathClip clip) {
        float displayRadius = clip.getRadius();
        try {
            // 尝试从当前摄像机位置实时计算半径（用于显示）
            CameraRuntime runtime =
                CameraRuntime.getInstance();
            CameraSample currentSample = runtime.getCurrentSample();
            if (currentSample != null) {
                Vec3d currentPos = currentSample.position();
                Vec3d c = clip.getCenter();
                double dx = currentPos.x - c.x;
                double dz = currentPos.z - c.z;
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > 1e-3) {
                    displayRadius = (float) dist;
                }
            }
        } catch (Exception e) {
            // 如果获取失败，使用 clip 中的值
        }
        return displayRadius;
    }

    /**
     * 分区 4：角度区间（Start/End Angle，带 Full Orbit 按钮）
     */
    private boolean renderAngleSection(OrbitPathClip clip) {
        boolean modified = false;
        
        if (ImGui.collapsingHeader(Icons.ROTATE + " 角度区间", ImGuiTreeNodeFlags.DefaultOpen)) {
            // ⭐ 快捷按钮：Full Orbit / 90° Sweep / 180° Sweep
            if (ImGui.button("Full Orbit (0° - 360°)")) {
                clip.setStartAngle(0f);
                clip.setEndAngle(360f);
                // ⭐ 确保缓存值同步更新，避免滑动条显示错误
                startAngleFloat.set(0f);
                endAngleFloat.set(360f);
                modified = true;
            }
            ImGui.sameLine();
            if (ImGui.button("90° Sweep")) {
                float currentStart = startAngleFloat.get();
                clip.setStartAngle(currentStart);
                clip.setEndAngle(currentStart + 90f);
                endAngleFloat.set(currentStart + 90f);
                modified = true;
            }
            ImGui.sameLine();
            if (ImGui.button("180° Sweep")) {
                float currentStart = startAngleFloat.get();
                clip.setStartAngle(currentStart);
                clip.setEndAngle(currentStart + 180f);
                endAngleFloat.set(currentStart + 180f);
                modified = true;
            }
            
            UITheme.spacingSmall();
            
            // ⭐ Start Angle 模式选择（From Current Position / Custom Angle）
            ImGui.text("Start Angle");
            com.chronoblocks.ui.timeline.model.camera.path.OrbitPathClip.StartAngleMode startAngleMode = clip.getStartAngleMode();
            boolean isStartAngleFromCurrent = startAngleMode == com.chronoblocks.ui.timeline.model.camera.path.OrbitPathClip.StartAngleMode.FROM_CURRENT_POSITION;
            
            // ⭐ 修复：与其他 radioButton 保持一致，添加状态检查
            // ⭐ 关键修复：切换到 From Current 模式时，立即对齐起始角
            // ⭐ 允许重复点击来重新对齐（即使已经选中）
            if (ImGui.radioButton("From Current Camera Position", isStartAngleFromCurrent)) {
                // 从其他模式切换过来
                if (!isStartAngleFromCurrent) {
                    clip.setStartAngleMode(com.chronoblocks.ui.timeline.model.camera.path.OrbitPathClip.StartAngleMode.FROM_CURRENT_POSITION);
                    modified = true;
                }
            }
            // ⭐ 无论是否切换模式，只要被点击就重新对齐起始角（允许重复点击重新对齐）
            if (ImGui.isItemClicked()) {
                // 如果当前不是 FROM_CURRENT 模式，先切换模式
                if (!isStartAngleFromCurrent) {
                    clip.setStartAngleMode(com.chronoblocks.ui.timeline.model.camera.path.OrbitPathClip.StartAngleMode.FROM_CURRENT_POSITION);
                    modified = true;
                }
                // 重新对齐起始角
                alignStartAngleFromCurrentCamera(clip);
                modified = true;
            }
            ImGui.sameLine();
            if (ImGui.radioButton("Custom Angle", !isStartAngleFromCurrent)) {
                if (isStartAngleFromCurrent) {
                    clip.setStartAngleMode(com.chronoblocks.ui.timeline.model.camera.path.OrbitPathClip.StartAngleMode.CUSTOM);
                    modified = true;
                }
            }
            
            // ⭐ 在 FROM_CURRENT_POSITION 模式下，显示当前计算的角度（只读）
            if (isStartAngleFromCurrent) {
                // ⭐ 实时获取当前摄像机位置计算的角度
                float currentAngle = clip.getStartAngle();
                ImGui.text(String.format("当前起始角: %.1f° (从当前摄像机位置计算)", currentAngle));
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("起始角会实时从当前摄像机位置计算（平面角度），实现无缝衔接");
                }
                // ⭐ 同步缓存值，以便切换回 CUSTOM 模式时使用
                startAngleFloat.set(currentAngle);
            } else {
                // ⭐ 只有在 Custom Angle 模式下才显示滑动条
                // ⭐ 同步缓存值：确保滑动条显示的值与 clip 中的值一致
                if (Math.abs(startAngleFloat.get() - clip.getStartAngle()) > 0.01f) {
                    startAngleFloat.set(clip.getStartAngle());
                }
                
                boolean startAngleSliderChanged = ImGui.sliderFloat("Start Angle (度)", startAngleFloat.getData(), 0f, 360f, "%.1f°");
                boolean isStartAngleSliderActive = ImGui.isItemActive();
                
                if (startAngleSliderChanged) {
                    clip.setStartAngle(startAngleFloat.get());
                    // ⭐ 性能优化：只在拖动结束时才标记为已修改（触发 markDirty）
                    if (!isStartAngleSliderActive) {
                        modified = true;
                    }
                }
                
                // ⭐ 检测拖动结束：从 active 变为 inactive
                if (wasStartAngleSliderActive && !isStartAngleSliderActive) {
                    modified = true; // 拖动结束，标记为已修改
                }
                wasStartAngleSliderActive = isStartAngleSliderActive;
            }
            
            UITheme.spacingSmall();
            
            // End Angle
            // ⭐ 同步缓存值：确保滑动条显示的值与 clip 中的值一致
            if (Math.abs(endAngleFloat.get() - clip.getEndAngle()) > 0.01f) {
                endAngleFloat.set(clip.getEndAngle());
            }
            
            boolean endAngleSliderChanged = ImGui.sliderFloat("End Angle (度)", endAngleFloat.getData(), 0f, 360f, "%.1f°");
            boolean isEndAngleSliderActive = ImGui.isItemActive();
            
            if (endAngleSliderChanged) {
                clip.setEndAngle(endAngleFloat.get());
                // ⭐ 性能优化：只在拖动结束时才标记为已修改（触发 markDirty）
                if (!isEndAngleSliderActive) {
                    modified = true;
                }
            }
            
            // ⭐ 检测拖动结束：从 active 变为 inactive
            if (wasEndAngleSliderActive && !isEndAngleSliderActive) {
                modified = true; // 拖动结束，标记为已修改
            }
            wasEndAngleSliderActive = isEndAngleSliderActive;
            
            // 角度范围显示
            float angleRange = endAngleFloat.get() - startAngleFloat.get();
            if (angleRange < 0) {
                angleRange += 360f;
            }
            ImGui.textDisabled("角度范围: " + String.format("%.1f", angleRange) + "°");
            
            // ⭐ 注意：起始角模式选择已在上面实现（From Current Camera Position / Custom Angle）
            // 移除了旧的 checkbox，现在起始角、半径、高度都使用统一的模式选择 UI
        }
        
        return modified;
    }
    
    
    /**
     * 处理世界拾取（在 UI 渲染后检查鼠标点击）
     * 
     * <p>注意：这个方法在 renderProperties 中调用，但实际的鼠标点击检测
     * 应该在外部系统（如 MouseMixin 或专门的拾取管理器）中处理。
     * 这里只做状态检查和提示显示。
     */
    private void handleWorldPick(OrbitPathClip clip) {
        if (currentPickMode == PickMode.NONE || pickingClip != clip) {
            return;
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null) {
            return;
        }
        
        // 检查 ImGui 是否捕获了鼠标（如果 UI 正在使用鼠标，不处理）
        if (imgui.ImGui.getIO().getWantCaptureMouse()) {
            return;
        }
        
        // ⭐ 使用 GLFW 直接检查鼠标左键是否按下（edge trigger）
        long windowHandle = client.getWindow().getHandle();
        int leftButtonState = org.lwjgl.glfw.GLFW.glfwGetMouseButton(windowHandle, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
        boolean currentLeftMouseDown = (leftButtonState == org.lwjgl.glfw.GLFW.GLFW_PRESS);
        
        // ⭐ Edge trigger：只在"刚按下"时处理（从 false 变为 true）
        boolean justPressed = currentLeftMouseDown && !lastLeftMouseDown;
        lastLeftMouseDown = currentLeftMouseDown;
        
        if (justPressed) {
            // 获取射线命中结果
            BlockHitResult hit = null;
            try {
                // 优先使用 ChronoBlocksInputSystem 的缓存射线
                hit = com.chronoblocks.client.input.ChronoBlocksInputSystem.getCurrentRay();
            } catch (Throwable ignored) {
                // 如果获取失败，使用 crosshairTarget
                if (client.crosshairTarget instanceof BlockHitResult) {
                    hit = (BlockHitResult) client.crosshairTarget;
                }
            }
            
            if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                BlockPos blockPos = hit.getBlockPos();
                // ⭐ 计算方块中心：BlockPos 转 Vec3d 中心坐标
                Vec3d blockCenter = new Vec3d(
                    blockPos.getX() + 0.5,
                    blockPos.getY() + 0.5,
                    blockPos.getZ() + 0.5
                );
                
                // 根据拾取模式设置对应的值
                if (currentPickMode == PickMode.PICK_CENTER) {
                    clip.setCenter(blockCenter);
                    LOGGER.info("[OrbitPathClip] 从世界拾取中心点: {} -> {}", blockPos, blockCenter);
                } else if (currentPickMode == PickMode.PICK_LOOKAT) {
                    clip.setLookAt(blockCenter);
                    LOGGER.info("[OrbitPathClip] 从世界拾取注视点: {} -> {}", blockPos, blockCenter);
                }
                
                // 拾取完成后，取消拾取模式
                currentPickMode = PickMode.NONE;
                pickingClip = null;
            }
        }
    }
    
    /**
     * 同步缓存值（从 clip 读取最新值）
     */
    private void syncCacheFromClip(OrbitPathClip clip) {
        if (radiusFloat.get() != clip.getRadius()) {
            radiusFloat.set(clip.getRadius());
        }
        // ⭐ 高度：只在 ABSOLUTE 模式下同步
        if (clip.getHeightMode() == com.chronoblocks.ui.timeline.model.camera.path.OrbitPathClip.HeightMode.ABSOLUTE) {
            if (heightFloat.get() != clip.getHeight()) {
                heightFloat.set(clip.getHeight());
            }
        }
        if (startAngleFloat.get() != clip.getStartAngle()) {
            startAngleFloat.set(clip.getStartAngle());
        }
        if (endAngleFloat.get() != clip.getEndAngle()) {
            endAngleFloat.set(clip.getEndAngle());
        }
        if (!nameBuffer.get().equals(clip.getName())) {
            nameBuffer.set(clip.getName());
        }
    }
    
    /**
     * ⭐ 从当前摄像机状态立即对齐起始角（UI 主动调用）
     * 
     * <p>当用户在属性面板中切换到 "From Current Camera Position" 模式时，
     * 立即使用当前摄像机状态重新计算起始角，而不需要等待 onEnter()。
     * 
     * @param clip 要对齐的 OrbitPathClip
     */
    private void alignStartAngleFromCurrentCamera(OrbitPathClip clip) {
        try {
            // 从 CameraRuntime 获取当前摄像机状态
            CameraRuntime runtime = CameraRuntime.getInstance();
            CameraSample currentSample = runtime.getCurrentSample();
            
            if (currentSample == null) {
                LOGGER.warn("[OrbitPathClipPropertyAdapter] 无法获取当前摄像机状态，无法对齐起始角");
                return;
            }

            Vec3d currentPos = currentSample.position();

            // 从 yaw/pitch 计算 lookAt（与 CameraTrackSampler 中的逻辑一致）
            double yawRad = Math.toRadians(currentSample.yaw());
            double pitchRad = Math.toRadians(currentSample.pitch());
            double cosPitch = Math.cos(pitchRad);
            Vec3d currentLookAt = currentPos.add(
                -Math.sin(yawRad) * cosPitch * 10.0,  // 向前看 10 个单位
                -Math.sin(pitchRad) * 10.0,
                Math.cos(yawRad) * cosPitch * 10.0
            );

            // 构造 CameraPathEnterContext
            CameraPathEnterContext ctx = new CameraPathEnterContext(currentPos, currentLookAt);

            // 调用对齐方法
            clip.alignStartAngleFromCamera(ctx);
            
            // ⭐ 同步更新 UI 缓存值，确保滑动条显示正确的值
            startAngleFloat.set(clip.getStartAngle());
            endAngleFloat.set(clip.getEndAngle());
            
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[OrbitPathClipPropertyAdapter] 已从当前摄像机状态对齐起始角: {}°", clip.getStartAngle());
            }
        } catch (Exception e) {
            LOGGER.error("[OrbitPathClipPropertyAdapter] 对齐起始角时发生错误", e);
        }
    }

    /**
     * ⭐ 从当前摄像机状态立即对齐半径（UI 主动调用）
     */
    private void alignRadiusFromCurrentCamera(OrbitPathClip clip) {
        try {
            // 从 CameraRuntime 获取当前摄像机状态
            CameraRuntime runtime = CameraRuntime.getInstance();
            CameraSample currentSample = runtime.getCurrentSample();
            
            if (currentSample == null) {
                LOGGER.warn("[OrbitPathClipPropertyAdapter] 无法获取当前摄像机状态，无法对齐半径");
                return;
            }
            
            Vec3d currentPos = currentSample.position();
            
            // 从 yaw/pitch 计算 lookAt（与 CameraTrackSampler 中的逻辑一致）
            double yawRad = Math.toRadians(currentSample.yaw());
            double pitchRad = Math.toRadians(currentSample.pitch());
            double cosPitch = Math.cos(pitchRad);
            Vec3d currentLookAt = currentPos.add(
                -Math.sin(yawRad) * cosPitch * 10.0,  // 向前看 10 个单位
                -Math.sin(pitchRad) * 10.0,
                Math.cos(yawRad) * cosPitch * 10.0
            );
            
            // 构造 CameraPathEnterContext
            CameraPathEnterContext ctx = new CameraPathEnterContext(currentPos, currentLookAt);
            
            // 调用对齐方法
            clip.alignRadiusFromCamera(ctx);
            
            // ⭐ 同步更新 UI 缓存值，确保滑动条显示正确的值
            radiusFloat.set(clip.getRadius());
            
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[OrbitPathClipPropertyAdapter] 已从当前摄像机状态对齐半径: {} blocks", clip.getRadius());
            }
        } catch (Exception e) {
            LOGGER.error("[OrbitPathClipPropertyAdapter] 对齐半径时发生错误", e);
        }
    }
    
    /**
     * ⭐ 检查播放头是否在当前 clip 的时间范围内
     * 
     * @param clip 要检查的 OrbitPathClip
     * @return 如果播放头在 clip 的时间范围内返回 true，否则返回 false
     */
    private boolean isPlayheadOnClip(OrbitPathClip clip) {
        TimelineModel timelineModel = ContainerTimelineService.getInstance().getTimelineModel();
        if (timelineModel == null) {
            return false;
        }
        
        float currentTime = timelineModel.getCurrentTime();
        float clipStartTime = clip.getStartTime();
        float clipEndTime = clip.getEndTime();
        
        // 检查播放头是否在 clip 的时间范围内（包含边界）
        return currentTime >= clipStartTime && currentTime <= clipEndTime;
    }
    
    /**
     * ⭐ 实时预览：当参数被修改且播放头在 clip 上时，立即更新摄像机
     * 
     * @param clip 要预览的 OrbitPathClip
     */
    private void updateCameraPreview(OrbitPathClip clip) {
        try {
            TimelineModel timelineModel = ContainerTimelineService.getInstance().getTimelineModel();
            if (timelineModel == null) {
                return;
            }
            
            TimelineCameraController controller = TimelineCameraController.getInstance();

            // ⭐ 使用 TimelineCameraController 的 previewAt() 来实时预览
            // previewAt() 会自动处理采样和应用，适用于所有状态（静止/播放/scrubbing）
            float currentTime = timelineModel.getCurrentTime();
            controller.previewAt(currentTime);
            
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[OrbitPathClipPropertyAdapter] 实时预览更新摄像机 @ {}s (clip: {} - {})", 
                    currentTime, clip.getStartTime(), clip.getEndTime());
            }
        } catch (Exception e) {
            LOGGER.error("[OrbitPathClipPropertyAdapter] 实时预览更新摄像机时发生错误", e);
        }
    }
}
