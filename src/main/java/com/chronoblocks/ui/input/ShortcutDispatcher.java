package com.chronoblocks.ui.input;

import com.chronoblocks.ui.commands.UICommandFacade;
import imgui.ImGui;
import imgui.ImGuiIO;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 快捷键分发器 - 统一的快捷键处理入口
 * 
 * <p>职责：
 * <ul>
 *   <li>接收键盘输入事件</li>
 *   <li>将按键映射到 ShortcutAction</li>
 *   <li>通过 ShortcutActions 分发到 Facade</li>
 *   <li>统一所有快捷键的处理流程</li>
 * </ul>
 * 
 * <p>设计原则：
 * <ul>
 *   <li>Shortcut 是第三个 Facade Client（与 Menu / Toolbar 并列）</li>
 *   <li>只调用 Facade，不直接调用业务类</li>
 *   <li>不维护业务状态</li>
 *   <li>支持未来扩展（宏、自动化等）</li>
 * </ul>
 * 
 * <p>架构地位：
 * <pre>
 * MenuBarPanel     ─┐
 * ToolbarPanel     ├─▶ UICommandFacade ─▶ Engine / Model / Managers
 * ShortcutDispatcher ─┘
 * </pre>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class ShortcutDispatcher {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ShortcutDispatcher.class);
    
    private final UICommandFacade commands;
    private final com.chronoblocks.ui.di.IPanelRegistry panelRegistry;
    
    // ⭐ 防抖机制：防止同一按键在短时间内被重复处理
    // 使用静态变量确保所有实例共享防抖状态（防止多个实例导致防抖失效）
    private static int lastProcessedKey = -1;
    private static long lastProcessedTime = 0;
    private static final long DEBOUNCE_MS = 300; // 300ms 防抖时间（增加到300ms以更好地防止重复触发）
    
    /**
     * 构造函数
     * 
     * @param commands Facade 命令接口
     * @param panelRegistry 面板注册表（用于检查面板焦点）
     */
    public ShortcutDispatcher(UICommandFacade commands, com.chronoblocks.ui.di.IPanelRegistry panelRegistry) {
        this.commands = commands;
        this.panelRegistry = panelRegistry;
        if (commands == null) {
            LOGGER.warn("[ShortcutDispatcher] 传入的 commands 为 null");
        }
    }
    
    /**
     * 处理 GLFW 键盘事件
     * 
     * @param key GLFW 键码
     * @param action GLFW 动作（PRESS, RELEASE, REPEAT）
     * @param modifiers 修饰键（Ctrl, Shift, Alt）
     */
    public void handleGLFWKeyEvent(int key, int action, int modifiers) {
        // 只处理按下事件，忽略释放和重复
        if (action != GLFW.GLFW_PRESS) {
            return;
        }
        
        // ⭐ 防抖机制：防止同一按键在短时间内被重复处理
        // 注意：防抖检查必须在所有其他检查之前，确保即使上下文检查失败也能防止重复触发
        long currentTime = System.currentTimeMillis();
        if (key == lastProcessedKey && (currentTime - lastProcessedTime) < DEBOUNCE_MS) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[ShortcutDispatcher] 按键防抖：忽略重复按键 key={}, 距离上次处理 {}ms", 
                    key, currentTime - lastProcessedTime);
            }
            return;
        }
        
        // ⭐ 添加日志：记录所有按键事件（便于调试）
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[ShortcutDispatcher] 收到按键事件: key={} (GLFW), action=PRESS, modifiers={}", key, modifiers);
        }
        
        // 上下文检查
        ImGuiIO io = ImGui.getIO();
        boolean shouldHandle = ShortcutContext.shouldHandleShortcut(io, panelRegistry);
        if (!shouldHandle) {
            // 添加调试日志（debug 级别，便于排查问题）
            boolean textInput = ShortcutContext.isTextInputContext(io);
            boolean timelineFocused = panelRegistry != null ? com.chronoblocks.ui.panels.TimelinePanel.isFocused() : false;
            boolean timelineVisible = panelRegistry != null && panelRegistry.getPanel(com.chronoblocks.ui.panels.TimelinePanel.class) != null 
                ? panelRegistry.getPanel(com.chronoblocks.ui.panels.TimelinePanel.class).isVisible() : false;
            LOGGER.debug("[ShortcutDispatcher] 快捷键被上下文检查拒绝: key={} (GLFW), textInput={}, timelineVisible={}, timelineFocused={}", 
                key, textInput, timelineVisible, timelineFocused);
            // ⚠️ 注意：即使上下文检查失败，也要更新防抖状态，防止重复触发
            lastProcessedKey = key;
            lastProcessedTime = currentTime;
            return;
        }
        
        // 提取修饰键状态
        boolean ctrlPressed = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shiftPressed = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean altPressed = (modifiers & GLFW.GLFW_MOD_ALT) != 0;
        
        // 查找对应的快捷键动作（支持修饰键）
        ShortcutAction shortcut = ShortcutBindings.getAction(key, ctrlPressed, shiftPressed, altPressed);
        if (shortcut == null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[ShortcutDispatcher] 未找到快捷键绑定: key={}, ctrl={}, shift={}, alt={}", 
                    key, ctrlPressed, shiftPressed, altPressed);
            }
            // ⚠️ 注意：即使没有找到快捷键绑定，也要更新防抖状态，防止重复触发
            lastProcessedKey = key;
            lastProcessedTime = currentTime;
            return;
        }
        
        // 更新防抖状态（在分发之前更新，确保即使分发失败也能防止重复触发）
        lastProcessedKey = key;
        lastProcessedTime = currentTime;
        
        LOGGER.info("[ShortcutDispatcher] ✅ 处理快捷键: action={}, key={} (GLFW)", shortcut, key);
        
        // 分发到 Facade
        ShortcutActions.dispatch(shortcut, commands);
    }
    
    // 注意：ImGui 的键码系统不支持数字键和大部分字母键
    // 所有快捷键处理都通过 GLFW 事件（handleGLFWKeyEvent）进行
    // 这样可以确保所有按键都能正确处理
}