package com.chronoblocks.ui.properties.adapters;

import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.theme.UITheme;
import com.chronoblocks.ui.timeline.model.camera.CameraKeyframe;
import com.chronoblocks.ui.theme.Icons;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import org.joml.Vector3f;

/**
 * 摄像机关键帧属性适配器
 * 
 * <p>为时间线上的摄像机关键帧提供属性编辑界面。
 * 
 * <p>支持的属性：
 * <ul>
 *   <li>位置和旋转（XYZ位置、偏航/俯仰/翻滚）</li>
 *   <li>投影参数（FOV、近/远裁剪面）</li>
 *   <li>镜头特效（景深、焦距、光圈）</li>
 * </ul>
 * 
 * <p>注意：此适配器使用反射或直接访问 CameraKeyframe 的属性。
 * 未来可以与 CameraPropertyAdapter 共享代码。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class CameraKeyframePropertyAdapter implements IPropertyAdapter<CameraKeyframe> {
    
    @Override
    public boolean renderProperties(CameraKeyframe keyframe) {
        if (keyframe == null) {
            UITheme.label("摄像机关键帧为 null", UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }
        
        boolean modified = false;
        
        // 标题
        UITheme.title(Icons.CLIPBOARD + " 摄像机关键帧");
        
        // ========== 基础信息 ==========
        if (ImGui.collapsingHeader("📋 基础信息", ImGuiTreeNodeFlags.DefaultOpen)) {
            float[] time = {keyframe.getTime()};
            if (ImGui.dragFloat("时间", time, 0.05f, 0.0f, 10_000f, "%.2fs")) {
                keyframe.setTime(time[0]);
                modified = true;
            }
            
            UITheme.spacingSmall();
        }
        
        // ========== 位置与旋转 ==========
        if (ImGui.collapsingHeader("📍 位置与旋转", ImGuiTreeNodeFlags.DefaultOpen)) {
            Vector3f pos = keyframe.getPosition();
            float[] position = {pos.x, pos.y, pos.z};
            if (ImGui.dragFloat3("位置 (XYZ)", position, 0.1f)) {
                keyframe.setPosition(new Vector3f(position[0], position[1], position[2]));
                modified = true;
            }
            
            UITheme.spacingSmall();
            
            Vector3f rot = keyframe.getRotation();
            float[] rotation = {rot.x, rot.y, rot.z};
            if (ImGui.dragFloat3("旋转 (偏航/俯仰/翻滚)", rotation, 1.0f, -180f, 180f, "%.1f°")) {
                keyframe.setRotation(new Vector3f(rotation[0], rotation[1], rotation[2]));
                modified = true;
            }
            
            UITheme.spacingSmall();
        }
        
        // ========== 投影参数 ==========
        if (ImGui.collapsingHeader("🎥 投影参数", ImGuiTreeNodeFlags.DefaultOpen)) {
            float[] fov = {keyframe.getFov()};
            if (ImGui.dragFloat("视野 (FOV)", fov, 0.5f, 10.0f, 150.0f, "%.1f°")) {
                keyframe.setFov(fov[0]);
                modified = true;
            }
            
            UITheme.spacingSmall();
            
            float[] nearPlane = {keyframe.getNearPlane()};
            if (ImGui.dragFloat("近裁剪面", nearPlane, 0.01f, 0.01f, 100f, "%.2f")) {
                keyframe.setNearPlane(nearPlane[0]);
                modified = true;
            }
            
            float[] farPlane = {keyframe.getFarPlane()};
            if (ImGui.dragFloat("远裁剪面", farPlane, 1.0f, 1.0f, 10000f, "%.1f")) {
                keyframe.setFarPlane(farPlane[0]);
                modified = true;
            }
            
            UITheme.spacingSmall();
        }
        
        // ========== 镜头特效 ==========
        if (ImGui.collapsingHeader("📷 镜头特效")) {
            ImBoolean dof = new ImBoolean(keyframe.isDepthOfFieldEnabled());
            if (ImGui.checkbox("启用景深", dof)) {
                keyframe.setDepthOfFieldEnabled(dof.get());
                modified = true;
            }
            
            if (dof.get()) {
                UITheme.spacingSmall();
                
                float[] focus = {keyframe.getFocusDistance()};
                if (ImGui.dragFloat("焦距", focus, 0.5f, 0.1f, 1000.0f, "%.1f")) {
                    keyframe.setFocusDistance(focus[0]);
                    modified = true;
                }
                
                float[] aperture = {keyframe.getAperture()};
                if (ImGui.dragFloat("光圈 (f-stop)", aperture, 0.1f, 1.4f, 22.0f, "%.1f")) {
                    keyframe.setAperture(aperture[0]);
                    modified = true;
                }
            }
        }
        
        return modified;
    }
    
    @Override
    public Class<CameraKeyframe> getTargetType() {
        return CameraKeyframe.class;
    }
}

