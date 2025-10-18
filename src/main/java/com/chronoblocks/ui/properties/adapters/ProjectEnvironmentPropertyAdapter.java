package com.chronoblocks.ui.properties.adapters;

import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 项目环境属性适配器 v4.9 - ChronoBlocks Properties System
 * 
 * <p>为 ChronoBlocks 项目环境对象提供专用属性编辑器。
 * 
 * <h3>项目环境（ProjectEnvironment）</h3>
 * <p>ProjectEnvironment 控制整个场景的视觉环境，包括：
 * <ul>
 *   <li>光照系统 - 环境光、天空光、光照方向</li>
 *   <li>天气系统 - 雨雪、雾效、昼夜循环</li>
 *   <li>渲染控制 - 阴影、后处理、曝光</li>
 *   <li>可视层控制 - 透明方块、实体、粒子</li>
 * </ul>
 * 
 * <h3>支持的属性</h3>
 * <ul>
 *   <li>💡 光照系统（环境光、天空光、光照方向、曝光）</li>
 *   <li>🌤 天气系统（雨雪、雾浓度、昼夜循环、时间）</li>
 *   <li>🎨 渲染控制（阴影、后处理、抗锯齿）</li>
 *   <li>🪟 可视层控制（透明方块、实体、粒子）</li>
 *   <li>⚡ 快捷操作（重置、预设、导入导出）</li>
 * </ul>
 * 
 * <h3>UI 特性</h3>
 * <ul>
 *   <li>折叠式分组 - 清晰的属性分类</li>
 *   <li>颜色拾取器 - 环境光、天空颜色</li>
 *   <li>实时预览 - 当前值显示</li>
 *   <li>环境预设 - 白天、黄昏、夜晚等</li>
 *   <li>智能提示 - Tooltip 说明</li>
 * </ul>
 * 
 * <h3>注意事项</h3>
 * <p>由于环境系统可能尚未完整实现，此适配器采用通用 Object 类型，
 * 使用反射和类型检查来动态渲染属性。
 * 
 * @author ChronoBlocks Team
 * @version 4.9
 * @since 2025-10-16
 */
public class ProjectEnvironmentPropertyAdapter implements IPropertyAdapter<Object> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectEnvironmentPropertyAdapter.class);
    
    // 环境预设
    private static final String[] ENV_PRESETS = {
        "☀️ 白天（明亮）",
        "🌅 黄昏（暖色）",
        "🌙 夜晚（暗淡）",
        "☁️ 阴天（柔和）",
        "🌫️ 雾天（朦胧）",
        "🌧️ 雨天（灰暗）"
    };
    
    // 时间预设（Minecraft ticks）
    private static final int[] TIME_PRESETS = {
        6000,   // 正午
        12000,  // 黄昏
        18000,  // 午夜
        0       // 日出
    };
    
    private static final String[] TIME_PRESET_NAMES = {
        "⛅ 正午 (6000)",
        "🌅 黄昏 (12000)",
        "🌙 午夜 (18000)",
        "🌄 日出 (0)"
    };

    @Override
    public boolean renderProperties(Object env) {
        if (env == null) {
            UITheme.spacingLarge();
            UITheme.label("项目环境为 null", UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }
        
        // 使用数组包装以支持 lambda 修改
        final boolean[] modifiedRef = {false};
        
        // 标题
        UITheme.title("🌍 项目环境属性编辑器");
        
        try {
            // ========== 光照系统 ==========
            if (ImGui.collapsingHeader("💡 光照系统", ImGuiTreeNodeFlags.DefaultOpen)) {
                // 环境光颜色
                float[] ambientColor = getFloatArrayProperty(env, "ambientColor", "getAmbientColor");
                if (ambientColor == null || ambientColor.length < 4) {
                    ambientColor = new float[]{0.7f, 0.7f, 0.7f, 1.0f};
                }
                
                if (ImGui.colorEdit4("环境光颜色", ambientColor)) {
                    if (setFloatArrayProperty(env, "ambientColor", "setAmbientColor", ambientColor)) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[ProjectEnvironmentPropertyAdapter] 环境光颜色已更新");
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("场景的基础光照颜色");
                }
                
                // 天空光颜色
                float[] skyColor = getFloatArrayProperty(env, "skyColor", "getSkyColor");
                if (skyColor == null || skyColor.length < 4) {
                    skyColor = new float[]{0.5f, 0.7f, 1.0f, 1.0f};
                }
                
                if (ImGui.colorEdit4("天空光颜色", skyColor)) {
                    if (setFloatArrayProperty(env, "skyColor", "setSkyColor", skyColor)) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[ProjectEnvironmentPropertyAdapter] 天空光颜色已更新");
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("天空的光照颜色");
                }
                
                // 光照方向
                float lightYaw = getFloatProperty(env, "lightYaw", "getLightYaw", 45.0f);
                float lightPitch = getFloatProperty(env, "lightPitch", "getLightPitch", 60.0f);
                float[] lightDirection = {lightYaw, lightPitch};
                
                if (ImGui.dragFloat2("光照方向 (偏航/俯仰)", lightDirection, 1.0f, 0.0f, 360.0f)) {
                    setFloatProperty(env, "lightYaw", "setLightYaw", lightDirection[0]);
                    setFloatProperty(env, "lightPitch", "setLightPitch", lightDirection[1]);
                    modifiedRef[0] = true;
                    LOGGER.debug("[ProjectEnvironmentPropertyAdapter] 光照方向已更新: ({}, {})", 
                        lightDirection[0], lightDirection[1]);
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("主光源的方向角度");
                }
                
                // 曝光度
                float currentExposure = getFloatProperty(env, "exposure", "getExposure", 1.0f);
                float[] exposure = {currentExposure};
                if (ImGui.dragFloat("曝光度", exposure, 0.05f, 0.1f, 3.0f, "%.2f")) {
                    if (setFloatProperty(env, "exposure", "setExposure", exposure[0])) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[ProjectEnvironmentPropertyAdapter] 曝光度已更新: {}", exposure[0]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("场景整体亮度（1.0 = 正常，>1.0 = 过曝，<1.0 = 欠曝）");
                }
            }
            
            // ========== 天气系统 ==========
            if (ImGui.collapsingHeader("🌤 天气系统", ImGuiTreeNodeFlags.DefaultOpen)) {
                // 雨雪天气
                boolean currentRain = getBooleanProperty(env, "weatherRain", "isWeatherRain", false);
                ImBoolean enableRain = new ImBoolean(currentRain);
                if (ImGui.checkbox("启用雨雪天气", enableRain)) {
                    if (setBooleanProperty(env, "weatherRain", "setWeatherRain", enableRain.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[ProjectEnvironmentPropertyAdapter] 雨雪天气已更新: {}", enableRain.get());
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("显示雨雪粒子效果");
                }
                
                // 雾浓度
                float currentFog = getFloatProperty(env, "fogDensity", "getFogDensity", 0.1f);
                float[] fogDensity = {currentFog};
                if (ImGui.dragFloat("雾浓度", fogDensity, 0.01f, 0.0f, 1.0f, "%.2f")) {
                    if (setFloatProperty(env, "fogDensity", "setFogDensity", fogDensity[0])) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[ProjectEnvironmentPropertyAdapter] 雾浓度已更新: {}", fogDensity[0]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("雾的密度（0.0 = 无雾，1.0 = 浓雾）");
                }
                
                UITheme.spacingSmall();
                
                // 昼夜循环
                boolean currentDayCycle = getBooleanProperty(env, "dayCycle", "isDayCycle", false);
                ImBoolean enableDayCycle = new ImBoolean(currentDayCycle);
                if (ImGui.checkbox("启用昼夜循环", enableDayCycle)) {
                    if (setBooleanProperty(env, "dayCycle", "setDayCycle", enableDayCycle.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[ProjectEnvironmentPropertyAdapter] 昼夜循环已更新: {}", enableDayCycle.get());
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("自动模拟时间流逝");
                }
                
                // 时间（Minecraft ticks）
                int currentTime = getIntProperty(env, "timeOfDay", "getTimeOfDay", 6000);
                int[] timeOfDay = {currentTime};
                if (ImGui.dragInt("时间 (ticks)", timeOfDay, 100.0f, 0, 24000)) {
                    if (setIntProperty(env, "timeOfDay", "setTimeOfDay", timeOfDay[0])) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[ProjectEnvironmentPropertyAdapter] 时间已更新: {}", timeOfDay[0]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("Minecraft 时间（0-24000，6000=正午，18000=午夜）");
                }
                
                // 时间预设
                UITheme.spacingSmall();
                UITheme.label("快速设置:", UITheme.DarkTheme.TEXT);
                
                for (int i = 0; i < TIME_PRESETS.length; i++) {
                    ImGui.pushID(i);
                    if (ImGui.button(TIME_PRESET_NAMES[i], 100, UITheme.Spacing.BUTTON_HEIGHT)) {
                        if (setIntProperty(env, "timeOfDay", "setTimeOfDay", TIME_PRESETS[i])) {
                            modifiedRef[0] = true;
                            LOGGER.debug("[ProjectEnvironmentPropertyAdapter] 时间预设已应用: {}", 
                                TIME_PRESET_NAMES[i]);
                        }
                    }
                    if ((i + 1) % 2 != 0 && i < TIME_PRESETS.length - 1) {
                        ImGui.sameLine();
                    }
                    ImGui.popID();
                }
            }
            
            // ========== 渲染控制 ==========
            if (ImGui.collapsingHeader("🎨 渲染控制")) {
                // 启用阴影
                boolean currentShadows = getBooleanProperty(env, "shadowsEnabled", "isShadowsEnabled", true);
                ImBoolean enableShadows = new ImBoolean(currentShadows);
                if (ImGui.checkbox("启用阴影", enableShadows)) {
                    if (setBooleanProperty(env, "shadowsEnabled", "setShadowsEnabled", enableShadows.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[ProjectEnvironmentPropertyAdapter] 阴影已更新: {}", enableShadows.get());
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("渲染方块阴影");
                }
                
                // 抗锯齿
                boolean currentAA = getBooleanProperty(env, "antiAliasing", "isAntiAliasing", false);
                ImBoolean enableAA = new ImBoolean(currentAA);
                if (ImGui.checkbox("抗锯齿 (FXAA)", enableAA)) {
                    if (setBooleanProperty(env, "antiAliasing", "setAntiAliasing", enableAA.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[ProjectEnvironmentPropertyAdapter] 抗锯齿已更新: {}", enableAA.get());
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("平滑锯齿边缘");
                }
                
                // 后处理
                boolean currentPostProcess = getBooleanProperty(env, "postProcessing", "isPostProcessing", false);
                ImBoolean enablePostProcess = new ImBoolean(currentPostProcess);
                if (ImGui.checkbox("后处理效果", enablePostProcess)) {
                    if (setBooleanProperty(env, "postProcessing", "setPostProcessing", enablePostProcess.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[ProjectEnvironmentPropertyAdapter] 后处理已更新: {}", enablePostProcess.get());
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("启用屏幕空间效果（泛光、景深等）");
                }
            }
            
            // ========== 可视层控制 ==========
            if (ImGui.collapsingHeader("🪟 可视层控制")) {
                // 显示透明方块
                boolean currentTransparent = getBooleanProperty(env, "showTransparentBlocks", "isShowTransparentBlocks", true);
                ImBoolean showTransparent = new ImBoolean(currentTransparent);
                if (ImGui.checkbox("显示透明方块", showTransparent)) {
                    if (setBooleanProperty(env, "showTransparentBlocks", "setShowTransparentBlocks", showTransparent.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[ProjectEnvironmentPropertyAdapter] 透明方块显示已更新: {}", showTransparent.get());
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("渲染玻璃、水等透明方块");
                }
                
                // 显示实体
                boolean currentEntities = getBooleanProperty(env, "showEntities", "isShowEntities", true);
                ImBoolean showEntities = new ImBoolean(currentEntities);
                if (ImGui.checkbox("显示实体模型", showEntities)) {
                    if (setBooleanProperty(env, "showEntities", "setShowEntities", showEntities.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[ProjectEnvironmentPropertyAdapter] 实体显示已更新: {}", showEntities.get());
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("渲染场景中的实体");
                }
                
                // 显示粒子
                boolean currentParticles = getBooleanProperty(env, "showParticles", "isShowParticles", true);
                ImBoolean showParticles = new ImBoolean(currentParticles);
                if (ImGui.checkbox("显示粒子特效", showParticles)) {
                    if (setBooleanProperty(env, "showParticles", "setShowParticles", showParticles.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[ProjectEnvironmentPropertyAdapter] 粒子显示已更新: {}", showParticles.get());
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("渲染粒子效果");
                }
                
                // 显示网格
                boolean currentGrid = getBooleanProperty(env, "showGrid", "isShowGrid", false);
                ImBoolean showGrid = new ImBoolean(currentGrid);
                if (ImGui.checkbox("显示网格", showGrid)) {
                    if (setBooleanProperty(env, "showGrid", "setShowGrid", showGrid.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[ProjectEnvironmentPropertyAdapter] 网格显示已更新: {}", showGrid.get());
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("显示辅助网格线");
                }
            }
            
            // ========== 环境预设 ==========
            if (ImGui.collapsingHeader("🌈 环境预设")) {
                UITheme.label("快速应用环境预设:", UITheme.DarkTheme.TEXT);
                UITheme.spacingSmall();
                
                for (int i = 0; i < ENV_PRESETS.length; i++) {
                    ImGui.pushID("preset_" + i);
                    if (ImGui.button(ENV_PRESETS[i], 140, UITheme.Spacing.BUTTON_HEIGHT)) {
                        applyEnvironmentPreset(env, i);
                        modifiedRef[0] = true;
                        LOGGER.info("[ProjectEnvironmentPropertyAdapter] 应用环境预设: {}", ENV_PRESETS[i]);
                    }
                    if ((i + 1) % 2 != 0 && i < ENV_PRESETS.length - 1) {
                        ImGui.sameLine();
                    }
                    ImGui.popID();
                }
            }
            
            UITheme.separator();
            
            // ========== 快捷操作 ==========
            UITheme.beginGroup("快捷操作");
            
            if (ImGui.button("🔄 重置为默认", 120, UITheme.Spacing.BUTTON_HEIGHT)) {
                if (callMethod(env, "resetToDefault")) {
                    modifiedRef[0] = true;
                    LOGGER.info("[ProjectEnvironmentPropertyAdapter] 环境已重置为默认值");
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("将所有环境参数重置为默认值");
            }
            
            ImGui.sameLine();
            
            if (ImGui.button("📋 复制环境", 120, UITheme.Spacing.BUTTON_HEIGHT)) {
                LOGGER.info("[ProjectEnvironmentPropertyAdapter] 复制环境设置");
                // TODO: 复制到剪贴板
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("复制当前环境设置");
            }
            
            ImGui.sameLine();
            
            if (ImGui.button("📥 导入环境", 120, UITheme.Spacing.BUTTON_HEIGHT)) {
                LOGGER.info("[ProjectEnvironmentPropertyAdapter] 导入环境设置");
                // TODO: 从文件导入
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("从文件导入环境设置");
            }
            
            UITheme.endGroup();
            
        } catch (Exception e) {
            LOGGER.error("[ProjectEnvironmentPropertyAdapter] 渲染环境属性时出错", e);
            UITheme.label("渲染出错: " + e.getMessage(), UITheme.DarkTheme.TEXT_DISABLED);
        }
        
        return modifiedRef[0];
    }
    
    /**
     * 应用环境预设
     */
    private void applyEnvironmentPreset(Object env, int presetIndex) {
        switch (presetIndex) {
            case 0 -> { // ☀️ 白天
                setFloatArrayProperty(env, "ambientColor", "setAmbientColor", 
                    new float[]{1.0f, 1.0f, 0.9f, 1.0f});
                setFloatArrayProperty(env, "skyColor", "setSkyColor", 
                    new float[]{0.5f, 0.7f, 1.0f, 1.0f});
                setFloatProperty(env, "exposure", "setExposure", 1.2f);
                setIntProperty(env, "timeOfDay", "setTimeOfDay", 6000);
            }
            case 1 -> { // 🌅 黄昏
                setFloatArrayProperty(env, "ambientColor", "setAmbientColor", 
                    new float[]{1.0f, 0.7f, 0.5f, 1.0f});
                setFloatArrayProperty(env, "skyColor", "setSkyColor", 
                    new float[]{1.0f, 0.5f, 0.3f, 1.0f});
                setFloatProperty(env, "exposure", "setExposure", 0.9f);
                setIntProperty(env, "timeOfDay", "setTimeOfDay", 12000);
            }
            case 2 -> { // 🌙 夜晚
                setFloatArrayProperty(env, "ambientColor", "setAmbientColor", 
                    new float[]{0.2f, 0.2f, 0.3f, 1.0f});
                setFloatArrayProperty(env, "skyColor", "setSkyColor", 
                    new float[]{0.1f, 0.1f, 0.2f, 1.0f});
                setFloatProperty(env, "exposure", "setExposure", 0.5f);
                setIntProperty(env, "timeOfDay", "setTimeOfDay", 18000);
            }
            case 3 -> { // ☁️ 阴天
                setFloatArrayProperty(env, "ambientColor", "setAmbientColor", 
                    new float[]{0.6f, 0.6f, 0.6f, 1.0f});
                setFloatArrayProperty(env, "skyColor", "setSkyColor", 
                    new float[]{0.5f, 0.5f, 0.5f, 1.0f});
                setFloatProperty(env, "exposure", "setExposure", 0.8f);
            }
            case 4 -> { // 🌫️ 雾天
                setFloatArrayProperty(env, "ambientColor", "setAmbientColor", 
                    new float[]{0.7f, 0.7f, 0.7f, 1.0f});
                setFloatProperty(env, "fogDensity", "setFogDensity", 0.5f);
                setFloatProperty(env, "exposure", "setExposure", 0.7f);
            }
            case 5 -> { // 🌧️ 雨天
                setFloatArrayProperty(env, "ambientColor", "setAmbientColor", 
                    new float[]{0.5f, 0.5f, 0.6f, 1.0f});
                setFloatArrayProperty(env, "skyColor", "setSkyColor", 
                    new float[]{0.4f, 0.4f, 0.5f, 1.0f});
                setBooleanProperty(env, "weatherRain", "setWeatherRain", true);
                setFloatProperty(env, "fogDensity", "setFogDensity", 0.3f);
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
    
    private float[] getFloatArrayProperty(Object obj, String fieldName, String getterName) {
        try {
            var method = obj.getClass().getMethod(getterName);
            Object result = method.invoke(obj);
            return result instanceof float[] ? (float[]) result : null;
        } catch (Exception e) {
            try {
                var field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object result = field.get(obj);
                return result instanceof float[] ? (float[]) result : null;
            } catch (Exception ex) {
                return null;
            }
        }
    }
    
    private boolean setFloatArrayProperty(Object obj, String fieldName, String setterName, float[] value) {
        try {
            var method = obj.getClass().getMethod(setterName, float[].class);
            method.invoke(obj, (Object) value);
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
    
    /**
     * 调用无参方法
     */
    private boolean callMethod(Object obj, String methodName) {
        try {
            var method = obj.getClass().getMethod(methodName);
            method.invoke(obj);
            return true;
        } catch (Exception e) {
            LOGGER.debug("[ProjectEnvironmentPropertyAdapter] 调用方法失败: {}", methodName);
            return false;
        }
    }

    @Override
    public Class<Object> getTargetType() {
        return Object.class;
    }
    
    @Override
    public String getAdapterName() {
        return "项目环境属性编辑器";
    }
    
    @Override
    public boolean supports(Object target) {
        if (target == null) return false;
        
        String className = target.getClass().getSimpleName().toLowerCase();
        return className.contains("environment") || 
               className.contains("projectenv");
    }
}

