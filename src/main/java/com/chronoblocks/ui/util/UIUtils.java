package com.chronoblocks.ui.util;

import imgui.ImGui;

/**
 * UI 工具类 (UIUtils)
 * <p>
 * 提供一组静态的、通用的辅助方法，用于处理 ImGui 渲染、数学计算和字符串清理等常见任务。
 * 这个类的目的是整合重复代码，提高代码的可维护性和复用性。
 * </p>
 * <p>
 * 该类被声明为 {@code final} 并拥有一个私有构造函数，以防止被继承或实例化。
 * </p>
 *
 * @author ChronoBlocks Team
 * @version 1.0
 */
public final class UIUtils {

    /**
     * 私有构造函数，防止该工具类被实例化。
     */
    private UIUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    // ========== 颜色处理 ==========

    /**
     * 将 RGBA 分量转换为 ImGui 使用的整数颜色格式 (ABGR)。
     *
     * @param r 红色分量 (0-255)
     * @param g 绿色分量 (0-255)
     * @param b 蓝色分量 (0-255)
     * @param a 透明度分量 (0-255)
     * @return 整数表示的 ABGR 颜色值
     */
    public static int rgba(int r, int g, int b, int a) {
        return ((a & 0xFF) << 24) | ((b & 0xFF) << 16) | ((g & 0xFF) << 8) | (r & 0xFF);
    }

    /**
     * 调整指定颜色的透明度。
     *
     * @param color    原始整数颜色值 (ABGR)
     * @param newAlpha 新的透明度分量 (0-255)
     * @return 修改透明度后的新颜色值
     */
    public static int adjustAlpha(int color, int newAlpha) {
        return (color & 0x00FFFFFF) | ((newAlpha & 0xFF) << 24);
    }

    // ========== 数学计算 ==========

    /**
     * 将一个浮点数值限制在指定的最小和最大值之间。
     *
     * @param value 要限制的值
     * @param min   允许的最小值
     * @param max   允许的最大值
     * @return 被限制在 [min, max] 范围内的值
     */
    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 将一个整数值限制在指定的最小和最大值之间。
     *
     * @param value 要限制的值
     * @param min   允许的最小值
     * @param max   允许的最大值
     * @return 被限制在 [min, max] 范围内的值
     */
    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // ========== ImGui 交互 ==========

    /**
     * 检查当前鼠标光标是否悬停在指定的矩形区域内。
     *
     * @param x1 矩形左上角的 X 坐标
     * @param y1 矩形左上角的 Y 坐标
     * @param x2 矩形右下角的 X 坐标
     * @param y2 矩形右下角的 Y 坐标
     * @return 如果鼠标在矩形内，则返回 true；否则返回 false。
     */
    public static boolean isMouseHoveringRect(float x1, float y1, float x2, float y2) {
        float mx = ImGui.getMousePosX();
        float my = ImGui.getMousePosY();
        // 确保 x1 <= x2 和 y1 <= y2
        float minX = Math.min(x1, x2);
        float maxX = Math.max(x1, x2);
        float minY = Math.min(y1, y2);
        float maxY = Math.max(y1, y2);
        return mx >= minX && mx <= maxX && my >= minY && my <= maxY;
    }

    // ========== 字符串处理 ==========

    /**
     * 清理字符串，用于处理来自 ImGui 输入框的数据。
     * <p>
     * 该方法执行以下操作：
     * <ul>
     *   <li>处理 ImString 可能包含的 null 终止符。</li>
     *   <li>移除所有 ASCII 控制字符（码点 < 32）。</li>
     *   <li>去除字符串首尾的空白字符。</li>
     * </ul>
     *
     * @param input 可能包含垃圾字符的原始字符串。
     * @return 清理后的干净字符串，如果输入为 null 则返回空字符串。
     */
    public static String cleanString(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        // ImString 的缓冲区可能包含 null 字符 ('\0')，在此处截断
        int nullIndex = input.indexOf('\0');
        String effectiveInput = (nullIndex >= 0) ? input.substring(0, nullIndex) : input;

        // 移除所有控制字符（例如退格、换行等），但保留有效的多语言字符
        return effectiveInput.replaceAll("\\p{Cntrl}", "").trim();
    }
}