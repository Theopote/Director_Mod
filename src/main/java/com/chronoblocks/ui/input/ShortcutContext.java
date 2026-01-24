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
 * @author ChronoBlocks Team
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
     * @param panelRegistry 面板注册表（可选，用于检查 TimelinePanel 焦点）
     * @return true 如果应该处理，false 如果不应该
     */
    public static boolean shouldHandleShortcut(ImGuiIO io, com.chronoblocks.ui.di.IPanelRegistry panelRegistry) {
        // 如果在文本输入中，不处理
        if (isTextInputContext(io)) {
            org.slf4j.LoggerFactory.getLogger(ShortcutContext.class)
                .debug("[ShortcutContext] 文本输入中，拒绝快捷键");
            return false;
        }
        
        // v1.1：检查 TimelinePanel 是否聚焦（播放快捷键需要）
        // 如果提供了 panelRegistry，检查 TimelinePanel 焦点
        if (panelRegistry != null) {
            boolean timelineFocused = isTimelinePanelFocused(panelRegistry);
            
            // 添加调试日志
            if (org.slf4j.LoggerFactory.getLogger(ShortcutContext.class).isDebugEnabled()) {
                com.chronoblocks.ui.panels.TimelinePanel timelinePanel = 
                    panelRegistry.getPanel(com.chronoblocks.ui.panels.TimelinePanel.class);
                org.slf4j.LoggerFactory.getLogger(ShortcutContext.class)
                    .debug("[ShortcutContext] TimelinePanel 检查: exists={}, visible={}, focused={}", 
                        timelinePanel != null,
                            timelinePanel != null && timelinePanel.isVisible(),
                        timelineFocused);
            }
            
            // ✅ TimelinePanel 或 ContainerPanel 聚焦时允许处理快捷键
            if (timelineFocused) {
                return true;
            }
            return com.chronoblocks.ui.panels.ContainerPanel.isContainerTreeFocused();
            
            // 如果都未聚焦，不允许处理（避免在其他面板或游戏中误触发）
        }
        
        // 降级方案：如果没有 panelRegistry，使用默认检查（允许所有快捷键）
        // 这确保了向后兼容性
        return isGlobalContext();
    }
    
    /**
     * 检查 TimelinePanel 是否聚焦（通过 PanelRegistry）
     * 
     * <p>v1.1 新增：支持面板焦点检测
     * 
     * @param panelRegistry 面板注册表（用于验证面板存在和获取焦点状态）
     * @return true 如果 TimelinePanel 聚焦，false 如果不聚焦
     */
    private static boolean isTimelinePanelFocused(com.chronoblocks.ui.di.IPanelRegistry panelRegistry) {
        // ⭐ v1.1 实现：使用 TimelinePanel 维护的静态焦点状态
        // TimelinePanel 在 doRender() 中会更新 isTimelinePanelFocused 状态
        try {
            // 首先检查 TimelinePanel 是否存在
            if (panelRegistry == null) {
                return false;
            }
            
            com.chronoblocks.ui.panels.TimelinePanel timelinePanel = 
                panelRegistry.getPanel(com.chronoblocks.ui.panels.TimelinePanel.class);
            if (timelinePanel == null) {
                return false;
            }
            
            // 检查 TimelinePanel 是否可见
            // 如果面板不可见，不应该处理快捷键
            if (!timelinePanel.isVisible()) {
                return false;
            }
            
            // 检查 TimelinePanel 维护的焦点状态
            // 这个状态由 TimelinePanel.doRender() 更新
            // 注意：如果面板未渲染（隐藏），焦点状态可能不会更新
            // 所以我们需要同时检查可见性和焦点状态
            boolean focused = com.chronoblocks.ui.panels.TimelinePanel.isFocused();
            
            // 调试日志（仅在 trace 级别）
            if (org.slf4j.LoggerFactory.getLogger(ShortcutContext.class).isTraceEnabled()) {
                org.slf4j.LoggerFactory.getLogger(ShortcutContext.class)
                    .trace("[ShortcutContext] TimelinePanel 可见性: {}, 焦点状态: {}", 
                        timelinePanel.isVisible(), focused);
            }
            
            return focused;
        } catch (Exception e) {
            // 如果检查失败，记录错误但不抛出异常
            org.slf4j.LoggerFactory.getLogger(ShortcutContext.class)
                .warn("[ShortcutContext] 检查 TimelinePanel 焦点失败", e);
            // 默认不允许（安全起见）
            return false;
        }
    }
}