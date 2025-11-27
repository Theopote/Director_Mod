package com.chronoblocks.core.animation.transform.ui;

import java.lang.reflect.Method;

/**
 * Transformer UI 渲染辅助类
 * 
 * <p>提供反射方式访问 UI 框架的方法，避免 core 包直接依赖 UI 包。
 * 如果 UI 类不可用，方法会安全地返回默认值。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class UIHelper {
    
    private static Class<?> uiThemeClass;
    private static Class<?> imGuiClass;
    private static Object darkTheme;
    private static Integer textSelectedColor;
    private static boolean initialized = false;
    
    private static void initialize() {
        if (initialized) return;
        
        try {
            uiThemeClass = Class.forName("com.chronoblocks.ui.theme.UITheme");
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
            // 更新值（无论是否被修改，都要获取当前值）
            Method getMethod = imBooleanClass.getMethod("get");
            value[0] = (Boolean) getMethod.invoke(imBoolean);
            return result;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 渲染下拉框
     */
    public static boolean combo(String label, int[] currentIndex, String[] items) {
        initialize();
        if (!initialized || imGuiClass == null) return false;
        
        try {
            Class<?> imIntClass = Class.forName("imgui.type.ImInt");
            Object imInt = imIntClass.getConstructor(int.class).newInstance(currentIndex[0]);
            Method method = imGuiClass.getMethod("combo", String.class, imIntClass, String[].class);
            boolean result = (Boolean) method.invoke(null, label, imInt, items);
            if (result) {
                Method getMethod = imIntClass.getMethod("get");
                currentIndex[0] = (Integer) getMethod.invoke(imInt);
            }
            return result;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 添加小间距
     */
    public static void spacingSmall() {
        initialize();
        if (!initialized || uiThemeClass == null) return;
        
        try {
            Method method = uiThemeClass.getMethod("spacingSmall");
            method.invoke(null);
        } catch (Exception e) {
            // 忽略错误
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
     * 检查 UI 类是否可用
     */
    public static boolean isUIAvailable() {
        initialize();
        return initialized;
    }
}

