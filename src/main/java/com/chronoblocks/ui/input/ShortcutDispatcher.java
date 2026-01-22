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
    
    /**
     * 构造函数
     * 
     * @param commands Facade 命令接口
     */
    public ShortcutDispatcher(UICommandFacade commands) {
        this.commands = commands;
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
        
        // 上下文检查
        ImGuiIO io = ImGui.getIO();
        if (!ShortcutContext.shouldHandleShortcut(io)) {
            return;
        }
        
        // 查找对应的快捷键动作
        ShortcutAction shortcut = ShortcutBindings.getAction(key);
        if (shortcut == null) {
            return;
        }
        
        // 分发到 Facade
        ShortcutActions.dispatch(shortcut, commands);
    }
    
    // 注意：ImGui 的键码系统不支持数字键和大部分字母键
    // 所有快捷键处理都通过 GLFW 事件（handleGLFWKeyEvent）进行
    // 这样可以确保所有按键都能正确处理
}