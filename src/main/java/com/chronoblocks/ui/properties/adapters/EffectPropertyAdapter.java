package com.chronoblocks.ui.properties.adapters;

import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.properties.PropertyAdapterRegistry;
import com.chronoblocks.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImString;
import imgui.type.ImInt;
import imgui.type.ImBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 特效属性适配器 v4.7 - ChronoBlocks Properties System
 * 
 * <p>为 ChronoBlocks 特效系统提供专用属性编辑器。
 * 
 * <h3>支持的特效类型</h3>
 * <ul>
 *   <li>✨ 粒子特效 - 粒子数量、速度、散射、重力</li>
 *   <li>💡 光照特效 - 亮度、半径、颜色、闪烁</li>
 *   <li>📷 镜头特效 - 振幅、频率、随机相位</li>
 *   <li>🎨 材质特效 - 透明度渐变、影响子对象</li>
 *   <li>🌪️ 物理特效 - 力场、风向、涡流</li>
 *   <li>🔊 音效特效 - 音量、音调、空间化</li>
 * </ul>
 * 
 * <h3>支持的属性</h3>
 * <ul>
 *   <li>📋 基础信息（名称、启用状态、特效类型）</li>
 *   <li>⏱ 时间控制（开始时间、持续时间、强度）</li>
 *   <li>🎨 通用参数（位置偏移、颜色、随机性）</li>
 *   <li>⚙️ 类型参数（根据特效类型动态显示）</li>
 *   <li>🔄 播放控制（循环、淡入淡出）</li>
 * </ul>
 * 
 * <h3>UI 特性</h3>
 * <ul>
 *   <li>折叠式分组 - 清晰的属性分类</li>
 *   <li>动态参数 - 根据特效类型显示专属参数</li>
 *   <li>实时预览 - 当前值显示</li>
 *   <li>智能提示 - Tooltip 说明</li>
 *   <li>颜色拾取 - 特效颜色编辑</li>
 *   <li>快捷操作 - 复制、重置等</li>
 * </ul>
 * 
 * <h3>注意事项</h3>
 * <p>由于特效系统可能尚未完整实现，此适配器采用通用 Object 类型，
 * 使用反射和类型检查来动态渲染属性。当特效系统完整后，可以
 * 替换为具体的 EffectBase 类型。
 * 
 * @author ChronoBlocks Team
 * @version 4.7
 * @since 2025-10-16
 */
public class EffectPropertyAdapter implements IPropertyAdapter<Object> {
    private static final Logger LOGGER = LoggerFactory.getLogger(EffectPropertyAdapter.class);
    
    // 特效类型常量
    private static final String[] EFFECT_TYPES = {
        "✨ 粒子特效",
        "💡 光照特效",
        "📷 镜头抖动",
        "🎨 材质渐变",
        "🌪️ 物理力场",
        "🔊 音效播放"
    };

    @Override
    public boolean renderProperties(Object effect) {
        if (effect == null) {
            UITheme.spacingLarge();
            UITheme.label("特效对象为 null", UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }
        
        // 使用数组包装以支持 lambda 修改
        final boolean[] modifiedRef = {false};
        
        // 标题
        UITheme.title("✨ 特效属性编辑器");
        
        // 使用反射获取特效属性
        // 注意：这是临时方案，待特效系统完整后替换为具体类型
        
        try {
            // ========== 基础信息 ==========
            if (ImGui.collapsingHeader("📋 基础信息", ImGuiTreeNodeFlags.DefaultOpen)) {
                // 名称编辑（尝试反射获取）
                String currentName = getStringProperty(effect, "name", "getName", "未命名特效");
                ImString nameBuffer = new ImString(currentName, 256);
                if (ImGui.inputText("特效名称", nameBuffer, ImGuiInputTextFlags.EnterReturnsTrue)) {
                    if (setStringProperty(effect, nameBuffer.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[EffectPropertyAdapter] 特效名称已更新: {}", nameBuffer.get());
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("特效的显示名称");
                }
                
                // 启用状态
                boolean currentEnabled = getBooleanProperty(effect, "enabled", "isEnabled", true);
                ImBoolean enabled = new ImBoolean(currentEnabled);
                if (ImGui.checkbox("启用特效", enabled)) {
                    if (setBooleanProperty(effect, "enabled", "setEnabled", enabled.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[EffectPropertyAdapter] 启用状态已更新: {}", enabled.get());
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("控制特效是否生效");
                }
                
                // 特效类型选择
                int currentTypeIndex = getIntProperty(effect, "typeIndex", "getTypeIndex", 0);
                ImInt selectedIdx = new ImInt(currentTypeIndex);
                
                if (ImGui.combo("特效类型", selectedIdx, EFFECT_TYPES)) {
                    if (setIntProperty(effect, "typeIndex", "setTypeIndex", selectedIdx.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[EffectPropertyAdapter] 特效类型已更新: {}", 
                            EFFECT_TYPES[selectedIdx.get()]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("选择特效的类型，不同类型有不同的参数");
                }
                
                // UUID 显示（如果有）
                String id = getStringProperty(effect, "id", "getId", null);
                if (id != null) {
                    UITheme.spacingSmall();
                    UITheme.label("ID: " + id, UITheme.DarkTheme.TEXT_DISABLED);
                }
            }
            
            // ========== 时间控制 ==========
            if (ImGui.collapsingHeader("⏱ 时间控制", ImGuiTreeNodeFlags.DefaultOpen)) {
                // 开始时间
                float currentStart = getFloatProperty(effect, "startTime", "getStartTime", 0.0f);
                float[] startTime = {currentStart};
                if (ImGui.dragFloat("开始时间 (秒)", startTime, 0.05f, 0.0f, 600.0f)) {
                    if (setFloatProperty(effect, "startTime", "setStartTime", startTime[0])) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[EffectPropertyAdapter] 开始时间已更新: {}", startTime[0]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("特效在时间轴上的起始位置");
                }
                
                // 持续时间
                float currentDuration = getFloatProperty(effect, "duration", "getDuration", 2.0f);
                float[] duration = {currentDuration};
                if (ImGui.dragFloat("持续时间 (秒)", duration, 0.05f, 0.1f, 600.0f)) {
                    if (setFloatProperty(effect, "duration", "setDuration", duration[0])) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[EffectPropertyAdapter] 持续时间已更新: {}", duration[0]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("特效持续的时间长度");
                }
                
                // 强度
                float currentIntensity = getFloatProperty(effect, "intensity", "getIntensity", 1.0f);
                float[] intensity = {currentIntensity};
                if (ImGui.sliderFloat("强度", intensity, 0.0f, 10.0f, "%.2f")) {
                    if (setFloatProperty(effect, "intensity", "setIntensity", intensity[0])) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[EffectPropertyAdapter] 强度已更新: {}", intensity[0]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("特效的强度或力度");
                }
                
                // 百分比显示
                UITheme.spacingSmall();
                UITheme.label(String.format("强度: %.0f%%", intensity[0] * 10), 
                    UITheme.DarkTheme.TEXT_DISABLED);
            }
            
            // ========== 通用参数 ==========
            if (ImGui.collapsingHeader("🎨 通用参数")) {
                // 位置偏移（尝试使用 Vector3fPropertyAdapter）
                Object offset = getObjectProperty(effect, "offset", "getOffset");
                if (offset != null) {
                    var vector3fAdapter = PropertyAdapterRegistry.getAdapterFor(offset);
                    if (vector3fAdapter != null) {
                        UITheme.label("位置偏移", UITheme.DarkTheme.TEXT);
                        if (vector3fAdapter.renderProperties(offset)) {
                            modifiedRef[0] = true;
                        }
                    }
                }
                
                UITheme.spacingSmall();
                
                // 颜色（尝试使用 ColorPropertyAdapter）
                Object color = getObjectProperty(effect, "color", "getColor");
                if (color != null) {
                    var colorAdapter = PropertyAdapterRegistry.getAdapterFor(color);
                    if (colorAdapter != null) {
                        UITheme.label("特效颜色", UITheme.DarkTheme.TEXT);
                        if (colorAdapter.renderProperties(color)) {
                            modifiedRef[0] = true;
                        }
                    }
                } else {
                    // 尝试 float[] colorRGBA
                    float[] colorRGBA = getFloatArrayProperty(effect);
                    if (colorRGBA != null && colorRGBA.length >= 4) {
                        if (ImGui.colorEdit4("特效颜色 (RGBA)", colorRGBA)) {
                            if (setFloatArrayProperty(effect, colorRGBA)) {
                                modifiedRef[0] = true;
                                LOGGER.debug("[EffectPropertyAdapter] 颜色已更新");
                            }
                        }
                    }
                }
                
                UITheme.spacingSmall();
                
                // 随机性
                float currentRandomness = getFloatProperty(effect, "randomness", "getRandomness", 0.0f);
                float[] randomness = {currentRandomness};
                if (ImGui.sliderFloat("随机性", randomness, 0.0f, 1.0f, "%.2f")) {
                    if (setFloatProperty(effect, "randomness", "setRandomness", randomness[0])) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[EffectPropertyAdapter] 随机性已更新: {}", randomness[0]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("增加随机性可以让特效更自然");
                }
            }
            
            // ========== 类型特定参数 ==========
            int typeIndex = getIntProperty(effect, "typeIndex", "getTypeIndex", 0);
            renderTypeSpecificParameters(effect, typeIndex, modified -> modifiedRef[0] = modifiedRef[0] || modified);
            
            UITheme.separator();
            
            // ========== 快捷操作 ==========
            UITheme.beginGroup("快捷操作");
            
            if (ImGui.button("重置参数", 100, UITheme.Spacing.BUTTON_HEIGHT)) {
                LOGGER.info("[EffectPropertyAdapter] 重置特效参数");
                // TODO: 实现重置逻辑
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("将所有参数重置为默认值");
            }
            
            ImGui.sameLine();
            
            if (ImGui.button("复制特效", 100, UITheme.Spacing.BUTTON_HEIGHT)) {
                LOGGER.info("[EffectPropertyAdapter] 复制特效");
                // TODO: 实现复制逻辑
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("创建此特效的副本");
            }
            
            ImGui.sameLine();
            
            if (ImGui.button("预览特效", 100, UITheme.Spacing.BUTTON_HEIGHT)) {
                LOGGER.info("[EffectPropertyAdapter] 预览特效");
                // TODO: 实现预览逻辑
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("播放特效预览");
            }
            
            UITheme.endGroup();
            
        } catch (Exception e) {
            LOGGER.error("[EffectPropertyAdapter] 渲染特效属性时出错", e);
            UITheme.label("渲染出错: " + e.getMessage(), UITheme.DarkTheme.TEXT_DISABLED);
        }
        
        return modifiedRef[0];
    }
    
    /**
     * 渲染类型特定参数
     */
    private void renderTypeSpecificParameters(Object effect, int typeIndex, ModifiedCallback callback) {
        switch (typeIndex) {
            case 0 -> renderParticleParameters(effect, callback);
            case 1 -> renderLightParameters(effect, callback);
            case 2 -> renderCameraShakeParameters(effect, callback);
            case 3 -> renderMaterialFadeParameters(effect, callback);
            case 4 -> renderPhysicsParameters(effect, callback);
            case 5 -> renderSoundParameters(effect, callback);
        }
    }
    
    /**
     * 粒子特效参数
     */
    private void renderParticleParameters(Object effect, ModifiedCallback callback) {
        if (ImGui.collapsingHeader("✨ 粒子参数")) {
            // 粒子数量
            int count = getIntProperty(effect, "particleCount", "getParticleCount", 100);
            int[] particleCount = {count};
            if (ImGui.dragInt("粒子数量", particleCount, 1.0f, 1, 10000)) {
                if (setIntProperty(effect, "particleCount", "setParticleCount", particleCount[0])) {
                    callback.onModified(true);
                }
            }
            
            // 速度
            float speed = getFloatProperty(effect, "speed", "getSpeed", 1.0f);
            float[] speedArr = {speed};
            if (ImGui.dragFloat("发射速度", speedArr, 0.01f, 0.0f, 10.0f)) {
                if (setFloatProperty(effect, "speed", "setSpeed", speedArr[0])) {
                    callback.onModified(true);
                }
            }
            
            // 散射角
            float spread = getFloatProperty(effect, "spread", "getSpread", 45.0f);
            float[] spreadArr = {spread};
            if (ImGui.dragFloat("散射角度 (°)", spreadArr, 1.0f, 0.0f, 180.0f)) {
                if (setFloatProperty(effect, "spread", "setSpread", spreadArr[0])) {
                    callback.onModified(true);
                }
            }
            
            // 受重力影响
            boolean gravity = getBooleanProperty(effect, "affectedByGravity", "isAffectedByGravity", true);
            ImBoolean gravityBool = new ImBoolean(gravity);
            if (ImGui.checkbox("受重力影响", gravityBool)) {
                if (setBooleanProperty(effect, "affectedByGravity", "setAffectedByGravity", gravityBool.get())) {
                    callback.onModified(true);
                }
            }
        }
    }
    
    /**
     * 光照特效参数
     */
    private void renderLightParameters(Object effect, ModifiedCallback callback) {
        if (ImGui.collapsingHeader("💡 光照参数")) {
            // 亮度
            float brightness = getFloatProperty(effect, "brightness", "getBrightness", 1.0f);
            float[] brightnessArr = {brightness};
            if (ImGui.sliderFloat("亮度", brightnessArr, 0.0f, 5.0f, "%.2f")) {
                if (setFloatProperty(effect, "brightness", "setBrightness", brightnessArr[0])) {
                    callback.onModified(true);
                }
            }
            
            // 半径
            float radius = getFloatProperty(effect, "radius", "getRadius", 5.0f);
            float[] radiusArr = {radius};
            if (ImGui.dragFloat("光照半径", radiusArr, 0.1f, 0.0f, 50.0f)) {
                if (setFloatProperty(effect, "radius", "setRadius", radiusArr[0])) {
                    callback.onModified(true);
                }
            }
            
            // 闪烁
            boolean flicker = getBooleanProperty(effect, "flicker", "isFlicker", false);
            ImBoolean flickerBool = new ImBoolean(flicker);
            if (ImGui.checkbox("光照闪烁", flickerBool)) {
                if (setBooleanProperty(effect, "flicker", "setFlicker", flickerBool.get())) {
                    callback.onModified(true);
                }
            }
        }
    }
    
    /**
     * 镜头抖动参数
     */
    private void renderCameraShakeParameters(Object effect, ModifiedCallback callback) {
        if (ImGui.collapsingHeader("📷 镜头抖动参数")) {
            // 振幅
            float amplitude = getFloatProperty(effect, "amplitude", "getAmplitude", 0.2f);
            float[] amplitudeArr = {amplitude};
            if (ImGui.dragFloat("振幅", amplitudeArr, 0.01f, 0.0f, 2.0f)) {
                if (setFloatProperty(effect, "amplitude", "setAmplitude", amplitudeArr[0])) {
                    callback.onModified(true);
                }
            }
            
            // 频率
            float frequency = getFloatProperty(effect, "frequency", "getFrequency", 5.0f);
            float[] frequencyArr = {frequency};
            if (ImGui.dragFloat("频率 (Hz)", frequencyArr, 0.1f, 0.0f, 20.0f)) {
                if (setFloatProperty(effect, "frequency", "setFrequency", frequencyArr[0])) {
                    callback.onModified(true);
                }
            }
            
            // 随机相位
            boolean randomPhase = getBooleanProperty(effect, "randomPhase", "isRandomPhase", true);
            ImBoolean randomPhaseBool = new ImBoolean(randomPhase);
            if (ImGui.checkbox("随机相位", randomPhaseBool)) {
                if (setBooleanProperty(effect, "randomPhase", "setRandomPhase", randomPhaseBool.get())) {
                    callback.onModified(true);
                }
            }
        }
    }
    
    /**
     * 材质渐变参数
     */
    private void renderMaterialFadeParameters(Object effect, ModifiedCallback callback) {
        if (ImGui.collapsingHeader("🎨 材质渐变参数")) {
            // 起始透明度
            float fromAlpha = getFloatProperty(effect, "fromAlpha", "getFromAlpha", 1.0f);
            float[] fromAlphaArr = {fromAlpha};
            if (ImGui.sliderFloat("起始透明度", fromAlphaArr, 0.0f, 1.0f, "%.2f")) {
                if (setFloatProperty(effect, "fromAlpha", "setFromAlpha", fromAlphaArr[0])) {
                    callback.onModified(true);
                }
            }
            
            // 目标透明度
            float toAlpha = getFloatProperty(effect, "toAlpha", "getToAlpha", 0.0f);
            float[] toAlphaArr = {toAlpha};
            if (ImGui.sliderFloat("目标透明度", toAlphaArr, 0.0f, 1.0f, "%.2f")) {
                if (setFloatProperty(effect, "toAlpha", "setToAlpha", toAlphaArr[0])) {
                    callback.onModified(true);
                }
            }
            
            // 影响子对象
            boolean affectChildren = getBooleanProperty(effect, "affectChildren", "isAffectChildren", false);
            ImBoolean affectChildrenBool = new ImBoolean(affectChildren);
            if (ImGui.checkbox("影响子对象", affectChildrenBool)) {
                if (setBooleanProperty(effect, "affectChildren", "setAffectChildren", affectChildrenBool.get())) {
                    callback.onModified(true);
                }
            }
        }
    }
    
    /**
     * 物理力场参数
     */
    private void renderPhysicsParameters(Object effect, ModifiedCallback callback) {
        if (ImGui.collapsingHeader("🌪️ 物理力场参数")) {
            UITheme.label("物理力场特效参数", UITheme.DarkTheme.TEXT_DISABLED);
            UITheme.label("（待实现）", UITheme.DarkTheme.TEXT_DISABLED);
        }
    }
    
    /**
     * 音效参数
     */
    private void renderSoundParameters(Object effect, ModifiedCallback callback) {
        if (ImGui.collapsingHeader("🔊 音效参数")) {
            UITheme.label("音效播放参数", UITheme.DarkTheme.TEXT_DISABLED);
            UITheme.label("（待实现）", UITheme.DarkTheme.TEXT_DISABLED);
        }
    }
    
    // ========== 反射辅助方法 ==========
    
    private String getStringProperty(Object obj, String fieldName, String getterName, String defaultValue) {
        try {
            // 尝试 getter 方法
            var method = obj.getClass().getMethod(getterName);
            Object result = method.invoke(obj);
            return result != null ? result.toString() : defaultValue;
        } catch (Exception e) {
            try {
                // 尝试直接访问字段
                var field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object result = field.get(obj);
                return result != null ? result.toString() : defaultValue;
            } catch (Exception ex) {
                return defaultValue;
            }
        }
    }
    
    private boolean setStringProperty(Object obj, String value) {
        try {
            var method = obj.getClass().getMethod("setName", String.class);
            method.invoke(obj, value);
            return true;
        } catch (Exception e) {
            try {
                var field = obj.getClass().getDeclaredField("name");
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
    
    private float[] getFloatArrayProperty(Object obj) {
        try {
            var method = obj.getClass().getMethod("getColorRGBA");
            Object result = method.invoke(obj);
            return result instanceof float[] ? (float[]) result : null;
        } catch (Exception e) {
            try {
                var field = obj.getClass().getDeclaredField("colorRGBA");
                field.setAccessible(true);
                Object result = field.get(obj);
                return result instanceof float[] ? (float[]) result : null;
            } catch (Exception ex) {
                return null;
            }
        }
    }
    
    private boolean setFloatArrayProperty(Object obj, float[] value) {
        try {
            var method = obj.getClass().getMethod("setColorRGBA", float[].class);
            method.invoke(obj, (Object) value);
            return true;
        } catch (Exception e) {
            try {
                var field = obj.getClass().getDeclaredField("colorRGBA");
                field.setAccessible(true);
                field.set(obj, value);
                return true;
            } catch (Exception ex) {
                return false;
            }
        }
    }

    @Override
    public Class<Object> getTargetType() {
        // 使用 Object 以支持各种特效类型
        return Object.class;
    }
    
    @Override
    public String getAdapterName() {
        return "特效属性编辑器";
    }
    
    @Override
    public boolean supports(Object target) {
        if (target == null) return false;
        
        // 检查是否是特效对象
        // 可以通过类名、接口或特定字段来判断
        String className = target.getClass().getSimpleName().toLowerCase();
        return className.contains("effect") || 
               className.contains("particle") ||
               className.contains("light");
    }
    
    /**
     * 修改回调接口
     */
    @FunctionalInterface
    private interface ModifiedCallback {
        void onModified(boolean modified);
    }
}

