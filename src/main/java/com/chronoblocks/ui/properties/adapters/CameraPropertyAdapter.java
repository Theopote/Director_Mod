package com.chronoblocks.ui.properties.adapters;

import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.properties.PropertyAdapterRegistry;
import com.chronoblocks.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImString;
import imgui.type.ImBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 相机属性适配器 v4.9 - ChronoBlocks Properties System
 * 
 * <p>为 ChronoBlocks 相机对象提供专用属性编辑器。
 * 
 * <h3>相机（Camera）</h3>
 * <p>Camera 控制场景的视角和镜头参数，包括：
 * <ul>
 *   <li>位置和旋转 - 相机在世界中的位姿</li>
 *   <li>投影参数 - FOV、近裁剪面、远裁剪面</li>
 *   <li>镜头特效 - 景深、焦距、光圈</li>
 *   <li>动画控制 - 相机路径、关键帧、缓动</li>
 * </ul>
 * 
 * <h3>支持的属性</h3>
 * <ul>
 *   <li>📍 位置与旋转（XYZ位置、偏航/俯仰/翻滚）</li>
 *   <li>🎥 投影参数（FOV、近/远裁剪面、纵横比）</li>
 *   <li>📷 镜头特效（景深、焦距、光圈、散景）</li>
 *   <li>🎬 动画控制（持续时间、循环、缓动曲线）</li>
 *   <li>⚡ 快捷操作（重置、复制、对齐到玩家）</li>
 * </ul>
 * 
 * <h3>UI 特性</h3>
 * <ul>
 *   <li>折叠式分组 - 清晰的属性分类</li>
 *   <li>Vector3f 集成 - 使用 Vector3fPropertyAdapter</li>
 *   <li>实时预览 - 当前值显示</li>
 *   <li>智能提示 - Tooltip 说明</li>
 *   <li>快捷预设 - 常用 FOV 值</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 4.9
 * @since 2025-10-16
 */
public class CameraPropertyAdapter implements IPropertyAdapter<Object> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CameraPropertyAdapter.class);
    
    // FOV 预设
    private static final float[] FOV_PRESETS = {60.0f, 70.0f, 90.0f, 110.0f};
    private static final String[] FOV_PRESET_NAMES = {"60° 正常", "70° 标准", "90° 广角", "110° 超广角"};

    @Override
    public boolean renderProperties(Object camera) {
        if (camera == null) {
            UITheme.spacingLarge();
            UITheme.label("相机对象为 null", UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }
        
        final boolean[] modifiedRef = {false};
        
        // 标题
        UITheme.title("🎥 相机属性编辑器");
        
        try {
            // ========== 位置与旋转 ==========
            if (ImGui.collapsingHeader("📍 位置与旋转", ImGuiTreeNodeFlags.DefaultOpen)) {
                // 位置（使用 Vector3fPropertyAdapter）
                Object position = getObjectProperty(camera, "position", "getPosition");
                if (position != null) {
                    var posAdapter = PropertyAdapterRegistry.getAdapterFor(position);
                    if (posAdapter != null) {
                        UITheme.label("相机位置", UITheme.DarkTheme.TEXT);
                        if (posAdapter.renderProperties(position)) {
                            modifiedRef[0] = true;
                        }
                    }
                }
                
                UITheme.spacingSmall();
                
                // 旋转（使用 Vector3fPropertyAdapter）
                Object rotation = getObjectProperty(camera, "rotation", "getRotation");
                if (rotation != null) {
                    var rotAdapter = PropertyAdapterRegistry.getAdapterFor(rotation);
                    if (rotAdapter != null) {
                        UITheme.label("相机旋转 (偏航/俯仰/翻滚)", UITheme.DarkTheme.TEXT);
                        if (rotAdapter.renderProperties(rotation)) {
                            modifiedRef[0] = true;
                        }
                    }
                }
            }
            
            // ========== 投影参数 ==========
            if (ImGui.collapsingHeader("🎥 投影参数", ImGuiTreeNodeFlags.DefaultOpen)) {
                // 视野（FOV）
                float currentFOV = getFloatProperty(camera, "fov", "getFov", 70.0f);
                float[] fov = {currentFOV};
                if (ImGui.dragFloat("视野 (FOV)", fov, 0.5f, 10.0f, 150.0f, "%.1f°")) {
                    if (setFloatProperty(camera, "fov", "setFov", fov[0])) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[CameraPropertyAdapter] FOV已更新: {}", fov[0]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("相机的视野角度（越大视野越广）");
                }
                
                // FOV 快捷预设
                UITheme.spacingSmall();
                UITheme.label("快速预设:", UITheme.DarkTheme.TEXT);
                for (int i = 0; i < FOV_PRESETS.length; i++) {
                    ImGui.pushID("fov_" + i);
                    if (ImGui.button(FOV_PRESET_NAMES[i], 90, UITheme.Spacing.BUTTON_HEIGHT)) {
                        if (setFloatProperty(camera, "fov", "setFov", FOV_PRESETS[i])) {
                            modifiedRef[0] = true;
                            LOGGER.debug("[CameraPropertyAdapter] 应用FOV预设: {}", FOV_PRESETS[i]);
                        }
                    }
                    if ((i + 1) % 2 != 0 && i < FOV_PRESETS.length - 1) {
                        ImGui.sameLine();
                    }
                    ImGui.popID();
                }
                
                UITheme.spacingSmall();
                
                // 近裁剪面
                float currentNear = getFloatProperty(camera, "nearPlane", "getNearPlane", 0.1f);
                float[] nearPlane = {currentNear};
                if (ImGui.dragFloat("近裁剪面", nearPlane, 0.01f, 0.01f, 10.0f, "%.2f")) {
                    if (setFloatProperty(camera, "nearPlane", "setNearPlane", nearPlane[0])) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[CameraPropertyAdapter] 近裁剪面已更新: {}", nearPlane[0]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("相机能看到的最近距离");
                }
                
                // 远裁剪面
                float currentFar = getFloatProperty(camera, "farPlane", "getFarPlane", 1000.0f);
                float[] farPlane = {currentFar};
                if (ImGui.dragFloat("远裁剪面", farPlane, 10.0f, 10.0f, 10000.0f, "%.0f")) {
                    if (setFloatProperty(camera, "farPlane", "setFarPlane", farPlane[0])) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[CameraPropertyAdapter] 远裁剪面已更新: {}", farPlane[0]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("相机能看到的最远距离");
                }
            }
            
            // ========== 镜头特效 ==========
            if (ImGui.collapsingHeader("📷 镜头特效")) {
                // 景深开关
                boolean currentDOF = getBooleanProperty(camera, "depthOfFieldEnabled", "isDepthOfFieldEnabled", false);
                ImBoolean enableDOF = new ImBoolean(currentDOF);
                if (ImGui.checkbox("启用景深 (Depth of Field)", enableDOF)) {
                    if (setBooleanProperty(camera, "depthOfFieldEnabled", "setDepthOfFieldEnabled", enableDOF.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[CameraPropertyAdapter] 景深已更新: {}", enableDOF.get());
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("模拟真实相机的景深效果");
                }
                
                // 焦距
                float currentFocus = getFloatProperty(camera, "focusDistance", "getFocusDistance", 10.0f);
                float[] focusDistance = {currentFocus};
                if (ImGui.dragFloat("焦距", focusDistance, 0.5f, 0.1f, 1000.0f, "%.1f")) {
                    if (setFloatProperty(camera, "focusDistance", "setFocusDistance", focusDistance[0])) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[CameraPropertyAdapter] 焦距已更新: {}", focusDistance[0]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("焦点距离（此距离的物体最清晰）");
                }
                
                // 光圈
                float currentAperture = getFloatProperty(camera, "aperture", "getAperture", 2.8f);
                float[] aperture = {currentAperture};
                if (ImGui.dragFloat("光圈 (f-stop)", aperture, 0.1f, 1.4f, 22.0f, "f/%.1f")) {
                    if (setFloatProperty(camera, "aperture", "setAperture", aperture[0])) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[CameraPropertyAdapter] 光圈已更新: {}", aperture[0]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("光圈大小（越小景深效果越强，如 f/1.4 = 浅景深）");
                }
                
                // 散景质量
                int currentBokeh = getIntProperty(camera);
                int[] bokehQuality = {currentBokeh};
                if (ImGui.dragInt("散景质量", bokehQuality, 1.0f, 0, 5)) {
                    if (setIntProperty(camera, bokehQuality[0])) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[CameraPropertyAdapter] 散景质量已更新: {}", bokehQuality[0]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("背景模糊的质量（0 = 关闭，5 = 最高）");
                }
            }
            
            // ========== 动画控制 ==========
            if (ImGui.collapsingHeader("🎬 动画控制")) {
                // 动画名称
                String currentName = getStringProperty(camera);
                ImString nameBuffer = new ImString(currentName, 256);
                if (ImGui.inputText("动画名称", nameBuffer, ImGuiInputTextFlags.EnterReturnsTrue)) {
                    if (setStringProperty(camera, nameBuffer.get())) {
                        modifiedRef[0] = true;
                    }
                }
                
                // 持续时间
                float currentDuration = getFloatProperty(camera, "animationDuration", "getAnimationDuration", 5.0f);
                float[] duration = {currentDuration};
                if (ImGui.dragFloat("持续时间 (秒)", duration, 0.1f, 0.1f, 300.0f)) {
                    if (setFloatProperty(camera, "animationDuration", "setAnimationDuration", duration[0])) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[CameraPropertyAdapter] 动画持续时间已更新: {}", duration[0]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("相机动画的时长");
                }
                
                // 循环播放
                boolean currentLoop = getBooleanProperty(camera, "loop", "isLoop", false);
                ImBoolean loop = new ImBoolean(currentLoop);
                if (ImGui.checkbox("循环播放", loop)) {
                    if (setBooleanProperty(camera, "loop", "setLoop", loop.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[CameraPropertyAdapter] 循环播放已更新: {}", loop.get());
                    }
                }
                
                // 平滑插值
                boolean currentSmooth = getBooleanProperty(camera, "smoothInterpolation", "isSmoothInterpolation", true);
                ImBoolean smooth = new ImBoolean(currentSmooth);
                if (ImGui.checkbox("平滑插值", smooth)) {
                    if (setBooleanProperty(camera, "smoothInterpolation", "setSmoothInterpolation", smooth.get())) {
                        modifiedRef[0] = true;
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("使用平滑曲线插值相机路径");
                }
            }
            
            UITheme.separator();
            
            // ========== 快捷操作 ==========
            UITheme.beginGroup("快捷操作");
            
            if (ImGui.button("🔄 重置相机", 120, UITheme.Spacing.BUTTON_HEIGHT)) {
                if (callMethod(camera, "reset") || callMethod(camera, "resetToDefault")) {
                    modifiedRef[0] = true;
                    LOGGER.info("[CameraPropertyAdapter] 相机已重置");
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("将相机重置为默认位置和参数");
            }
            
            ImGui.sameLine();
            
            if (ImGui.button("📋 复制相机", 120, UITheme.Spacing.BUTTON_HEIGHT)) {
                LOGGER.info("[CameraPropertyAdapter] 复制相机设置");
                // TODO: 复制到剪贴板
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("复制当前相机设置");
            }
            
            ImGui.sameLine();
            
            if (ImGui.button("👤 对齐到玩家", 120, UITheme.Spacing.BUTTON_HEIGHT)) {
                if (callMethod(camera, "alignToPlayer")) {
                    modifiedRef[0] = true;
                    LOGGER.info("[CameraPropertyAdapter] 相机已对齐到玩家");
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("将相机移动到玩家当前位置");
            }
            
            UITheme.endGroup();
            
        } catch (Exception e) {
            LOGGER.error("[CameraPropertyAdapter] 渲染相机属性时出错", e);
            UITheme.label("渲染出错: " + e.getMessage(), UITheme.DarkTheme.TEXT_DISABLED);
        }
        
        return modifiedRef[0];
    }
    
    // ========== 反射辅助方法 ==========
    
    private String getStringProperty(Object obj) {
        try {
            var method = obj.getClass().getMethod("getAnimationName");
            Object result = method.invoke(obj);
            return result != null ? result.toString() : "";
        } catch (Exception e) {
            try {
                var field = obj.getClass().getDeclaredField("animationName");
                field.setAccessible(true);
                Object result = field.get(obj);
                return result != null ? result.toString() : "";
            } catch (Exception ex) {
                return "";
            }
        }
    }
    
    private boolean setStringProperty(Object obj, String value) {
        try {
            var method = obj.getClass().getMethod("setAnimationName", String.class);
            method.invoke(obj, value);
            return true;
        } catch (Exception e) {
            try {
                var field = obj.getClass().getDeclaredField("animationName");
                field.setAccessible(true);
                field.set(obj, value);
                return true;
            } catch (Exception ex) {
                return false;
            }
        }
    }
    
    private boolean getBooleanProperty(Object obj, String fieldName, String getterName, boolean defaultValue) {
        try {
            var method = obj.getClass().getMethod(getterName);
            Object result = method.invoke(obj);
            return result instanceof Boolean ? (Boolean) result : defaultValue;
        } catch (Exception e) {
            try {
                var field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object result = field.get(obj);
                return result instanceof Boolean ? (Boolean) result : defaultValue;
            } catch (Exception ex) {
                return defaultValue;
            }
        }
    }
    
    private boolean setBooleanProperty(Object obj, String fieldName, String setterName, boolean value) {
        try {
            var method = obj.getClass().getMethod(setterName, boolean.class);
            method.invoke(obj, value);
            return true;
        } catch (Exception e) {
            try {
                var field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(obj, value);
                return true;
            } catch (Exception ex) {
                return false;
            }
        }
    }
    
    private int getIntProperty(Object obj) {
        try {
            var method = obj.getClass().getMethod("getBokehQuality");
            Object result = method.invoke(obj);
            return result instanceof Integer ? (Integer) result : 2;
        } catch (Exception e) {
            try {
                var field = obj.getClass().getDeclaredField("bokehQuality");
                field.setAccessible(true);
                Object result = field.get(obj);
                return result instanceof Integer ? (Integer) result : 2;
            } catch (Exception ex) {
                return 2;
            }
        }
    }
    
    private boolean setIntProperty(Object obj, int value) {
        try {
            var method = obj.getClass().getMethod("setBokehQuality", int.class);
            method.invoke(obj, value);
            return true;
        } catch (Exception e) {
            try {
                var field = obj.getClass().getDeclaredField("bokehQuality");
                field.setAccessible(true);
                field.set(obj, value);
                return true;
            } catch (Exception ex) {
                return false;
            }
        }
    }
    
    private float getFloatProperty(Object obj, String fieldName, String getterName, float defaultValue) {
        try {
            var method = obj.getClass().getMethod(getterName);
            Object result = method.invoke(obj);
            if (result instanceof Float) return (Float) result;
            if (result instanceof Double) return ((Double) result).floatValue();
            return defaultValue;
        } catch (Exception e) {
            try {
                var field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object result = field.get(obj);
                if (result instanceof Float) return (Float) result;
                if (result instanceof Double) return ((Double) result).floatValue();
                return defaultValue;
            } catch (Exception ex) {
                return defaultValue;
            }
        }
    }
    
    private boolean setFloatProperty(Object obj, String fieldName, String setterName, float value) {
        try {
            var method = obj.getClass().getMethod(setterName, float.class);
            method.invoke(obj, value);
            return true;
        } catch (Exception e) {
            try {
                var field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(obj, value);
                return true;
            } catch (Exception ex) {
                return false;
            }
        }
    }
    
    private Object getObjectProperty(Object obj, String fieldName, String getterName) {
        try {
            var method = obj.getClass().getMethod(getterName);
            return method.invoke(obj);
        } catch (Exception e) {
            try {
                var field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(obj);
            } catch (Exception ex) {
                return null;
            }
        }
    }
    
    private boolean callMethod(Object obj, String methodName) {
        try {
            var method = obj.getClass().getMethod(methodName);
            method.invoke(obj);
            return true;
        } catch (Exception e) {
            LOGGER.debug("[CameraPropertyAdapter] 调用方法失败: {}", methodName);
            return false;
        }
    }

    @Override
    public Class<Object> getTargetType() {
        return Object.class;
    }

    @Override
    public int getPriority() { return 60; }
    
    @Override
    public String getAdapterName() {
        return "相机属性编辑器";
    }
    
    @Override
    public boolean supports(Object target) {
        if (target == null) return false;
        
        String className = target.getClass().getSimpleName().toLowerCase();
        return className.contains("camera");
    }
}

