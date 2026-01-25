package com.chronoblocks.ui.imgui;

import imgui.ImGui;

/**
 * ImGui工具类 - 提供安全的ImGui资源管理
 * <p>
 * 使用try-with-resources模式确保ImGui的begin/end调用总是匹配，
 * 防止状态泄漏和资源管理问题。
 * 
 * <h3>核心功能：</h3>
 * <ul>
 *   <li><strong>窗口管理</strong> - 自动管理ImGui窗口的begin/end调用</li>
 *   <li><strong>样式管理</strong> - 自动管理样式变量的push/pop调用</li>
 *   <li><strong>异常安全</strong> - 确保即使发生异常也能正确清理资源</li>
 *   <li><strong>资源管理</strong> - 使用AutoCloseable接口确保资源释放</li>
 * </ul>
 * 
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 窗口管理
 * try (var window = ImGuiUtils.beginWindow("MyWindow", ImGuiWindowFlags.None)) {
 *     if (window != null) {
 *         ImGui.text("Hello World");
 *     }
 * }
 * 
 * // 样式管理
 * try (var style = ImGuiUtils.pushStyle()) {
 *     style.setColor(ImGuiCol.WindowBg, 1.0f, 0.0f, 0.0f, 1.0f);
 *     // 样式会自动恢复
 * }
 * }</pre>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 * @since 1.0
 */
public final class ImGuiUtils {
    
    /**
     * ImGui窗口资源管理器
     */
    @FunctionalInterface
    public interface ImGuiWindow extends AutoCloseable {
        @Override
        void close(); // 无需抛出异常
    }
    
    /**
     * ImGui样式资源管理器
     */
    public interface ImGuiStyle extends AutoCloseable {
        /**
         * 设置颜色样式
         * @param colorIndex 颜色索引
         * @param r 红色分量
         * @param g 绿色分量
         * @param b 蓝色分量
         * @param a 透明度分量
         */
        void setColor(int colorIndex, float r, float g, float b, float a);
        
        /**
         * 设置变量样式
         * @param varIndex 变量索引
         * @param value 值
         */
        void setVar(int varIndex, float value);
        
        /**
         * 设置变量样式（两个值）
         * @param varIndex 变量索引
         * @param value1 第一个值
         * @param value2 第二个值
         */
        void setVar(int varIndex, float value1, float value2);
        
        @Override
        void close(); // 无需抛出异常
    }
    
    /**
     * 开始ImGui窗口，返回资源管理器
     * @param name 窗口名称
     * @param flags 窗口标志
     * @return 窗口资源管理器，如果窗口未能开始则返回null
     */
    public static ImGuiWindow beginWindow(String name, int flags) {
        boolean started = ImGui.begin(name, flags);
        if (started) {
            return ImGui::end;
        }
        return null;
    }

    /**
     * 创建带计数器的样式资源管理器
     * @return 样式资源管理器
     */
    public static ImGuiStyle pushStyleWithCounter() {
        return new ImGuiStyle() {
            private boolean closed = false;
            private int colorCount = 0;
            private int varCount = 0;
            
            @Override
            public void setColor(int colorIndex, float r, float g, float b, float a) {
                if (!closed) {
                    ImGui.pushStyleColor(colorIndex, r, g, b, a);
                    colorCount++;
                }
            }
            
            @Override
            public void setVar(int varIndex, float value) {
                if (!closed) {
                    ImGui.pushStyleVar(varIndex, value);
                    varCount++;
                }
            }
            
            @Override
            public void setVar(int varIndex, float value1, float value2) {
                if (!closed) {
                    ImGui.pushStyleVar(varIndex, value1, value2);
                    varCount++;
                }
            }
            
            @Override
            public void close() {
                if (!closed) {
                    // 按相反顺序pop
                    for (int i = 0; i < varCount; i++) {
                        ImGui.popStyleVar();
                    }
                    for (int i = 0; i < colorCount; i++) {
                        ImGui.popStyleColor();
                    }
                    closed = true;
                }
            }
        };
    }
}
