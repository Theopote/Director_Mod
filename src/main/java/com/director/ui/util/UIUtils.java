package com.director.ui.util;

import imgui.ImGui;
import imgui.type.ImString;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

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
 * @author Director Team
 * @version 1.0
 */
public final class UIUtils {

    private static final Field IM_STRING_INPUT_SIZE_FIELD = resolveImStringInputSizeField();

    /**
     * ImGui/ImString 的容量单位是 UTF-8 字节而不是 Java 字符数。
     * 这里统一按最坏 4 bytes/code point 预留，避免中文/emoji 等多字节输入被过早截断。
     */
    public static final int DEFAULT_NAME_MAX_CHARACTERS = 256;
    public static final int DEFAULT_DESCRIPTION_MAX_CHARACTERS = 1024;

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

    /**
     * 将“最大字符数”转换为 ImString 需要的缓冲区字节数。
     */
    public static int inputBufferBytes(int maxCharacters) {
        if (maxCharacters <= 0) {
            return 32;
        }
        return Math.max(32, maxCharacters * 4 + 1);
    }

    /**
     * 创建一个按“字符数”配置的 ImGui 文本输入缓冲区。
     */
    public static ImString createInputBuffer(int maxCharacters) {
        int initialBytes = inputBufferBytes(maxCharacters);
        return new ImString(initialBytes);
    }

    /**
     * Reads the complete NUL-terminated UTF-8 contents from an ImString buffer.
     *
     * <p>imgui-java's ImString#get() depends on InputData.size. In some inputText
     * paths that size can remain at the original text length even though the
     * native byte buffer contains more text, which truncates multibyte input on
     * commit. Reading to the first NUL byte matches how ImGui treats the buffer.
     */
    public static String readInputBuffer(ImString buffer) {
        if (buffer == null) {
            return "";
        }

        byte[] data = buffer.getData();
        int length = 0;
        while (length < data.length && data[length] != 0) {
            length++;
        }
        if (length == 0) {
            return "";
        }
        return new String(data, 0, length, StandardCharsets.UTF_8);
    }

    /**
     * Forces imgui-java's internal input size metadata to the byte buffer capacity.
     *
     * <p>The native inputText bridge in imgui-java 1.86 can behave as if
     * InputData.size is the writable buffer size. ImString#set(...) stores the
     * current text length there, which makes editing stop at the old name length.
     * The actual text is still NUL-terminated in the byte array, so expanding this
     * metadata before inputText gives the native side the real capacity.
     */
    public static void exposeInputBufferCapacity(ImString buffer) {
        if (buffer == null || IM_STRING_INPUT_SIZE_FIELD == null) {
            return;
        }
        try {
            IM_STRING_INPUT_SIZE_FIELD.setInt(buffer.inputData, Math.max(0, buffer.getData().length - 1));
        } catch (IllegalAccessException ignored) {
            // Leave the buffer unchanged if this imgui-java build changes internals.
        }
    }

    private static Field resolveImStringInputSizeField() {
        try {
            Field field = Class.forName("imgui.type.ImString$InputData").getDeclaredField("size");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
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
