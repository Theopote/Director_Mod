package com.chronoblocks.ui.adapters.impl;

import com.chronoblocks.ui.adapters.BasePropertyAdapter;

/**
 * ✨ EffectAdapter - 特效属性适配器
 *
 * <p>为 ChronoBlocks 特效对象提供通用属性编辑器。
 *
 * <h3>✨ 支持的属性：</h3>
 * <ul>
 *     <li>📝 基础信息：特效名称、类型、启用状态</li>
 *     <li>⏱ 时间控制：开始时间、持续时间、强度</li>
 *     <li>🎨 视觉属性：颜色、不透明度、大小</li>
 *     <li>⚙️ 特效参数：特效特有的参数</li>
 * </ul>
 *
 * <h3>📖 使用说明：</h3>
 * <p>此适配器使用反射机制，支持任何包含 "Effect" 或 "Particle" 名称的对象。
 * 当具体的 Effect 类定义完成后，可以替换为类型安全的实现。
 *
 * @author ChronoBlocks Team
 * @version 4.5
 * @since 4.5
 */
public class EffectAdapter extends BasePropertyAdapter<Object> {

    @Override
    protected boolean doRenderProperties(Object effect) {
        boolean changed = false;

        // ========== 基础信息 ==========
        renderSectionHeader("📝 基础信息");
        
        // 特效名称
        String name = getStringProperty(effect, "name", "getName");
        if (name != null) {
            changed |= renderTextProperty("特效名称",
                    () -> name,
                    value -> setStringProperty(effect, value),
                    128);
        } else {
            renderReadOnlyText("特效名称", "未命名特效");
        }

        // 启用状态
        Boolean enabled = getBoolProperty(effect, "enabled", "isEnabled");
        if (enabled != null) {
            changed |= renderBoolProperty("启用",
                    () -> enabled,
                    value -> setBoolProperty(effect, "enabled", "setEnabled", value));
        }

        // 特效类型
        String type = getStringProperty(effect, "type", "getType");
        if (type != null) {
            renderReadOnlyText("类型", type);
        }

        // ========== 时间控制 ==========
        renderSectionHeader("⏱ 时间控制");
        
        // 开始时间
        Float startTime = getFloatProperty(effect, "startTime", "getStartTime");
        if (startTime != null) {
            changed |= renderFloatProperty("开始时间 (秒)",
                    () -> startTime,
                    value -> setFloatProperty(effect, "startTime", "setStartTime", value),
                    0.01f, 0.0f, Float.MAX_VALUE);
        }

        // 持续时间
        Float duration = getFloatProperty(effect, "duration", "getDuration");
        if (duration != null) {
            changed |= renderFloatProperty("持续时间 (秒)",
                    () -> duration,
                    value -> setFloatProperty(effect, "duration", "setDuration", value),
                    0.1f, 0.1f, 60.0f);
        }

        // 强度
        Float intensity = getFloatProperty(effect, "intensity", "getIntensity");
        if (intensity != null) {
            changed |= renderFloatProperty("强度",
                    () -> intensity,
                    value -> setFloatProperty(effect, "intensity", "setIntensity", value),
                    0.01f, 0.0f, 1.0f);
        }

        // ========== 视觉属性 ==========
        renderSectionHeader("🎨 视觉属性");
        
        // 颜色
        float[] color = getFloatArrayProperty(effect);
        if (color != null && color.length >= 3) {
            if (color.length == 3) {
                changed |= renderColorProperty(
                        () -> color,
                        value -> setFloatArrayProperty(effect, value));
            } else {
                changed |= renderColorAlphaProperty(
                        () -> color,
                        value -> setFloatArrayProperty(effect, value));
            }
        }

        // 不透明度
        Float opacity = getFloatProperty(effect, "opacity", "getOpacity");
        if (opacity != null) {
            changed |= renderFloatProperty("不透明度",
                    () -> opacity,
                    value -> setFloatProperty(effect, "opacity", "setOpacity", value),
                    0.01f, 0.0f, 1.0f);
        }

        // 大小/缩放
        Float scale = getFloatProperty(effect, "scale", "getScale");
        if (scale != null) {
            changed |= renderFloatProperty("大小",
                    () -> scale,
                    value -> setFloatProperty(effect, "scale", "setScale", value),
                    0.1f, 0.1f, 10.0f);
        }

        // ========== 特效参数 ==========
        renderEffectSpecificParameters(effect);

        // ========== 播放控制 ==========
        renderSectionHeader("🔄 播放控制");
        
        // 循环
        Boolean loop = getBoolProperty(effect, "loop", "isLoop");
        if (loop != null) {
            changed |= renderBoolProperty("循环播放",
                    () -> loop,
                    value -> setBoolProperty(effect, "loop", "setLoop", value));
        }

        // 淡入时间
        Float fadeIn = getFloatProperty(effect, "fadeIn", "getFadeIn");
        if (fadeIn != null) {
            changed |= renderFloatProperty("淡入时间 (秒)",
                    () -> fadeIn,
                    value -> setFloatProperty(effect, "fadeIn", "setFadeIn", value),
                    0.1f, 0.0f, 5.0f);
        }

        // 淡出时间
        Float fadeOut = getFloatProperty(effect, "fadeOut", "getFadeOut");
        if (fadeOut != null) {
            changed |= renderFloatProperty("淡出时间 (秒)",
                    () -> fadeOut,
                    value -> setFloatProperty(effect, "fadeOut", "setFadeOut", value),
                    0.1f, 0.0f, 5.0f);
        }

        return changed;
    }

    /**
     * 渲染特效特有参数
     */
    private void renderEffectSpecificParameters(Object effect) {
        String type = getStringProperty(effect, "type", "getType");
        if (type == null) {
            return;
        }

        renderSectionHeader("⚙️ 特效参数");
        
        // 根据类型显示不同的参数
        if (type.toLowerCase().contains("particle") || type.toLowerCase().contains("粒子")) {
            renderParticleParameters(effect);
        } else if (type.toLowerCase().contains("light") || type.toLowerCase().contains("光")) {
            renderLightParameters(effect);
        } else if (type.toLowerCase().contains("shake") || type.toLowerCase().contains("抖动")) {
            renderShakeParameters(effect);
        } else {
            imgui.ImGui.textDisabled("无特定参数");
        }
    }

    /**
     * 渲染粒子特效参数
     */
    private void renderParticleParameters(Object effect) {
        // 粒子数量
        Integer particleCount = getIntProperty(effect);
        if (particleCount != null) {
            renderReadOnlyText("粒子数量", String.valueOf(particleCount));
        }

        // 发射速率
        Float emissionRate = getFloatProperty(effect, "emissionRate", "getEmissionRate");
        if (emissionRate != null) {
            renderReadOnlyText("发射速率", String.format("%.1f/秒", emissionRate));
        }

        // 粒子生命周期
        Float lifetime = getFloatProperty(effect, "lifetime", "getLifetime");
        if (lifetime != null) {
            renderReadOnlyText("生命周期", String.format("%.2f秒", lifetime));
        }
    }

    /**
     * 渲染光照特效参数
     */
    private void renderLightParameters(Object effect) {
        // 光照范围
        Float range = getFloatProperty(effect, "range", "getRange");
        if (range != null) {
            renderReadOnlyText("光照范围", String.format("%.1f", range));
        }

        // 亮度
        Float brightness = getFloatProperty(effect, "brightness", "getBrightness");
        if (brightness != null) {
            renderReadOnlyText("亮度", String.format("%.2f", brightness));
        }
    }

    /**
     * 渲染镜头抖动参数
     */
    private void renderShakeParameters(Object effect) {
        // 振幅
        Float amplitude = getFloatProperty(effect, "amplitude", "getAmplitude");
        if (amplitude != null) {
            renderReadOnlyText("振幅", String.format("%.2f", amplitude));
        }

        // 频率
        Float frequency = getFloatProperty(effect, "frequency", "getFrequency");
        if (frequency != null) {
            renderReadOnlyText("频率", String.format("%.1f Hz", frequency));
        }
    }

    // ==================== 反射辅助方法 ====================

    private String getStringProperty(Object obj, String fieldName, String getterName) {
        try {
            var method = obj.getClass().getMethod(getterName);
            Object result = method.invoke(obj);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void setStringProperty(Object obj, String value) {
        try {
            var method = obj.getClass().getMethod("setName", String.class);
            method.invoke(obj, value);
        } catch (Exception e) {
            logger.trace("[EffectAdapter] 无法设置字符串属性: {}", "name");
        }
    }

    private Float getFloatProperty(Object obj, String fieldName, String getterName) {
        try {
            var method = obj.getClass().getMethod(getterName);
            Object result = method.invoke(obj);
            if (result instanceof Float) {
                return (Float) result;
            } else if (result instanceof Number) {
                return ((Number) result).floatValue();
            }
        } catch (Exception e) {
            // 忽略
        }
        return null;
    }

    private void setFloatProperty(Object obj, String fieldName, String setterName, float value) {
        try {
            var method = obj.getClass().getMethod(setterName, float.class);
            method.invoke(obj, value);
        } catch (Exception e) {
            logger.trace("[EffectAdapter] 无法设置浮点属性: {}", fieldName);
        }
    }

    private Boolean getBoolProperty(Object obj, String fieldName, String getterName) {
        try {
            var method = obj.getClass().getMethod(getterName);
            Object result = method.invoke(obj);
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
        } catch (Exception e) {
            // 忽略
        }
        return null;
    }

    private void setBoolProperty(Object obj, String fieldName, String setterName, boolean value) {
        try {
            var method = obj.getClass().getMethod(setterName, boolean.class);
            method.invoke(obj, value);
        } catch (Exception e) {
            logger.trace("[EffectAdapter] 无法设置布尔属性: {}", fieldName);
        }
    }

    private Integer getIntProperty(Object obj) {
        try {
            var method = obj.getClass().getMethod("getParticleCount");
            Object result = method.invoke(obj);
            if (result instanceof Integer) {
                return (Integer) result;
            } else if (result instanceof Number) {
                return ((Number) result).intValue();
            }
        } catch (Exception e) {
            // 忽略
        }
        return null;
    }

    private float[] getFloatArrayProperty(Object obj) {
        try {
            var method = obj.getClass().getMethod("getColor");
            Object result = method.invoke(obj);
            return result instanceof float[] ? (float[]) result : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void setFloatArrayProperty(Object obj, float[] value) {
        try {
            var method = obj.getClass().getMethod("setColor", float[].class);
            method.invoke(obj, (Object) value);
        } catch (Exception e) {
            logger.trace("[EffectAdapter] 无法设置浮点数组属性: {}", "color");
        }
    }

    @Override
    public void onPropertyChanged(Object effect) {
        super.onPropertyChanged(effect);
        logger.debug("[EffectAdapter] 特效属性已更新");
    }

    @Override
    public boolean validate(Object effect) {
        // 验证开始时间和持续时间
        Float startTime = getFloatProperty(effect, "startTime", "getStartTime");
        if (startTime != null && startTime < 0) {
            logger.warn("[EffectAdapter] 开始时间不能为负: {}", startTime);
            return false;
        }

        Float duration = getFloatProperty(effect, "duration", "getDuration");
        if (duration != null && duration <= 0) {
            logger.warn("[EffectAdapter] 持续时间必须大于0: {}", duration);
            return false;
        }

        return true;
    }

    @Override
    public Class<Object> getTargetType() {
        return Object.class; // 使用 Object 以支持各种特效类型
    }

    @Override
    public boolean supports(Object obj) {
        if (obj == null) {
            return false;
        }
        
        // 检查类名是否包含特效相关关键词
        String className = obj.getClass().getSimpleName().toLowerCase();
        return className.contains("effect") || 
               className.contains("particle") ||
               className.contains("light") ||
               className.contains("shake");
    }

    @Override
    public int getPriority() {
        return 50; // 中等优先级（通用适配器）
    }

    @Override
    public String getDisplayName() {
        return "✨ 特效编辑器";
    }

    @Override
    public String getDescription() {
        return "用于编辑特效的时间、视觉和播放属性";
    }
}

