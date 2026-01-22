package com.chronoblocks.ui.input;

import imgui.ImGui;
import imgui.ImGuiIO;

/**
 * 快捷键上下文系统 - 判断快捷键是否应该在当前上下文中生效
 * 
 * <p>职责：
 * <ul>
 *   <li>判断当前是否允许执行全局快捷键</li>
 *   <li>避免在文本输入等场景中误触发快捷键</li>
 *   <li>支持未来扩展（不同上下文下的快捷键）</li>
 * </ul>
 * 
 * <p>设计原则：
 * <ul>
 *   <li>只做判断，不执行任何操作</li>
 *   <li>保持简单，避免过度复杂化</li>
 *   <li>支持未来扩展</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public final class ShortcutContext {
    
    private ShortcutContext() {
        throw new UnsupportedOperationException("ShortcutContext 是工具类，不能实例化");
    }
    
    /**
     * 检查当前是否处于全局快捷键上下文
     * 
     * <p>v1 实现：永远允许（简化版本）
     * <p>v2 扩展：可以判断是否在文本输入、是否聚焦特定面板等
     * 
     * @return true 如果允许执行全局快捷键，false 如果不允许
     */
    public static boolean isGlobalContext() {
        // v1：永远允许
        // 未来可以扩展为：
        // - 检查是否在文本输入框（ImGui.isAnyItemActive()）
        // - 检查是否聚焦特定面板
        // - 检查是否在特定编辑模式
        return true;
    }
    
    /**
     * 检查是否在文本输入上下文中（不应触发快捷键）
     * 
     * @param io ImGui IO 对象
     * @return true 如果在文本输入中，false 如果不在
     */
    public static boolean isTextInputContext(ImGuiIO io) {
        if (io == null) {
            return false;
        }
        // 检查是否有任何输入框处于活动状态
        return ImGui.isAnyItemActive() && ImGui.isAnyItemFocused();
    }
    
    /**
     * 检查是否应该处理快捷键
     * 
     * @param io ImGui IO 对象
     * @return true 如果应该处理，false 如果不应该
     */
    public static boolean shouldHandleShortcut(ImGuiIO io) {
        // 如果不在全局上下文，不处理
        if (!isGlobalContext()) {
            return false;
        }
        // 如果在文本输入中，不处理
        return !isTextInputContext(io);
    }
}