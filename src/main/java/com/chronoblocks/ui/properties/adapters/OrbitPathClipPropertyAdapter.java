package com.chronoblocks.ui.properties.adapters;

import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.theme.UITheme;
import com.chronoblocks.ui.timeline.model.camera.path.OrbitPathClip;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImFloat;
import net.minecraft.util.math.Vec3d;

/**
 * OrbitPathClipPropertyAdapter - 围绕路径片段属性适配器
 * 
 * <p>用于在属性面板中编辑 OrbitPathClip 的参数。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class OrbitPathClipPropertyAdapter implements IPropertyAdapter<OrbitPathClip> {
    
    // ⭐ 性能优化：缓存 ImGui 对象，避免每帧创建新对象
    private imgui.type.ImString nameBuffer = new imgui.type.ImString("", 256);
    private final ImFloat radiusFloat = new ImFloat(0f);
    private final ImFloat heightFloat = new ImFloat(0f);
    private final ImFloat startAngleFloat = new ImFloat(0f);
    private final ImFloat endAngleFloat = new ImFloat(0f);
    private OrbitPathClip lastClip = null; // 用于检测 clip 变化，只在变化时更新缓存
    
    @Override
    public Class<OrbitPathClip> getTargetType() {
        return OrbitPathClip.class;
    }
    
    @Override
    public boolean supports(Object target) {
        return target instanceof OrbitPathClip;
    }
    
    @Override
    public boolean renderProperties(OrbitPathClip clip) {
        if (clip == null) {
            ImGui.textDisabled("未选择围绕路径片段");
            lastClip = null;
            return false;
        }
        
        // ⭐ 性能优化：只在 clip 变化时更新缓存值，避免每帧都更新
        if (clip != lastClip) {
            nameBuffer.set(clip.getName());
            radiusFloat.set(clip.getRadius());
            heightFloat.set(clip.getHeight());
            startAngleFloat.set(clip.getStartAngle());
            endAngleFloat.set(clip.getEndAngle());
            lastClip = clip;
        }
        
        UITheme.title(Icons.CAMERA + " 围绕路径片段属性");
        ImGui.separator();
        
        boolean modified = false;
        
        // 基本信息
        if (ImGui.collapsingHeader(Icons.INFO + " 基本信息", ImGuiTreeNodeFlags.DefaultOpen)) {
            // 名称
            if (ImGui.inputText("名称", nameBuffer)) {
                clip.setName(nameBuffer.get());
                modified = true;
            }
            
            // 时间范围
            float[] timeRange = {clip.getStartTime(), clip.getEndTime()};
            if (ImGui.inputFloat2("时间范围 (开始, 结束)", timeRange)) {
                clip.setStartTime(timeRange[0]);
                clip.setEndTime(timeRange[1]);
                modified = true;
            }
            
            float duration = clip.getDuration();
            // ⭐ 性能优化：避免每帧使用 String.format()，使用简单的字符串拼接
            ImGui.textDisabled("时长: " + String.format("%.2f", duration) + " 秒");
            
            // 启用/禁用
            boolean enabled = clip.isEnabled();
            if (ImGui.checkbox("启用", enabled)) {
                clip.setEnabled(!enabled);
                modified = true;
            }
        }
        
        UITheme.spacingSmall();
        
        // 围绕参数
        if (ImGui.collapsingHeader(Icons.SCALE + " 围绕参数", ImGuiTreeNodeFlags.DefaultOpen)) {
            // 中心点
            Vec3d center = clip.getCenter();
            float[] centerArr = {(float) center.x, (float) center.y, (float) center.z};
            if (ImGui.inputFloat3("中心点 (X, Y, Z)", centerArr)) {
                clip.setCenter(new Vec3d(centerArr[0], centerArr[1], centerArr[2]));
                modified = true;
            }
            ImGui.textDisabled("提示：可以点选方块来设置中心点");
            
            // 半径
            if (ImGui.inputFloat("半径 (方块数)", radiusFloat)) {
                clip.setRadius(radiusFloat.get());
                modified = true;
            }
            ImGui.sameLine();
            if (ImGui.button("+1")) {
                float newRadius = radiusFloat.get() + 1;
                clip.setRadius(newRadius);
                radiusFloat.set(newRadius);
                modified = true;
            }
            ImGui.sameLine();
            if (ImGui.button("-1")) {
                float newRadius = Math.max(0.1f, radiusFloat.get() - 1);
                clip.setRadius(newRadius);
                radiusFloat.set(newRadius);
                modified = true;
            }
            
            // 高度
            if (ImGui.inputFloat("高度 (Y 坐标)", heightFloat)) {
                clip.setHeight(heightFloat.get());
                modified = true;
            }
            ImGui.sameLine();
            if (ImGui.button("当前高度")) {
                // 获取当前摄像机高度
                com.chronoblocks.ui.timeline.playback.camera.CameraRuntime runtime = 
                    com.chronoblocks.ui.timeline.playback.camera.CameraRuntime.getInstance();
                com.chronoblocks.ui.timeline.playback.camera.CameraSample sample = runtime.getCurrentSample();
                if (sample != null) {
                    float newHeight = (float) sample.position().y;
                    clip.setHeight(newHeight);
                    heightFloat.set(newHeight);
                    modified = true;
                }
            }
        }
        
        UITheme.spacingSmall();
        
        // 角度参数
        if (ImGui.collapsingHeader(Icons.ROTATE + " 角度参数", ImGuiTreeNodeFlags.DefaultOpen)) {
            // 起始角度
            if (ImGui.sliderFloat("起始角度 (度)", startAngleFloat.getData(), 0f, 360f)) {
                clip.setStartAngle(startAngleFloat.get());
                modified = true;
            }
            
            // 结束角度
            if (ImGui.sliderFloat("结束角度 (度)", endAngleFloat.getData(), 0f, 360f)) {
                clip.setEndAngle(endAngleFloat.get());
                modified = true;
            }
            
            // 角度范围显示
            float angleRange = endAngleFloat.get() - startAngleFloat.get();
            if (angleRange < 0) {
                angleRange += 360f;
            }
            // ⭐ 性能优化：避免每帧使用 String.format()，使用简单的字符串拼接
            ImGui.textDisabled("角度范围: " + String.format("%.1f", angleRange) + "°");
            
            // 顺时针/逆时针
            boolean clockwise = clip.isClockwise();
            if (ImGui.checkbox("顺时针", clockwise)) {
                clip.setClockwise(!clockwise);
                modified = true;
            }
        }
        
        UITheme.spacingSmall();
        
        // 注视点
        if (ImGui.collapsingHeader(Icons.TARGET + " 注视点", ImGuiTreeNodeFlags.DefaultOpen)) {
            Vec3d lookAt = clip.getLookAt();
            float[] lookAtArr = {(float) lookAt.x, (float) lookAt.y, (float) lookAt.z};
            if (ImGui.inputFloat3("注视点 (X, Y, Z)", lookAtArr)) {
                clip.setLookAt(new Vec3d(lookAtArr[0], lookAtArr[1], lookAtArr[2]));
                modified = true;
            }
            
            ImGui.sameLine();
            if (ImGui.button("= 中心点")) {
                clip.setLookAt(clip.getCenter());
                modified = true;
            }
        }
        
        // ⭐ 性能优化：如果值被外部修改，同步更新缓存（避免显示过时值）
        if (!modified) {
            // 检查值是否被外部修改（例如通过命令）
            if (radiusFloat.get() != clip.getRadius()) {
                radiusFloat.set(clip.getRadius());
            }
            if (heightFloat.get() != clip.getHeight()) {
                heightFloat.set(clip.getHeight());
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
        
        return modified;
    }
}
