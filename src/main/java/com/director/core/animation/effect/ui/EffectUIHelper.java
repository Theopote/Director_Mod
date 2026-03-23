package com.director.core.animation.effect.ui;

import java.lang.reflect.Method;

/**
 * 特效 UI 渲染辅助类
 * 
 * <p>提供反射方式访问 UI 框架的方法，避免 core 包直接依赖 UI 包。
 * 如果 UI 类不可用，方法会安全地返回默认值。
 * 
 * <p>参考 {@link com.director.core.animation.transform.ui.UIHelper} 的实现模式。
 * 
 * @author Director Team
 * @version 1.0
 */
public final class EffectUIHelper {
    
    private static Class<?> uiThemeClass;
    private static Class<?> imGuiClass;
    private static Object darkTheme;
    private static Integer textSelectedColor;
    private static boolean initialized = false;
    
    private static void initialize() {
        if (initialized) return;
        
        try {
            uiThemeClass = Class.forName("com.director.ui.theme.UITheme");
            imGuiClass = Class.forName("imgui.ImGui");
            darkTheme = uiThemeClass.getField("DarkTheme").get(null);
            textSelectedColor = darkTheme.getClass().getField("TEXT_SELECTED").getInt(null);
            initialized = true;
        } catch (Exception e) {
            // UI 类不可用，后续调用会返回 false
            initialized = false;
        }
    }
    
    /**
     * 渲染标签
     */
    public static void label(String text, int color) {
        initialize();
        if (!initialized || uiThemeClass == null) return;
        
        try {
            Method method = uiThemeClass.getMethod("label", String.class, int.class);
            method.invoke(null, text, color);
        } catch (Exception e) {
            // 忽略错误
        }
    }
    
    /**
     * 渲染标签（使用默认选中颜色）
     */
    public static void label(String text) {
        initialize();
        if (!initialized || textSelectedColor == null) return;
        label(text, textSelectedColor);
    }
    
    /**
     * 渲染浮点数拖拽控件
     */
    public static boolean dragFloat(String label, float[] value, float speed, float min, float max, String format) {
        initialize();
        if (!initialized || imGuiClass == null) return false;
        
        try {
            Method method = imGuiClass.getMethod("dragFloat", String.class, float[].class, 
                float.class, float.class, float.class, String.class);
            return (Boolean) method.invoke(null, label, value, speed, min, max, format);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 渲染复选框
     */
    public static boolean checkbox(String label, boolean[] value) {
        initialize();
        if (!initialized || imGuiClass == null) return false;
        
        try {
            Class<?> imBooleanClass = Class.forName("imgui.type.ImBoolean");
            Object imBoolean = imBooleanClass.getConstructor(boolean.class).newInstance(value[0]);
            Method method = imGuiClass.getMethod("checkbox", String.class, imBooleanClass);
            boolean result = (Boolean) method.invoke(null, label, imBoolean);
            // 更新值
            Method getMethod = imBooleanClass.getMethod("get");
            value[0] = (Boolean) getMethod.invoke(imBoolean);
            return result;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 显示工具提示
     */
    public static void setTooltip(String text) {
        initialize();
        if (!initialized || imGuiClass == null) return;
        
        try {
            Method method = imGuiClass.getMethod("setTooltip", String.class);
            method.invoke(null, text);
        } catch (Exception e) {
            // 忽略错误
        }
    }
    
    /**
     * 检查是否悬停
     */
    public static boolean isItemHovered() {
        initialize();
        if (!initialized || imGuiClass == null) return false;
        
        try {
            Method method = imGuiClass.getMethod("isItemHovered");
            return (Boolean) method.invoke(null);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 检查 UI 类是否可用
     */
    public static boolean isUIAvailable() {
        initialize();
        return initialized;
    }
}

