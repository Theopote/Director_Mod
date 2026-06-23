package com.director.ui.input;

import imgui.ImGui;
import imgui.ImGuiIO;

/**
 * 快捷键上下文系统 - 判断快捷键是否应该在当前上下文中生效
 * 
 * <p>职责：
 * <ul>
 *   <li>判断当前是否允许执行全局快捷键</li>
 *   <li>避免在文本输入等场景中误触发快捷键</li>
 *   <li>支持面板特定的快捷键上下文（如 TimelinePanel）</li>
 * </ul>
 * 
 * <p>设计原则：
 * <ul>
 *   <li>只做判断，不执行任何操作</li>
 *   <li>保持简单，避免过度复杂化</li>
 *   <li>支持未来扩展</li>
 * </ul>
 * 
 * @author Director Team
 * @version 1.1 - 支持 TimelinePanel 焦点检测
 */
public final class ShortcutContext {
    
    private ShortcutContext() {
        throw new UnsupportedOperationException("ShortcutContext 是工具类，不能实例化");
    }
    
    /**
     * 检查当前是否处于全局快捷键上下文
     * 
     * <p>v1.1 实现：默认允许（降级方案）
     * 
     * @return true 如果允许执行全局快捷键，false 如果不允许
     */
    public static boolean isGlobalContext() {
        // v1.1：默认允许（降级方案）
        // 精确的 TimelinePanel 焦点检查在 shouldHandleShortcut() 中通过 panelRegistry 进行
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
        return shouldHandleShortcut(io, null);
    }
    
    /**
     * 检查是否应该处理快捷键（v1.1 新增：支持面板焦点检测）
     * 
     * @param io ImGui IO 对象
     * @param panelRegistry 面板注册表（保留参数供未来扩展；上下文路由在 {@link ShortcutDispatcher} 内按动作处理）
     * @return true 如果应该处理，false 如果不应该
     */
    public static boolean shouldHandleShortcut(ImGuiIO io, com.director.ui.di.IPanelRegistry panelRegistry) {
        if (isTextInputContext(io)) {
            org.slf4j.LoggerFactory.getLogger(ShortcutContext.class)
                .debug("[ShortcutContext] 文本输入中，拒绝快捷键");
            return false;
        }

        // 非文本输入时允许全局快捷键（工具切换、F1 帮助、Esc 清选区等）。
        // 时间线/容器等“需要焦点”的动作由 ShortcutDispatcher.requiresTimelineFocus() 与各按键路由单独判断，
        // 不能在此处要求 Timeline/Container 焦点，否则在 3D 视口工作时工具栏快捷键（1/V/B/Tab 等）全部失效。
        return true;
    }
}