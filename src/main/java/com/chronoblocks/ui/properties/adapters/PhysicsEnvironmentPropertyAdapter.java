package com.chronoblocks.ui.properties.adapters;

import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.properties.PropertyAdapterRegistry;
import com.chronoblocks.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 物理环境属性适配器 v4.9 - ChronoBlocks Properties System
 * 
 * <p>为 ChronoBlocks 物理环境对象提供专用属性编辑器。
 * 
 * <h3>物理环境（PhysicsEnvironment）</h3>
 * <p>PhysicsEnvironment 控制场景的物理模拟参数，包括：
 * <ul>
 *   <li>重力系统 - 重力方向和强度</li>
 *   <li>风场系统 - 风向、风速、湍流</li>
 *   <li>碰撞系统 - 碰撞检测、弹性、摩擦</li>
 *   <li>空气阻力 - 阻力系数、流体密度</li>
 *   <li>物理特效 - 爆炸力、吸引力场</li>
 * </ul>
 * 
 * <h3>支持的属性</h3>
 * <ul>
 *   <li>🌍 重力系统（重力向量、重力强度、启用开关）</li>
 *   <li>🌪️ 风场系统（风向量、风速、湍流、阵风）</li>
 *   <li>💥 碰撞系统（启用、弹性系数、摩擦系数）</li>
 *   <li>🌫️ 空气阻力（阻力系数、流体密度）</li>
 *   <li>⚡ 快捷操作（重置、预设、启用/禁用）</li>
 * </ul>
 * 
 * <h3>重力预设</h3>
 * <ul>
 *   <li>地球重力 - (0, -9.8, 0)</li>
 *   <li>月球重力 - (0, -1.6, 0)</li>
 *   <li>火星重力 - (0, -3.7, 0)</li>
 *   <li>零重力 - (0, 0, 0)</li>
 * </ul>
 * 
 * <h3>UI 特性</h3>
 * <ul>
 *   <li>折叠式分组 - 清晰的属性分类</li>
 *   <li>Vector3f 集成 - 使用 Vector3fPropertyAdapter</li>
 *   <li>重力预设 - 快速切换常用重力</li>
 *   <li>实时预览 - 当前值显示</li>
 *   <li>智能提示 - Tooltip 说明</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 4.9
 * @since 2025-10-16
 */
public class PhysicsEnvironmentPropertyAdapter implements IPropertyAdapter<Object> {
    private static final Logger LOGGER = LoggerFactory.getLogger(PhysicsEnvironmentPropertyAdapter.class);
    
    // 重力预设
    private static final float[][] GRAVITY_PRESETS = {
        {0.0f, -9.8f, 0.0f},    // 地球
        {0.0f, -1.6f, 0.0f},    // 月球
        {0.0f, -3.7f, 0.0f},    // 火星
        {0.0f, 0.0f, 0.0f}      // 零重力
    };
    
    private static final String[] GRAVITY_PRESET_NAMES = {
        "🌍 地球重力",
        "🌙 月球重力",
        "🔴 火星重力",
        "🚀 零重力"
    };

    @Override
    public boolean renderProperties(Object physics) {
        if (physics == null) {
            UITheme.spacingLarge();
            UITheme.label("物理环境为 null", UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }
        
        final boolean[] modifiedRef = {false};
        
        // 标题
        UITheme.title("🧮 物理环境属性编辑器");
        
        try {
            // ========== 重力系统 ==========
            if (ImGui.collapsingHeader("🌍 重力系统", ImGuiTreeNodeFlags.DefaultOpen)) {
                // 重力向量（使用 Vector3fPropertyAdapter）
                Object gravity = getObjectProperty(physics, "gravity", "getGravity");
                if (gravity != null) {
                    var gravityAdapter = PropertyAdapterRegistry.getAdapterFor(gravity);
                    if (gravityAdapter != null) {
                        UITheme.label("重力向量", UITheme.DarkTheme.TEXT);
                        if (gravityAdapter.renderProperties(gravity)) {
                            modifiedRef[0] = true;
                        }
                    }
                }
                
                // 重力预设
                UITheme.spacingSmall();
                UITheme.label("快速预设:", UITheme.DarkTheme.TEXT);
                
                for (int i = 0; i < GRAVITY_PRESETS.length; i++) {
                    ImGui.pushID("gravity_" + i);
                    if (ImGui.button(GRAVITY_PRESET_NAMES[i], 100, UITheme.Spacing.BUTTON_HEIGHT)) {
                        applyGravityPreset(physics, i);
                        modifiedRef[0] = true;
                        LOGGER.debug("[PhysicsEnvironmentPropertyAdapter] 应用重力预设: {}", 
                            GRAVITY_PRESET_NAMES[i]);
                    }
                    if ((i + 1) % 2 != 0 && i < GRAVITY_PRESETS.length - 1) {
                        ImGui.sameLine();
                    }
                    ImGui.popID();
                }
            }
            
            // ========== 风场系统 ==========
            if (ImGui.collapsingHeader("🌪️ 风场系统")) {
                // 风向量（使用 Vector3fPropertyAdapter）
                Object wind = getObjectProperty(physics, "wind", "getWind");
                if (wind != null) {
                    var windAdapter = PropertyAdapterRegistry.getAdapterFor(wind);
                    if (windAdapter != null) {
                        UITheme.label("风向量", UITheme.DarkTheme.TEXT);
                        if (windAdapter.renderProperties(wind)) {
                            modifiedRef[0] = true;
                        }
                    }
                }
                
                UITheme.spacingSmall();
                
                // 风速
                float currentWindSpeed = getFloatProperty(physics, "windSpeed", "getWindSpeed", 0.0f);
                float[] windSpeed = {currentWindSpeed};
                if (ImGui.dragFloat("风速", windSpeed, 0.1f, 0.0f, 50.0f, "%.1f")) {
                    if (setFloatProperty(physics, "windSpeed", "setWindSpeed", windSpeed[0])) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[PhysicsEnvironmentPropertyAdapter] 风速已更新: {}", windSpeed[0]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("风的强度");
                }
                
                // 湍流
                float currentTurbulence = getFloatProperty(physics, "turbulence", "getTurbulence", 0.0f);
                float[] turbulence = {currentTurbulence};
                if (ImGui.dragFloat("湍流强度", turbulence, 0.05f, 0.0f, 1.0f, "%.2f")) {
                    if (setFloatProperty(physics, "turbulence", "setTurbulence", turbulence[0])) {
                        modifiedRef[0] = true;
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("风的不规则程度");
                }
            }
            
            // ========== 碰撞系统 ==========
            if (ImGui.collapsingHeader("💥 碰撞系统")) {
                // 启用碰撞
                boolean currentCollision = getBooleanProperty(physics, "collisionEnabled", "isCollisionEnabled", true);
                ImBoolean enableCollision = new ImBoolean(currentCollision);
                if (ImGui.checkbox("启用碰撞检测", enableCollision)) {
                    if (setBooleanProperty(physics, "collisionEnabled", "setCollisionEnabled", enableCollision.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[PhysicsEnvironmentPropertyAdapter] 碰撞检测已更新: {}", enableCollision.get());
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("启用物体间的碰撞检测");
                }
                
                // 弹性系数
                float currentRestitution = getFloatProperty(physics, "restitution", "getRestitution", 0.5f);
                float[] restitution = {currentRestitution};
                if (ImGui.dragFloat("弹性系数", restitution, 0.05f, 0.0f, 1.0f, "%.2f")) {
                    if (setFloatProperty(physics, "restitution", "setRestitution", restitution[0])) {
                        modifiedRef[0] = true;
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("碰撞后的反弹能量（0 = 完全吸收，1 = 完全反弹）");
                }
                
                // 摩擦系数
                float currentFriction = getFloatProperty(physics, "friction", "getFriction", 0.5f);
                float[] friction = {currentFriction};
                if (ImGui.dragFloat("摩擦系数", friction, 0.05f, 0.0f, 1.0f, "%.2f")) {
                    if (setFloatProperty(physics, "friction", "setFriction", friction[0])) {
                        modifiedRef[0] = true;
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("表面摩擦力（0 = 光滑，1 = 粗糙）");
                }
            }
            
            // ========== 空气阻力 ==========
            if (ImGui.collapsingHeader("🌫️ 空气阻力")) {
                // 阻力系数
                float currentDrag = getFloatProperty(physics, "airResistance", "getAirResistance", 0.01f);
                float[] airDrag = {currentDrag};
                if (ImGui.dragFloat("阻力系数", airDrag, 0.01f, 0.0f, 1.0f, "%.3f")) {
                    if (setFloatProperty(physics, "airResistance", "setAirResistance", airDrag[0])) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[PhysicsEnvironmentPropertyAdapter] 空气阻力已更新: {}", airDrag[0]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("空气对运动物体的阻力");
                }
                
                // 流体密度
                float currentDensity = getFloatProperty(physics, "fluidDensity", "getFluidDensity", 1.0f);
                float[] fluidDensity = {currentDensity};
                if (ImGui.dragFloat("流体密度", fluidDensity, 0.1f, 0.0f, 10.0f, "%.1f")) {
                    if (setFloatProperty(physics, "fluidDensity", "setFluidDensity", fluidDensity[0])) {
                        modifiedRef[0] = true;
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("环境流体的密度（1.0 = 空气，更高 = 水、油等）");
                }
            }
            
            // ========== 物理开关 ==========
            if (ImGui.collapsingHeader("⚙️ 物理开关")) {
                // 启用物理系统
                boolean currentEnabled = getBooleanProperty(physics, "enabled", "isEnabled", true);
                ImBoolean enabled = new ImBoolean(currentEnabled);
                if (ImGui.checkbox("启用物理系统", enabled)) {
                    if (setBooleanProperty(physics, "enabled", "setEnabled", enabled.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[PhysicsEnvironmentPropertyAdapter] 物理系统已更新: {}", enabled.get());
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("启用后物体将受物理影响");
                }
                
                // 调试可视化
                boolean currentDebug = getBooleanProperty(physics, "debugVisualization", "isDebugVisualization", false);
                ImBoolean debugVis = new ImBoolean(currentDebug);
                if (ImGui.checkbox("调试可视化", debugVis)) {
                    if (setBooleanProperty(physics, "debugVisualization", "setDebugVisualization", debugVis.get())) {
                        modifiedRef[0] = true;
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("显示碰撞体、力场等调试信息");
                }
            }
            
            UITheme.separator();
            
            // ========== 快捷操作 ==========
            UITheme.beginGroup("快捷操作");
            
            if (ImGui.button("🔄 重置为默认", 120, UITheme.Spacing.BUTTON_HEIGHT)) {
                if (callMethod(physics, "resetToDefault")) {
                    modifiedRef[0] = true;
                    LOGGER.info("[PhysicsEnvironmentPropertyAdapter] 物理环境已重置");
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("重置所有物理参数为默认值");
            }
            
            ImGui.sameLine();
            
            if (ImGui.button("📋 复制配置", 120, UITheme.Spacing.BUTTON_HEIGHT)) {
                LOGGER.info("[PhysicsEnvironmentPropertyAdapter] 复制物理配置");
                // TODO: 复制到剪贴板
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("复制当前物理配置");
            }
            
            ImGui.sameLine();
            
            boolean currentEnabled = getBooleanProperty(physics, "enabled", "isEnabled", true);
            String toggleText = currentEnabled ? "⏸ 暂停物理" : "▶ 启用物理";
            if (ImGui.button(toggleText, 120, UITheme.Spacing.BUTTON_HEIGHT)) {
                if (setBooleanProperty(physics, "enabled", "setEnabled", !currentEnabled)) {
                    modifiedRef[0] = true;
                    LOGGER.info("[PhysicsEnvironmentPropertyAdapter] 物理系统切换: {}", !currentEnabled);
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("快速启用/禁用整个物理系统");
            }
            
            UITheme.endGroup();
            
        } catch (Exception e) {
            LOGGER.error("[PhysicsEnvironmentPropertyAdapter] 渲染物理环境属性时出错", e);
            UITheme.label("渲染出错: " + e.getMessage(), UITheme.DarkTheme.TEXT_DISABLED);
        }
        
        return modifiedRef[0];
    }
    
    /**
     * 应用重力预设
     */
    private void applyGravityPreset(Object physics, int presetIndex) {
        if (presetIndex < 0 || presetIndex >= GRAVITY_PRESETS.length) return;
        
        float[] preset = GRAVITY_PRESETS[presetIndex];
        
        // 尝试获取 gravity 对象并设置
        Object gravity = getObjectProperty(physics, "gravity", "getGravity");
        if (gravity != null) {
            try {
                // 尝试调用 set(x, y, z) 方法
                var setMethod = gravity.getClass().getMethod("set", float.class, float.class, float.class);
                setMethod.invoke(gravity, preset[0], preset[1], preset[2]);
            } catch (Exception e) {
                LOGGER.debug("[PhysicsEnvironmentPropertyAdapter] 设置重力向量失败");
            }
        }
    }
    
    // ========== 反射辅助方法 ==========
    
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
    
    private int getIntProperty(Object obj, String fieldName, String getterName, int defaultValue) {
        try {
            var method = obj.getClass().getMethod(getterName);
            Object result = method.invoke(obj);
            return result instanceof Integer ? (Integer) result : defaultValue;
        } catch (Exception e) {
            try {
                var field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object result = field.get(obj);
                return result instanceof Integer ? (Integer) result : defaultValue;
            } catch (Exception ex) {
                return defaultValue;
            }
        }
    }
    
    private boolean setIntProperty(Object obj, String fieldName, String setterName, int value) {
        try {
            var method = obj.getClass().getMethod(setterName, int.class);
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
            LOGGER.debug("[PhysicsEnvironmentPropertyAdapter] 调用方法失败: {}", methodName);
            return false;
        }
    }

    @Override
    public Class<Object> getTargetType() {
        return Object.class;
    }
    
    @Override
    public String getAdapterName() {
        return "物理环境属性编辑器";
    }
    
    @Override
    public boolean supports(Object target) {
        if (target == null) return false;
        
        String className = target.getClass().getSimpleName().toLowerCase();
        return className.contains("physics") && 
               (className.contains("environment") || className.contains("env"));
    }
}

